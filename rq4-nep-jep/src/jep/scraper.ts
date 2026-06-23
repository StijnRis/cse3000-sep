import { HTMLElement } from 'node-html-parser';
import { XMLParser } from "fast-xml-parser";
import efs from 'fs-extra';
const { outputJSONSync } = efs;
import { fetchEmails, fetchHTMLParsed, fetchJSONParsed, fetchXMLParsed, mailIntervalAPI, mailListAPI } from "../common.ts";
import type { Email, SEP } from '../../types.js';
import { sortedBy } from '../util.ts';

//NOTE: all JEPs have issue IDs (long number), they get a JEP ID (short number) when they pass the draft phase
const jepBaseURL = 'openjdk.org/jeps/';
const jepURL = (id: number) => `${jepBaseURL}${id}`;
const jepIssueBaseURL = 'bugs.openjdk.org/';
// const jepIssueURL = (issueID: number) => `${jepIssueBaseURL}browse/JDK-${issueID}`;
const jepXMLURL = (issueID: number) => `${jepIssueBaseURL}si/jira.issueviews:issue-xml/JDK-${issueID}/JDK-${issueID}.xml`;
// const jepPrintableURL = (issueID: number) => `${jepIssueBaseURL}si/jira.issueviews:issue-html/JDK-${issueID}/JDK-${issueID}.html`;
const jepJIRAIssueURL = (issueID: number) => `${jepIssueBaseURL}rest/api/2/issue/JDK-${issueID}?expand=changelog`;

export const jepMailListURL = (listName: string, fileName: string) =>
  mailListAPI('mail.openjdk.org', `${listName}@openjdk.org`, fileName);
export const jepMailIntervalURL = (listName: string,
  fromYear: number, fromMonth: number, fromDay: number,
  toYear: number, toMonth: number, toDay: number,
  fileName: string
) => mailIntervalAPI('mail.openjdk.org', `${listName}@openjdk.org`, fromYear, fromMonth, fromDay, toYear, toMonth, toDay, fileName);

/**
 * Fetch IDs of all JEPs
 * @param cacheDir cache folder, empty for no caching
 * @returns all IDs findable from the main page
 */
export const collectIDsJEP = async (cacheDir: string): Promise<number[]> => {
  const doc = await fetchHTMLParsed(`${jepBaseURL}0`, cacheDir);
  return doc.querySelectorAll('.jep')
    .map(h => parseInt(h.text))
    .filter(id => id !== 2); // JEP 2 is the template
};

const jepTypes = ['Feature', 'Research', 'Infrastructure', 'Process', 'Informational'] as const;
type JEPType = typeof jepTypes[number];
const jepStates = ['Draft', 'Posted', 'Submitted', 'Candidate', 'Funded', 'Completed', 'Delivered', 'Withdrawn', 'Rejected', 'Active', 'Closed / Delivered', 'Closed / Withdrawn', 'Proposed to Target', 'Integrated', 'Targeted', 'Open', 'In Progress', 'Closed', 'Proposed to Drop', 'New', undefined] as const;
type JEPStatus = typeof jepStates[number];
export type OpenJDKUser = {
  username: string,
  name: string,
};
export type JEPComment = Email | {
  id: string,
  replyTo?: string,
  author: OpenJDKUser,
  created: Date,
  content: string,
};
type JEPRevision = {
  kind: 'status' | 'resolution' | 'summary' | 'version' |  'description' | undefined;
  from?: string,
  to?: string,
  created: Date,
  author: OpenJDKUser,
};
export type JEP = SEP <JEPType, JEPComment> & {
  issueID: number,
  owner: OpenJDKUser,
  authors: string[],
  created: Date,
  status: JEPStatus,
  release?: string,
  relatedIDs: number[],
  revisions: JEPRevision[],
  content: string,
};

type JEPXML = {
  created: string,
  summary: string,
  fixVersion?: string,
  description: string,
  customfields: {
    customfield: {
      customfieldname: string,
      customfieldvalues: {
        customfieldvalue: any[],
      },
    }[],
  },
};
type JIRAUser = {
  name: string,
  displayName: string,
};
type JEPJIRAIssueJSON = {
  fields: {
    description: string,
    creator: JIRAUser,
    comment: {
      comments: {
        id: string,
        author: JIRAUser,
        created: string,
        body: string,
      }[]
    },
  },
  changelog: {
    histories: {
      author: JIRAUser,
      created: string,
      items: {
        field: string,
        fromString: string | null,
        toString: string | null,
      }[],
    }[],
  },
};

