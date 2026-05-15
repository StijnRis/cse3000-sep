import { HTMLElement } from 'node-html-parser';
import efs from 'fs-extra';
const { outputJSONSync } = efs;
import { fetchEmails, fetchHTMLParsed, fetchJSONParsed, fetchText, ghAuthHeaders, githubCommitAPI, githubCommitsForPathAPI, githubIssueAPI, githubPullAPI, parseGithubCommentJSON, parseMboxEmail, pythonMailIntervalURL, pythonMailListURL } from './common.ts';
import type { SEP, EmailUser, Email, GithubComment, GithubCommentJSON, GithubCommit, GithubCommitDetailed } from '../types.d.ts';

const nepBaseURL = 'numpy.org/neps/';

/**
 * Fetch IDs of all NEPs (format: nep-<number ID>-<short title>)
 * @returns all IDs findable from the main page
 */
export const collectIDsNEP = async (cacheDir: string): Promise<string[]> => {
  const doc = await fetchHTMLParsed(nepBaseURL, cacheDir);
  return doc.getElementById('numpy-enhancement-proposals-neps')!
    .querySelectorAll('.toctree-l2')
    .flatMap(h => h.getElementsByTagName('a'))
    .map(h => h.getAttribute('href')!)
    .filter(page => page !== 'nep-template.html') // meta-NEP, not needed
    .map(page => page.replace(/\.html$/, ''));
};

const githubPathToFile = (strID: string) => `doc/neps/${strID}.rst`;

// email optional for authors
type NEPAuthor = Omit<EmailUser, 'email'> & Partial<Pick<EmailUser, 'email'>>;
const nepTypes = ['Standards Track', 'Process', 'Informational', undefined] as const;
type NEPType = typeof nepTypes[number];
type NEPActualType = NEPType | 'Standard' | 'Standard Track' | 'Standards'; // python users are allergic to conventions
const translateType = (actual: NEPActualType): NEPType => actual ? ({
  'Standard': 'Standards Track',
  'Standard Track': 'Standards Track',
  'Standards': 'Standards Track',
  'Standards Track': 'Standards Track',
  'Process': 'Process',
  'Informational': 'Informational',
} as const)[actual] : actual;
const nepStates = ['Draft', 'Active', 'Accepted', 'Deferred', 'Rejected', 'Withdrawn', 'Final', 'Superseded', 'Provisional', undefined] as const;
type NEPStatus = typeof nepStates[number];
type NEPComment = Email | GithubComment;
export type NEPRevision = {
  authors: NEPAuthor[],
  status: NEPStatus,
  created: Date,
  content: string,
};
export type NEP = SEP<NEPType, NEPComment> & {
  revisions: NEPRevision[],
};
export const isGithubComment = (comm: NEPComment): comm is GithubComment => Object.hasOwn(comm.author, 'login');

