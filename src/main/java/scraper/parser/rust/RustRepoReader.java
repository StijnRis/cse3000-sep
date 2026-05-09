package scraper.parser.rust;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import scraper.data.proposal.Proposal;

import java.io.*;
import java.util.List;

public class RustRepoReader {

    private String cacheDir;
    private boolean ignoreCached;
    private List<Proposal> proposals;

    public RustRepoReader(String cacheDir, boolean ignoreCached, List<Proposal> proposals) {
        this.cacheDir = cacheDir;
        this.ignoreCached = ignoreCached;
        this.proposals = proposals;
    }

    public void scrapeGitHubData() throws IOException {
        for (Proposal proposal : proposals) {
            Document pr = getPRPage(proposal);
        }
    }

    public Document getPRPage(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + pr.getId().getProposalId() + ".html");
        if(!ignoreCached && possibleCache.isFile()) {
            return Jsoup.parse(possibleCache);
        }

        Document doc = Jsoup.connect(pr.getRfcPrs().getFirst()).get();

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(possibleCache)
        );

        writer.write(doc.toString());

        return doc;
    }

}
