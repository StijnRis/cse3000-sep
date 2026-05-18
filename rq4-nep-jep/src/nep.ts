import { collectIDsNEP, scrapeNEP } from "./nep-scraper.ts";
import { dataSource, dumpDatabase } from "./persist.ts";
import { NEPPersister } from "./nep-persist.ts";
import { getArg } from "./args-helper.ts";

const dbName = getArg('db-name');
const cacheDirPart = getArg('cache-dir');
const cacheDir = cacheDirPart ? `cache/${cacheDirPart}` : '';
const saveDirPart = getArg('save-dir');
const saveDir = saveDirPart ? `data/nep/${saveDirPart}` : undefined;
const nepIDs = await collectIDsNEP(cacheDir);
const neps = await Promise.all(nepIDs.map(id =>
  scrapeNEP(id, cacheDir, saveDir).then((nep) => {
    console.log(`Succesfully scraped NEP ${nep.id}`);
    return nep;
  })
));
const db = new NEPPersister().persist(neps);
const source = await dataSource(dbName !== undefined ? `data/${dbName}.db` : ':memory:');
await dumpDatabase(source, db);