const jepXMLParser = new XMLParser({
  ignoreAttributes: false,
  // alwaysCreateTextNode: true,
  isArray: (tagName) => ['comment', 'customfield', 'customfieldvalue'].includes(tagName),
});

/**
 * Extract comments from a JEP issue JSON
 * @param html full JSON (bugs.openjdk.org/rest/api/2/issue/JDK-xxxx)
 * @returns list of comments, potentially empty
 */
const parseJEPComments = (issueJSON: JEPJIRAIssueJSON): JEPComment[] => {
  return issueJSON.fields.comment.comments
    .map(({ id, author, created, body }) => ({
      id,
      author: {
        username: author.name,
        name: author.displayName,
      },
      created: new Date(created),
      content: body,
    }));
}

/**
 * Collect emails from a mailing list that belong to given JEP
 * @param id JEP ID
 * @param listName name of mailing list
 * @param cacheDir cache folder
 * @returns all emails in the list whose title mentions the JEP
 */
const collectJEPEmails = async (id: number, listName: string, cacheDir: string): Promise<Email[]> => {
  let allEmails;
  if(listName === 'core-libs-dev') { // this list is too big to get in one piece
    const years = Array(2027).keys().toArray().slice(2007);
    const yearLists = await Promise.all(years.map(y => fetchEmails(jepMailIntervalURL(
      listName, y, 1, 1, y, 12, 31, `${listName}${y}`
    ), cacheDir)));
    allEmails = yearLists.flat();
  } else {
    allEmails = await fetchEmails(jepMailListURL(listName, listName), cacheDir);
  }
  return allEmails.filter(({ subject }) => subject?.toLowerCase().match(/^jep[^a-z]|[^a-z]jep[^a-z]|[^a-z]jep$/)
    && subject.toLowerCase().match(`^${id}[^a-z0-9]|[^a-z0-9./]${id}[^a-z0-9]|[^a-z0-9./]${id}$`)
    && !subject.toLowerCase().match(`jdk ${id}`));
};

/**
 * Get value of a custom field, according to the exported XML schema
 * @param xml parsed XML (bugs.openjdk.org/si/jira.issueviews:issue-xml/JDK-xxx/JDK-xxx.xml), then indexed .rss.channel.item
 * @param fieldName name of custom field (customfieldname)
 * @returns array of values (customfieldvalue) if any
 */
const getJEPXMLField = (xml: JEPXML, fieldName: string): any[] | undefined => xml
  .customfields.customfield
  .find((f: any) => f.customfieldname === fieldName)
  ?.customfieldvalues.customfieldvalue;

/**
 * Extract JEP details from its openjdk.org page
 * @param id JEP ID number
 * @param supersederID ID of direct superseder JEP if any
 * @param html full HTML page (openjdk.org/jeps/xxx)
 * @param cacheDir cache folder, empty for no caching
 * @returns JEP object
 */
