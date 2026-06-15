package scraper.parser.swift;

import lombok.Setter;
import org.hibernate.Session;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.proposal.Comment;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitHubService;
import scraper.parser.RepoReader;
import util.Log;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwiftRepoReader extends RepoReader {
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    @Setter
    private SwiftDataReader sdr;
    private int fallbackId = 0;

    public SwiftRepoReader(Session session, String authToken, String cacheDir, boolean ignoreCached, Project project, int authorIndex) {
        super(session, "swiftlang", "swift-evolution", authToken, cacheDir, ignoreCached, project, authorIndex);

        commentIndex = 7000000;
        organisationIndex = 7000000;
    }

    @Override
    public void scrapeGitHubData() throws IOException {
        Log.log("GitHub: Indexing PRs");
        List<GitHubService.PullRequest> prs = getPullRequestsFromAPI(OWNER, REPO);

        int success = 0;
        int failure = 0;

        for (GitHubService.PullRequest pr : prs) {
            Log.log("GitHub Scraping: Started on " + pr.getNumber());

            try {
                GitHubService.PRCommit commit = getCommitFromAPI(OWNER, REPO, pr.getHead().getSha());
                List<GitHubService.CommitFile> filepaths = Arrays.stream(commit.getFiles())
                        .filter(file -> file.getFilename().contains("proposals/"))
                        .toList();
                if(filepaths.isEmpty()) {
                    String name = commit.getFiles().length > 0 ? commit.getFiles()[0].getFilename() : "null";
                    Log.warn("GitHub Scraping: Skipping " + pr.getNumber() + " (No valid proposal file: " + name + ")");
                    continue;
                }

                //Filters out some bad "changed whitespace" PRs, though some PRs contain a few changes
                if(filepaths.size() > 6) {
                    Log.warn("GitHub Scraping: Skipping " + pr.getNumber() + " Many files included, this is likely a restructuring commit.");
                }

                for (GitHubService.CommitFile path : filepaths) {
                    processFile(commit, pr, path.getFilename());
                }

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



    private void processFile(GitHubService.PRCommit commit, GitHubService.PullRequest pr, String filepath) throws IOException {
        String[] pathSplit = filepath.split("/");
        String filename = pathSplit[pathSplit.length-1];

        Matcher m = Pattern.compile("\\d{4}").matcher(filename);
        if(!m.find()) return; //Not a proposal file.

        String fileIdentifier = filename.substring(5); //remove XXXX-

        GitHubService.RawFile file = getFileFromAPI(OWNER, REPO, commit.getSha(), filepath);
        String content = new String(DECODER.decode(file.getContent().replace("\n", "")));

        Proposal proposal = proposals.stream().filter(p -> p.getFileIdentifier().equals(fileIdentifier)).findFirst().orElse(null);

        if(proposal == null) {
            proposal = createUnlistedProposal(pr, content, fileIdentifier);
        }

        Log.oLog("GitHub Scraping: Grabbing Conversations");

        sdr.setAuthorIndex(authorIndex);
        Person author = sdr.getAuthorsFromContent(new Scanner(content)).getFirst();
        authorIndex = sdr.getAuthorIndex();

        Comment prComment = new Comment(commentIndex, author, proposal, null, pr.getCreated_at(),
                pr.getBody() != null ? pr.getBody() : "");
        commentIndex++;
        comments.add(prComment);

        getCommentsFromPR(pr, proposal, prComment);
        getReviewComments(pr, proposal, prComment);

        proposal.getPullRequests().add(pr);
    }

    public ProposalRevision createRevision(Proposal proposal, GitHubService.PullRequest pr, int sequence) throws IOException {
        GitHubService.PRCommit commit = getCommitFromAPI(OWNER, REPO, pr.getHead().getSha());
        String filepath = Arrays.stream(
                commit.getFiles()).filter(file -> file.getFilename().contains(proposal.getFileIdentifier())
                ).findFirst().get().getFilename();
        GitHubService.RawFile file = getFileFromAPI(OWNER, REPO, commit.getSha(), filepath);
        String content = new String(DECODER.decode(file.getContent().replace("\n", "")));
        String implementedAt = SwiftDataParser.getImplementedAt(content);

        ProposalRevisionId prId = new ProposalRevisionId(
                project.getId(),
                proposal.getId().getProposalId(),
                sequence
        );

        return new ProposalRevision(prId, proposal, pr.getTitle(), pr.getCreated_at(), content, implementedAt);
    }

    private Proposal createUnlistedProposal(GitHubService.PullRequest pr, String content, String fileIdentifier) throws IOException {
        String title = getTitleSafe(content);
        String id = getNewFallbackId();

        Proposal p = Proposal.create(session, project, id, "", "", fileIdentifier, new ArrayList<>(), pr.getHead().getSha());
        proposals.add(p);
        return p;
    }

    private String getNewFallbackId() {
        String fallback = "XX-" + String.format("%04d", fallbackId++);
        Log.warn("GitHub Scraping: Using fallback ID " + fallback);
        return fallback;
    }

    private static String getTitleSafe(String content) {
        String[] lines = content.split("\n");
        if(lines.length > 0 && lines[0].length() > 2) return lines[0].substring(2);
        return "";
    }
}
