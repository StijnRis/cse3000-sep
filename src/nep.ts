import { collectURLsNEP, scrapeNEP } from "./nep-scraper.ts";
import { dataSource, dumpDatabase, NEPPersister } from "./persist.ts";

const nepURLs = await collectURLsNEP();
const dirSuf = 'test';
const neps = await Promise.all(nepURLs.map(url =>
  scrapeNEP(url, `neps${dirSuf}`).then((nep) => {
    // console.log(`Succesfully scraped NEP ${nep.id}`);
    return nep;
  })
));
const db = new NEPPersister().persist(neps);
const source = await dataSource('data/nep.db');
await dumpDatabase(source, db);