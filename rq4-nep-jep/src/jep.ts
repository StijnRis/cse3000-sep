import { scrapeJEP, collectIDsJEP, getSupersederJEPs } from "./jep-scraper.ts";
import type { JEP } from "./jep-scraper.ts";
import { JEPPersister, dataSource, dumpDatabase } from "./persist.ts";

const dbName = process.argv[2] as string | undefined;
const cacheDirPart = process.argv[3] || '';
const cacheDir = cacheDirPart ? `cache/${cacheDirPart}` : '';
const saveDirPart = process.argv[4] || '';
const saveDir = saveDirPart ? `data/jep/${saveDirPart}` : undefined;
const jepIDs = await collectIDsJEP(cacheDir);
const superseders = await getSupersederJEPs(cacheDir);
const jeps: JEP[] = [];
for(const id of jepIDs) {
  // may need to scrape one at a time because openjdk.org crashes or forces a cooldown from too many parallel requests
  const jep = await scrapeJEP(id, superseders[id], cacheDir, saveDir);
  jeps.push(jep);
  console.log(`Successfully scraped JEP ${jep.id}`);
}
const db = new JEPPersister().persist(jeps);
const source = await dataSource(dbName ? `data/${dbName}.db` : ':memory:');
await dumpDatabase(source, db);