import { parse, HTMLElement } from 'node-html-parser';
import { XMLParser } from 'fast-xml-parser';
import { readFileSync, existsSync } from 'node:fs';
import { Readable } from 'node:stream';
import efs from 'fs-extra';
const { outputFileSync } = efs;
import type { SEP, GithubUser, GithubComment, GithubCommentJSON, Email } from '../types.d.ts';
import { mboxParser, ParsedMail } from 'mbox-parser';

/**
 * Fetch page of given URL
 * Caches content and gets it from cache if present
 * @param url URL to fetch (without https://)
 * @param contentType HTML, XML, JSON, RST
 * @param cacheDir directory for cache file, which is named using URL; leave out or empty for no caching
 * @param headers optional HTTP headers
 * @returns text of page, to be parsed as needed
 */
export const fetchText = async (url: string, contentType: 'html' | 'xml' | 'json' | 'rst', cacheDir: string = '', headers?: HeadersInit): Promise<string> => {
  const cacheFile = `${cacheDir}/${url}.${contentType}`;
  let resText: string;
  if(cacheDir && existsSync(cacheFile)) {
    resText = readFileSync(cacheFile, 'utf8');
  } else {
    resText = await fetch(`https://${url}`, {headers}).then(res => res.text());
    if(cacheDir) outputFileSync(cacheFile, resText, 'utf8');
  }
  return resText;
}

/**
 * Fetch HTML page
 * Caches by default
 * @param url URL to fetch (without https://)
 * @param cacheDir cache folder, empty string for no caching
 * @param headers optional HTTP headers
 * @returns parsed HTMLElement
 */
export const fetchHTMLParsed = async (url: string, cacheDir: string, headers?: HeadersInit): Promise<HTMLElement> => {
  return parse(await fetchText(url, 'html', cacheDir, headers));
};

/**
 * Fetch XML page
 * Caches by default
 * @param url URL to fetch (without https://)
 * @param parser XML parser
 * @param cacheDir cache folder, empty string for no caching
 * @param headers optional HTTP headers
 * @returns parsed XML object
 */
export const fetchXMLParsed = async (url: string, parser: XMLParser, cacheDir: string, headers?: HeadersInit): Promise<any> => {
  return parser.parse(await fetchText(url, 'xml', cacheDir, headers));
};

/**
 * Fetch JSON response
 * Caches by default
 * @param url URL to fetch (without https://)
 * @param cacheDir cache folder, empty string for no caching
 * @param headers optional HTTP headers
 * @returns parsed JSON object
 */
export const fetchJSONParsed = async <T>(url: string, cacheDir: string, headers?: HeadersInit): Promise<T> => {
  return JSON.parse(await fetchText(url, 'json', cacheDir, headers));
};

/**
 * Download file
 * @param url URL to fetch (without https://)
 * @param contentType response content type, as file extension (zip, gz)
 * @param cacheDir cache folder, empty string for no caching
 * @param headers optional HTTP headers
 * @returns file as blob
 */
export const fetchFile = async (url: string, contentType: string, cacheDir: string, headers?: HeadersInit): Promise<Blob> => {
  const cacheFile = `${cacheDir}/${url}.${contentType}`;
  let resBlob: Blob;
  if(cacheDir && existsSync(cacheFile)) {
    const buf = readFileSync(cacheFile, null);
    resBlob = new Blob([buf]);
  } else {
    resBlob = await fetch(`https://${url}`, {headers}).then(res => res.blob());
    const buf = await resBlob.arrayBuffer();
    if(cacheDir) {
      outputFileSync(cacheFile, Buffer.from(buf), null);
    }
  }
  return resBlob;
};

