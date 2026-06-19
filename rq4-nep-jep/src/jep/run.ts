import { scrapeJEP, collectIDsJEP, getSupersederJEPs } from "./scraper.ts";
import type { JEP } from "./scraper.ts";
import { dataSource, dumpDatabase } from "../persist.ts";
import { JEPPersister } from "./persist.ts";
import { getArg, isArg } from "../args-helper.ts";

const dbName = getArg('db-name');
const cacheDirPart = getArg('cache-dir');
const cacheDir = cacheDirPart ? `cache/${cacheDirPart}` : '';
const saveDirPart = getArg('save-dir');
const saveDir = saveDirPart ? `data/jep/${saveDirPart}` : undefined;
const newDB = isArg('new-db');

const jepIDs = await collectIDsJEP(cacheDir);
const superseders = await getSupersederJEPs(cacheDir);
const jeps: JEP[] = [];
for(const id of jepIDs) {
  // may need to scrape one at a time because openjdk.org has rate limits
  const jep = await scrapeJEP(id, superseders[id], cacheDir, saveDir);
  jeps.push(jep);
  console.log(`Successfully scraped JEP ${jep.id}`);
}
const db = new JEPPersister().persist(jeps);
const source = await dataSource(dbName ? `data/${dbName}.db` : ':memory:', newDB);
await dumpDatabase(source, db);