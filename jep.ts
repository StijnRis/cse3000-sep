import { scrapeJEP, collectIDsJEP, getSupersederJEPs, JEP, OpenJDKUser } from "./jep-scraper.ts";
import { JEPPersister } from "./persist.ts";

const jepIDs = await collectIDsJEP();
const superseders = await getSupersederJEPs();
const dirSuf = 'test';
const jeps: JEP[] = [];
for(const id of jepIDs) {
  // may need to scrape one at a time because openjdk.org crashes or forces a cooldown from too many parallel requests
  const jep = await scrapeJEP(id, superseders[id], `jeps${dirSuf}`);
  jeps.push(jep);
  console.log(`Successfully scraped JEP ${jep.id}`);
}
const jepDB = new JEPPersister().persist(jeps);
