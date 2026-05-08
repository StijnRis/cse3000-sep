import { parse, HTMLElement } from 'node-html-parser';
import { XMLParser } from "fast-xml-parser";
import efs from 'fs-extra';
const { outputJSONSync } = efs;
import { fetchHTMLParsed, fetchXMLParsed } from "./common.ts";
import type { SEP } from '../types.d.ts';

//NOTE: all JEPs have issue IDs (long number), they get a JEP ID (short number) when they pass the draft phase
const jepBaseURL = 'openjdk.org/jeps/';
const jepURL = (id: number) => `${jepBaseURL}${id}`;
const jepIssueBaseURL = 'bugs.openjdk.org/';
// const jepIssueURL = (issueID: number) => `${jepIssueBaseURL}browse/JDK-${issueID}`;
const jepXMLURL = (issueID: number) => `${jepIssueBaseURL}si/jira.issueviews:issue-xml/JDK-${issueID}/JDK-${issueID}.xml`;
const jepPrintableURL = (issueID: number) => `${jepIssueBaseURL}si/jira.issueviews:issue-html/JDK-${issueID}/JDK-${issueID}.html`;

/**
 * Fetch IDs of all JEPs
 * @param cacheDir cache folder, empty for no caching
 * @returns all IDs findable from the main page
 */
export const collectIDsJEP = async (cacheDir: string): Promise<number[]> => {
  const doc = await fetchHTMLParsed(`${jepBaseURL}0`, cacheDir);
  return doc.querySelectorAll('.jep')
    .map(h => parseInt(h.text));
};

const jepTypes = ['Feature', 'Research', 'Infrastructure', 'Process', 'Informational'] as const;
type JEPType = typeof jepTypes[number];
const jepStates = ['Draft', 'Posted', 'Submitted', 'Candidate', 'Funded', 'Completed', 'Withdrawn', 'Rejected', 'Active', 'Closed / Delivered', 'Closed / Withdrawn', 'Proposed to Target', 'Integrated', 'Targeted', undefined] as const;
type JEPStatus = typeof jepStates[number];
export type OpenJDKUser = {
  username: string,
  name: string,
};
type JEPComment = {
  id: number,
  author: OpenJDKUser,
  created: Date,
  content: string,
};
export type JEP = SEP<string, JEPType, JEPStatus, JEPComment> & {
  issueID: number,
  owner?: OpenJDKUser,
  release?: number,
  relatedIDs: number[],
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

const jepXMLParser = new XMLParser({
  ignoreAttributes: false,
  // alwaysCreateTextNode: true,
  isArray: (tagName) => ['comment', 'customfield', 'customfieldvalue'].includes(tagName),
});

/**
 * Extract comments from a JEP issue printable page
 * @param html full HTML page (bugs.openjdk.org/browse/JDK-xxxx, then export as printable)
 * @returns list of comments, potentially empty
 */
const parseJEPComments = (html: HTMLElement): JEPComment[] => {
  const grids = html.querySelectorAll('table.grid');
  const commRows = grids[grids.length - 1].querySelectorAll('tr'); // headers and bodies are intercalated even-odd
  const commHeaders = commRows.filter(r => r.id.startsWith('comment-header-'));
  const commBodies = commRows.filter(r => r.id.startsWith('comment-body-'));
  const commEntries: [HTMLElement, HTMLElement][] = commHeaders.map((h, i) => [h, commBodies[i]]);
  return commEntries
    .map(([head, body]) => ({
      id: parseInt(head.id.replace('comment-header-', '')),
      author: {
        username: head.querySelector('a')!.getAttribute('rel')!,
        name: head.querySelector('a')!.innerText,
      },
      created: new Date(head.querySelector('font')!.querySelector('font')!.text),
      content: body.querySelector('td')!.text,
    }));
}

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
    'Issue': HTMLElement,
  } = Object.fromEntries(
    html.querySelector('.head')!
      .getElementsByTagName('tr')
      .map(row => row.getElementsByTagName('td').map((h, i) => i === 0 ? h.text : h))
  );
  const issueID = parseInt(metadata['Issue'].text);
  const issueXML = await fetchXMLParsed(jepXMLURL(issueID), jepXMLParser, cacheDir);
  const jepXML: JEPXML = issueXML.rss.channel.item;
  const type = getJEPXMLField(jepXML, 'JEP Type')![0]['#text'];
  const printablePage = await fetchHTMLParsed(jepPrintableURL(issueID), cacheDir);
  const ownerCell = printablePage.querySelectorAll('table.grid')[1].querySelector('span.user-hover');
  const comments = parseJEPComments(printablePage);
  return {
    id,
    issueID,
    title: jepXML.summary,
    owner: ownerCell ? {
      username: ownerCell.getAttribute('rel')!,
      name: ownerCell.text,
    } : undefined,
    authors: metadata['Authors']?.text.split(/, & |,& |, | & /) || (metadata['Author'] ? [metadata['Author'].text] : []),
    created: new Date(jepXML.created),
    type,
    status: metadata['Status'].text as JEPStatus,
    release: metadata['Release'] ? parseInt(metadata['Release'].text) : undefined,
    supersederID,
    relatedIDs: metadata['Relates to']?.querySelectorAll('a').map(a => parseInt(a.getAttribute('href')!)) || [],
    content: parse(jepXML.description).text,
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
    outputJSONSync(`data/${saveDir}/${jep.id}.json`, jep, { encoding: 'utf8', spaces: 2 });
  }
  return jep;
};