const parseJEP = async (id: number, supersederID: number | undefined, html: HTMLElement, cacheDir: string): Promise<JEP> => {
//   const fullTitle = html.getElementById('main')!.querySelector('h1')!.text;
  const metadata: {
    'Author'?: HTMLElement,
    'Authors'?: HTMLElement,
    'Owner': HTMLElement,
    'Created': HTMLElement,
    'Type': HTMLElement,
    'Status': HTMLElement,
    'Release'?: HTMLElement,
    'Relates to'?: HTMLElement,
    'Discussion'?: HTMLElement,
    'Issue': HTMLElement,
  } = Object.fromEntries(
    html.querySelector('.head')!
      .getElementsByTagName('tr')
      .map(row => row.getElementsByTagName('td').map((h, i) => i === 0 ? h.text : h))
  );
  const issueID = parseInt(metadata['Issue'].text);
  // const issuePage = await fetchHTMLParsed(jepIssueURL(issueID), cacheDir);
  const issueXML = await fetchXMLParsed(jepXMLURL(issueID), jepXMLParser, cacheDir);
  const issueJSON = await fetchJSONParsed<JEPJIRAIssueJSON>(jepJIRAIssueURL(issueID), cacheDir);
  const jepXML: JEPXML = issueXML.rss.channel.item;
  const type = getJEPXMLField(jepXML, 'JEP Type')![0]['#text'];
  // const printablePage = await fetchHTMLParsed(jepPrintableURL(issueID), cacheDir);
  // const ownerCell = printablePage.querySelectorAll('table.grid')[1].querySelector('span.user-hover');

  const comments = parseJEPComments(issueJSON);

  const discussion = metadata['Discussion'];
  if(discussion) {
    const mailListName = discussion.text
      .replace(/ at .*$/, '')
      .replaceAll(/ dash /g, '-')
      .replace('hostspot', 'hotspot').trim();
    const parsedMails = await collectJEPEmails(id, mailListName, cacheDir);
    comments.push(...parsedMails);
  }

  return {
    id,
    issueID,
    title: jepXML.summary,
    owner: {
      username: issueJSON.fields.creator.name,
      name: issueJSON.fields.creator.displayName,
    },
    authors: metadata['Authors']?.text.split(/, & |,& |, | & /) || (metadata['Author'] ? [metadata['Author'].text] : []),
    created: new Date(jepXML.created),
    type,
    status: metadata['Status'].text as JEPStatus,
    release: metadata['Release']?.text,
    supersederID,
    relatedIDs: metadata['Relates to']?.querySelectorAll('a')
      .map(a => parseInt(a.getAttribute('href')!))
      .filter(id => !isNaN(id))
      || [],
    content: issueJSON.fields.description,
    revisions: sortedBy(issueJSON.changelog.histories.flatMap(h => 
      h.items.map(i => ({
        kind: ({
          'status': 'status',
          'resolution': 'resolution',
          'summary': 'summary',
          'fix version': 'version',
          'description': 'description',
        } as const)[i.field.toLowerCase()],
        from: i.fromString ?? undefined,
        to: i.toString ?? undefined,
        created: new Date(h.created),
        author: {
          username: h.author.name,
          name: h.author.displayName,
        },
      }))
    ), rev => rev.created.getTime()),
    comments,
  }
};

/**
 * Finds JEPs which directly supersede previous JEPs (later previews or incubators)
 * Assumes that JEPs in a superseding relation have the same title up to the "(nth Preview)" suffix
 * @param cacheDir cache folder, empty for no caching
 * @returns map from IDs to their superseders
 */
export const getSupersederJEPs = async (cacheDir: string) => {
  const frontPage = await fetchHTMLParsed(`${jepBaseURL}0`, cacheDir);
  const idTitles = frontPage.querySelectorAll('.jeps')
    .flatMap(j => j.querySelectorAll('tr'))
    .map(h => [parseInt(h.querySelector('.jep')!.text), h.querySelector('a')!.text] as [number, string])
    .toSorted(([id1, _], [id2, __]) => id1 - id2); // sorted by ID ascending
  const prevs: {[_: string]: number | undefined} = {}; //the previous occurrence of a given title
  const sups: {[_: number]: number | undefined} = {};
  for(const [id, title] of idTitles) {
    // common title of all JEPs in the series
    const mainTitle = title.replace(/ \(.*Preview\)$| \(.*Incubator\)$/, '');
    const prev = prevs[mainTitle];
    if(prev) sups[prev] = id;
    prevs[mainTitle] = id;
  }
  return sups;
};

/**
 * Scrapes JEP with given ID
 * @param id ID
 * @param supersederID ID of direct superseder if any
 * @param cacheDir cache folder, empty for no caching
 * @param saveDir directory to save result; leave undefined for no saving
 */
export const scrapeJEP = async (id: number, supersederID: number | undefined, cacheDir: string, saveDir?: string): Promise<JEP> => {
  console.log(`Fetching JEP ${id}`);
  const page = await fetchHTMLParsed(jepURL(id), cacheDir);
  console.log(`Fetching comments of JEP ${id}`);
  const jep = await parseJEP(id, supersederID, page, cacheDir);
  if(saveDir !== undefined) {
    outputJSONSync(`${saveDir}/${jep.id}.json`, jep, { encoding: 'utf8', spaces: 2 });
  }
  return jep;
};