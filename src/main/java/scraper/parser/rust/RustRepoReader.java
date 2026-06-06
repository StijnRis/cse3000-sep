package scraper.parser.rust;

import org.hibernate.Session;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.proposal.Comment;
import scraper.data.proposal.NormalizedStatus;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.ProposalStatus;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionAuthorId;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitHubService;
import scraper.parser.RepoReader;
import util.Log;

import java.io.*;
import java.sql.Date;
import java.util.*;

public class RustRepoReader extends RepoReader {

    public RustRepoReader(Session session, String authToken, String cacheDir, boolean ignoreCached, Project project, int authorIndex) {
        super(session, "rust-lang", "rfcs", authToken, cacheDir, ignoreCached, project, authorIndex);

        commentIndex = 5000000;
        organisationIndex = 5000000;
    }

    @Override
    public void scrapeGitHubData() throws IOException {
        Log.log("GitHub: Indexing PRs");
        List<GitHubService.PullRequest> prs = getPullRequestsFromAPI(OWNER, REPO);

        int success = 0;
        int failure = 0;

        for (GitHubService.PullRequest pr : prs) {
            Log.log("GitHub Scraping: Started on " + pr.getNumber());
            Proposal proposal;

            try {
                Log.oLog("GitHub Scraping: Creating Proposal");
                Person author = getOrCreateAuthor(pr.getUser());
                String id = String.format("%04d", pr.getNumber());


                proposal = Proposal.create(session, project, id, "", "", "", List.of(pr), pr.getHead().getSha());
                proposals.add(proposal);

                createStageHistory(pr, proposal);

                Comment prComment = new Comment(commentIndex, author, proposal, null, pr.getCreated_at(),
                        pr.getBody() != null ? pr.getBody() : "");
                commentIndex++;
                comments.add(prComment);

                getCommentsFromPR(pr, proposal, prComment);
                getReviewComments(pr, proposal, prComment);

                success++;
            } catch (Exception e) {
                Log.log("GitHub Scraping: Failed on PR " + pr.getNumber());
                failure++;
            }
        }

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("GitHub Scraping:  %s completed, %s failed. Failure rate: %s", success, failure, r));
    }

    public List<ProposalRevision> createRevisionHistory(Proposal proposal) throws IOException {
        List<ProposalRevision> revisions = new ArrayList<>();
        List<GitHubService.PRCommit> commits = getCommitListFromAPI(OWNER, REPO, proposal.getPullRequests().getFirst());

        int propRevisionIndex = 0;
        for (int i = 0; i < commits.size(); i++) {
            try {
                GitHubService.PRCommit commit = commits.get(i);
                GitHubService.PRCommit prc = getCommitFromAPI(OWNER, REPO, commit.getSha());
                GitHubService.RawFile file = null;

                //Some safeties to avoid weird states with missing files and existing authors.
                int index = 0;
                while (file == null && index < commits.size()) {
                    try {
                        file = getFileFromAPI(OWNER, REPO, commit.getSha(), prc.getFiles()[index].getFilename());
                    } catch(Exception e) {} //silent fail
                    index++;
                }

                if (file == null) continue;

                Base64.Decoder decoder = Base64.getDecoder();
                String content = new String(decoder.decode(file.getContent().replace("\n", "")));

                Date createdAt = commit.getCommit().getAuthor().getDate();

                ProposalRevisionId prId = new ProposalRevisionId(
                        project.getId(),
                        proposal.getId().getProposalId(),
                        propRevisionIndex
                );

                ProposalRevision pr = new ProposalRevision(
                        prId,
                        proposal,
                        commit.getCommit().getMessage(),
                        createdAt,
                        content,
                        ""
                );

                revisions.add(pr);
                propRevisionIndex++;

                GitHubService.GitHubUser user = prc.getAuthor();
                if (user == null)
                    user = new GitHubService.GitHubUser(null, prc.getCommit().getAuthor().getName(), prc.getCommit().getAuthor().getEmail(), null);

                Person author = getOrCreateAuthor(user);

                proposalRevisionAuthors.add(new ProposalRevisionAuthor(new ProposalRevisionAuthorId(
                        project.getId(), proposal.getId().getProposalId(), i, author.getId()
                ), pr, author));
            } catch(Exception e) {
                Log.warn("GitHub Scraping: Failed creating revisions for " + proposal.getId().getProposalId());
            }
        }

        return revisions;
    }

    private void createStageHistory(GitHubService.PullRequest pr, Proposal proposal) {
        ProposalStatus.create(session, proposal, 0, "created", NormalizedStatus.review, pr.getCreated_at());

        if(pr.getMerged_at() != null) {
            ProposalStatus.create(session, proposal, 1, "merged", NormalizedStatus.accepted, pr.getMerged_at());
        } else if(pr.getClosed_at() != null) {
            ProposalStatus.create(session, proposal, 1, "closed", NormalizedStatus.rejected, pr.getClosed_at());
        }
    }

    /* Remnants of trying to parse HTML documents. Leaving it in for history.
    @Deprecated
    private List<Comment> getComments(Proposal proposal, Document doc) throws IOException {
        List<Comment> comments = new ArrayList<>();

        Element discussion = doc.getElementsByClass("js-discussion").getFirst();
        Element dBody = discussion.getElementsByAttributeValueContaining("data-partial-name", "Body").getFirst();
        Element dTimeline = discussion.getElementsByAttributeValueContaining("data-partial-name", "Timeline").getFirst();

        //dBody contains PR Body, "initial comment"
        Comment prBody = createFromDiv(dBody, proposal);

        comments.add(prBody);

        return comments;
    }

    /**
     * Extracts comments data from HTML source
     * @param element Should be a data-partial-name element with either Body or Timeline

    @Deprecated
    private Comment createFromDiv(Element element, Proposal proposal) throws IOException {
        Element commentFull = element.selectXpath("./div/div/div").getFirst();

        Element commentHeader = commentFull.getElementsByClass("timeline-comment-header").getFirst()
                .selectXpath("./h3/div").getFirst();

        //Person author = getOrCreateAuthor(commentHeader.selectXpath("./strong/a").getFirst());
        Date date = Date.valueOf(LocalDate.ofInstant(Instant.parse(
                commentHeader.selectXpath("./a/relative-time").getFirst().attribute("datetime").getValue()),
                ZoneId.systemDefault()
        ));

        String commentBody = commentFull.getElementsByClass("comment-body").getFirst()
                .getElementsByTag("p").getFirst().text();

        Comment comment = new Comment(commentIndex,
                null,
                proposal,
                null,
                date,
                commentBody
        );

        commentIndex++;
        return comment;
    }

    @Deprecated
    private Document getGitHubAuthorPage(Element authorHeader) throws IOException {
        File possibleCache = new File(cacheDir + "github_" + authorHeader.text() + ".html");
        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return Jsoup.parse(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping GitHub Page for " + authorHeader.text());
        }

        return getOnlineDocument("https://github.com" + authorHeader.attr("href"), possibleCache);
    }

    @Deprecated
    private Document getPRPage(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + "pr_" + pr.getId().getProposalId() + ".html");
        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return Jsoup.parse(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping " + pr.getId().getProposalId());
        }

        return getOnlineDocument(pr.getRfcPrs().getFirst(), possibleCache);
    }

    @Deprecated
    private Document getOnlineDocument(String url, File cacheFile) throws IOException {
        Log.log("GitHub Scraping: Sending Get Request for " + url);
        Document doc = Jsoup.connect(url).ignoreHttpErrors(true).get();

        String rL = doc.connection().response().header("x-ratelimit-remaining");
        rateLimit = Integer.parseInt(rL != null ? rL : "1");

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(cacheFile)
        );

        writer.write(doc.toString());
        writer.close();

        return doc;
    }
    */

}