const collectNEPRevisions = async (strID: string, cacheDir: string): Promise<NEPRevision[]> => {
  const githubFile = githubPathToFile(strID);
  const changingCommits = await fetchJSONParsed<GithubCommit[]>(
    githubCommitsForPathAPI('numpy', 'numpy', githubFile),
    cacheDir, ghAuthHeaders
  );

  // breaking parallelism because of rate limit
  const detailedCommits: GithubCommitDetailed[] = [];
  for(const commit of changingCommits) {
    detailedCommits.push(await fetchJSONParsed<GithubCommitDetailed>(
      githubCommitAPI('numpy', 'numpy', commit.sha),
      cacheDir, ghAuthHeaders
    ));
  }

  // rate limit again
  const changedFiles: {created: Date, fileText: string}[] = [];
  for(const commit of detailedCommits) {
    for(const file of commit.files) {
      if(file.filename === githubFile) {
        changedFiles.push(({
          created: new Date(commit.commit.author.date),
          fileText: await fetchText(file.raw_url.replace(/^https:\/\//, ''), 'rst', cacheDir),
        }));
      }
    }
  }
  return changedFiles.map(({created, fileText}) => {
    // console.log(fileText);
    const lines = fileText.split('\n');
    return {
      authors: lines.filter(l => l.startsWith(':Author:'))
        .map(l => l.replace(':Author:', '').trim())
        .flatMap(a => a.split(', '))
        .map(a => ({name: a.replace(/ <.*>$/, ''), email: a.match(/ <(.*)>$/)?.[1]})),
      status: lines.find(l => l.startsWith(':Status:'))?.replace(':Status:', '').trim() as NEPStatus,
      created,
      content: lines.filter(l => l.trim() !== '' && !l.match(/^:[A-Za-z]*:/))
        .join('\n'),
    };
  });
};

/**
 * Collects comments that can be found under a given URL
 * Tries one of multiple scraping strategies (email, github) depending on URL
 * @param url URL to look under
 * @param cacheDir cache folder for fetch results, empty for no caching
 * @returns list of comments under the URL, in no particular order
 */
const collectNEPComments = async (url: string, cacheDir: string): Promise<NEPComment[]> => {
  /**
   * Fetch emails from given thread using HyperKitty archive download URL
   * @todo support pipermail (older version, no explicit download URL)
   * @param threadID HyperKitty thread ID
   * @returns list of email objects in thread
   */
  const collectArchiveEmails = async (threadID: string): Promise<Email[]> => {
    const mailAPI = pythonMailListURL('numpy-discussion', threadID, threadID);
    const mails = await fetchEmails(mailAPI, cacheDir);
    return mails.map(parseMboxEmail);
  };

  /**
   * Fetch emails from given thread in the Pipermail archive
   * Since Pipermail doesn't give an export API, nor an easily parseable HTML, I look for the same thread in HyperKitty
   * @param url Pipermail URL (without https://) for getting the thread title
   * @param month number of month 1-12
   * @param year year
   * @returns list of mails
   */
  const collectPipermailEmails = async (url: string, month: number, year: number): Promise<Email[]> => {
    // first get the old pipermail page to get the title
    const pipermailPage = await fetchHTMLParsed(url, cacheDir);
    const title = pipermailPage.querySelector('h1')!.text.toLowerCase().replace('[numpy-discussion] ', '');
    const endMonth = month === 12 ? 1 : month + 1;
    const endYear = month === 12 ? year + 1 : year;
    const intervalAPI = pythonMailIntervalURL('numpy-discussion',
      year, month, 1,
      endYear, endMonth, 1,
      `${year}-${month}-${endYear}-${endMonth}`
    );
    const mails = await fetchEmails(intervalAPI, cacheDir);
    return mails
      .filter(m => m.subject?.toLowerCase().includes(title))
      .map(parseMboxEmail);
  }

  /**
   * Fetch comments from GitHub issue
   * @param issueID ID of issue
   * @returns list of comments under issue
   */
  const collectGithubIssueComments = async (issueID: number): Promise<GithubComment[]> => {
    const issueAPI = githubIssueAPI('numpy', 'numpy', issueID);
    const issueComments = await fetchJSONParsed<GithubCommentJSON[]>(`${issueAPI}/comments`, cacheDir, ghAuthHeaders);
    return await Promise.all(issueComments.map(comm => parseGithubCommentJSON(comm, cacheDir)));
  };

  /**
   * Fetch comments from a GitHub PR resolution
   * Also checks associated issue
   * @param pullID ID of pull request
   * @returns list of comments under PR (both issue and review)
   */
  const collectGithubPRComments = async (pullID: number): Promise<GithubComment[]> => {
    const pullAPI = githubPullAPI('numpy', 'numpy', pullID);
    const prBody = await fetchJSONParsed<GithubCommentJSON>(pullAPI, cacheDir, ghAuthHeaders);
    const prComments = await fetchJSONParsed<GithubCommentJSON[]>(`${pullAPI}/comments`, cacheDir, ghAuthHeaders);
    const prDiscussion = await Promise.all([prBody].concat(prComments)
      .map(comm => parseGithubCommentJSON(comm, cacheDir)));
    const issueComments = await collectGithubIssueComments(pullID);
    return prDiscussion.concat(issueComments);
  };

  let match = url.match(/^https:\/\/mail\.python\.org\/archives\/list\/numpy-discussion@python\.org\/thread\/([A-Z0-9]*)/);
  if(match)
    return await collectArchiveEmails(match[1]);
  match = url.match(/^https:\/\/(mail\.python\.org\/pipermail\/numpy-discussion\/([0-9]*)-([A-Z][a-z]*).*)$/);
  if(match)
    // first argument is url without https, second is year (numeric), third is month (text)
    return await collectPipermailEmails(
      match[1],
      new Date(`1 ${match[3]} 2000`).getMonth() + 1,
      parseInt(match[2]),
    );
  match = url.match(/^https:\/\/github\.com\/numpy\/numpy\/issues\/([0-9]*)/);
  if(match)
    return await collectGithubIssueComments(parseInt(match[1]));
  match = url.match(/^https:\/\/github\.com\/numpy\/numpy\/pull\/([0-9]*)/);
  if(match)
    return await collectGithubPRComments(parseInt(match[1]));
  return [];
};

/**
 * Extract NEP details from its numpy.org page
 * @param strID text ID of NEP (format: nep-<number ID>-<short title>)
 * @param html full HTML page (numpy.org/neps/<strID>.html)
 * @param cacheDir cache folder for fetch results, empty for no caching
 * @returns NEP object
 */
const parseNEP = async (strID: string, html: HTMLElement, cacheDir: string): Promise<NEP> => {
  const article = html.querySelector('.bd-article')!.querySelector('section')!;

  const fullTitle = article.querySelector('h1')!.childNodes[0].text;
  const id = parseInt(fullTitle.split(' — ')[0].replaceAll(/\D/g, ''));
  const title = fullTitle.split(' — ')[1];

  const metadata = article.querySelector('dl')!;
  const mdKeys = metadata.querySelectorAll('dt').map(h => h.text);
  const mdValues = metadata.querySelectorAll('dd');
  const mdEntries: [string, HTMLElement][] = mdKeys.map((k, i) => [k, mdValues[i]]);
  const mdField = (...possibleKeys: string[]) =>
    mdEntries.find(([k]) => possibleKeys.includes(k))?.[1];
  const mdTextField = (...possibleKeys: string[]) => {
    const fieldText = mdField(...possibleKeys)?.text;
    return fieldText === undefined
      ? undefined
      // to remove \n at the end
      : fieldText.substring(0, fieldText.length - 1);
  }

  const supersederIDStr = mdField('Replaced-By:')
    ?.querySelector('a')!
    .getAttribute('href')!
    .match(/nep-([0-9]*)-.*\.html/)![1];

  // const contentFragments = id === 5
  //   ? article.children.filter(c => ['p', 'ol', 'section'].includes(c.tagName.toLowerCase()))
  //   : id === 9
  //     ? article.children.filter(c => ['ol', 'blockquote'].includes(c.tagName.toLowerCase()))
  //     : article.querySelectorAll('section');
  // const content = contentFragments.map(h => h.text).join('');

  const resolutionURLs = mdEntries.filter(([k]) => k === 'Resolution:')
    .map(([_, v]) => v)
    .flatMap(h => h.querySelectorAll('a'))
    .map(h => h.getAttribute('href')!);
  const comments = (await Promise.all(resolutionURLs.map(url => collectNEPComments(url, cacheDir)))).flat();

  return {
    id,
    title,
    type: translateType(mdTextField('Type:') as NEPActualType),
    // some old NEPs don't have dates on the website, I manually took the earliest dates I could find from their github history
    // created: new Date(dateStr ? `${dateStr} UTC` : nepDates[`${id}` as keyof typeof nepDates]),
    supersederID: supersederIDStr ? parseInt(supersederIDStr) : undefined,
    revisions: await collectNEPRevisions(strID, cacheDir),
    comments,
  };
};

/**
 * Scrapes NEP from URL
 * @param strID text ID of NEP (format: nep-<number ID>-<short title>)
 * @param cacheDir cache folder for fetch results, empty for no caching
 * @param saveDir directory to save result; leave undefined for no saving
 */
export const scrapeNEP = async (strID: string, cacheDir: string, saveDir?: string): Promise<NEP> => {
  const nep = await parseNEP(strID, await fetchHTMLParsed(`${nepBaseURL}${strID}.html`, cacheDir), cacheDir);
  if(saveDir !== undefined) {
    outputJSONSync(`${saveDir}/${nep.id}.json`, nep, { encoding: 'utf8', spaces: 2 });
  }
  return nep;
};