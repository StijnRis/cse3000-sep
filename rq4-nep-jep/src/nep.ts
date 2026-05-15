import { collectIDsNEP, scrapeNEP } from "./nep-scraper.ts";
import { dataSource, dumpDatabase, NEPPersister } from "./persist.ts";

const dbName = process.argv[2] as string | undefined;
const cacheDirPart = process.argv[3] || '';
const cacheDir = cacheDirPart ? `cache/${cacheDirPart}` : '';
const saveDirPart = process.argv[4] || '';
const saveDir = saveDirPart ? `data/nep/${saveDirPart}` : undefined;
const nepIDs = await collectIDsNEP(cacheDir);
const neps = await Promise.all(nepIDs.map(id =>
  scrapeNEP(id, cacheDir, saveDir).then((nep) => {
    console.log(`Succesfully scraped NEP ${nep.id}`);
    return nep;
  })
));
const db = new NEPPersister().persist(neps);
const source = await dataSource(dbName ? `data/${dbName}.db` : ':memory:');
await dumpDatabase(source, db);