const githubAPIBase = 'api.github.com/';
const githubUserAPI = (login: string) => `${githubAPIBase}users/${login}`;
const githubRepoAPI = (owner: string, repo: string) => `${githubAPIBase}repos/${owner}/${repo}`;
export const githubPullAPI = (owner: string, repo: string, pullID: number) => `${githubRepoAPI(owner, repo)}/pulls/${pullID}`;
export const githubIssueAPI = (owner: string, repo: string, issueID: number) => `${githubRepoAPI(owner, repo)}/issues/${issueID}`;
export const githubCommitsForPathAPI = (owner: string, repo: string, path: string) => `${githubRepoAPI(owner, repo)}/commits?path=${path}`;
export const githubCommitAPI = (owner: string, repo: string, ref: string) => `${githubRepoAPI(owner, repo)}/commits/${ref}`;

export const pythonMailListURL = (list: string, threadID: string, fileName: string) =>
  `mail.python.org/archives/list/${list}@python.org/export/${fileName}.mbox.gz?thread=${threadID}`;
export const pythonMailIntervalURL = (list: string,
  fromYear: number, fromMonth: number, fromDay: number,
  toYear: number, toMonth: number, toDay: number,
  fileName: string
) => `mail.python.org/archives/list/${list}@python.org/export/${fileName}.mbox.gz?start=${fromYear}-${fromMonth}-${fromDay}&end=${toYear}-${toMonth}-${toDay}`;

const ghHeaders: HeadersInit = {
  'Accept': 'application/vnd.github+json',
  'X-Github-Api-Version': process.env.GITHUB_API_VERSION!,
};
export const ghAuthHeaders: HeadersInit = {
  ...ghHeaders,
  'Authorization': `Bearer ${process.env.GITHUB_TOKEN}`,
};

/**
 * Fetch user details from GitHub API
 * @param login username
 * @param cacheDir cache folder, empty string for no caching
 * @returns user details
 */
export const getGithubUser = async (login: string, cacheDir: string): Promise<GithubUser> => {
  const { name } = await fetchJSONParsed<GithubUser>(githubUserAPI(login), cacheDir, ghAuthHeaders);
  return { login, name };
};

export const parseGithubCommentJSON = async ({
  id,
  user: {login},
  created_at,
  body,
  in_reply_to_id
}: GithubCommentJSON, cacheDir: string): Promise<GithubComment> => ({
  id,
  replyTo: in_reply_to_id,
  author: await getGithubUser(login, cacheDir),
  created: new Date(created_at),
  body,
});

export const parseMboxEmail = ({
  messageId,
  inReplyTo,
  from,
  date,
  text,
}: ParsedMail): Email => ({
  id: messageId!,
  replyTo: inReplyTo,
  author: {
    email: from!.value[0].address!,
    name: from!.value[0].name,
  },
  created: date!,
  body: text!,
});

/**
 * Get an archive of emails in mbox format and parse them
 * @param url URL returning a .mbox.gz file
 * @param cacheDir cache folder
 * @returns list of parsed email objects
 */
export const fetchEmails = async (url: string, cacheDir: string): Promise<ParsedMail[]> => {
  const gzBlob = await fetchFile(url, 'gz', cacheDir);
  const unpacked = gzBlob.stream().pipeThrough(new DecompressionStream('gzip'));
  return await mboxParser(Readable.from(unpacked));
}

/**
 * Groups SEPs by the ID of their latest revision
 * @param seps all SEPs; superseders must also be present
 * @returns map with lists of SEPs grouped by the ID of the final superseder, each sorted by ID descending
 */
export const groupByFinalID = <T,C, SEP_T extends SEP<T,C>>(seps: SEP_T[]): Map<SEP_T['id'], SEP_T[]> => {
  const byID = new Map<SEP_T['id'], SEP_T>();
  for(const sep of seps) {
    byID.set(sep.id, sep);
  }
  const res = new Map<SEP_T['id'], SEP_T[]>();
  for(const sep of seps) {
    let parent = sep;
    while(parent.supersederID !== undefined) parent = byID.get(parent.supersederID)!;
    if(!res.has(parent.id)) res.set(parent.id, []);
    res.get(parent.id)!.push(sep);
  }
  for(const group of res.values()) {
    group.sort(({id: id1}, {id: id2}) => id2 - id1);
  }
  return res;
};