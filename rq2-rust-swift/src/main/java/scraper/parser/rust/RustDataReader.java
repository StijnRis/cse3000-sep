package scraper.parser.rust;

import lombok.Getter;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.hibernate.Session;
import scraper.data.*;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.ProposalId;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import util.Log;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

public class RustDataReader {

    @Getter
    private List<Proposal> proposals;

    @Getter
    private List<ProposalRevision> proposalRevisions = new ArrayList<>();

    @Getter
    private List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    @Getter
    private int authorIndex; //Assigned project id (5) * 1 million

    private final Session session;
    private final Repository repository;
    private final RustRepoReader rr;
    private final Project project;
    private final String rootDirPath;

    public RustDataReader(Session session, Project project, RustRepoReader rr, String rootDirPath, List<Proposal> proposals) throws IOException {
        this.session = session;
        this.project = project;
        this.rr = rr;
        this.rootDirPath = rootDirPath;
        this.proposals = proposals;

        authorIndex = rr.getAuthorIndex();
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(rootDirPath + ".git/"))
                .readEnvironment()
                .findGitDir()
                .build();
    }

    /**
     * Reads all RustRFCs to build a proposal list. Expects a directory to be set from the constructor using the layout in the rfcs repository.
     */
    public void readProjectMarkdownFiles() throws IllegalArgumentException {
        Log.log("Git: Adding Revisions");

        int success = 0;
        int failure = 0;

        for (Proposal proposal : proposals) {
            Log.log("Git: Started on " + proposal.getId().getProposalId());

            try {
                parseRustRFC(proposal);
                success++;
            } catch (Exception e) {
                Log.warn("Git: Failed reading history for " + proposal.getId().getProposalId());
                e.printStackTrace();
                failure++;
            }
        }

        revisionAuthors.addAll(rr.getProposalRevisionAuthors());

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("RFC Parsing: %s completed, %s failed. Failure rate: %s", success, failure, r));
    }

    /**
     * Parses a PR into a Proposal with associated revision history & data.
     */
    public void parseRustRFC(Proposal proposal) throws Exception {
        RustDataParser parser = new RustDataParser(
                session,
                repository,
                project,
                proposal,
                rootDirPath,
                proposal.getLastCommitHash(),
                authorIndex
        );

        List<ProposalRevision> revisions = List.of();
        try {
            revisions = parser.createRevisionHistory();
            authorIndex = parser.authorIndex;
        } catch (MissingObjectException mOE) {
            //Retrieve from online
            rr.setAuthorIndex(authorIndex);
            revisions = rr.createRevisionHistory(proposal);
            authorIndex = rr.getAuthorIndex();
        }

        proposalRevisions.addAll(revisions);
        revisionAuthors.addAll(parser.getRevisionAuthors());
    }

    /**
     * Proposal Entity needs some data to be created, using the HEAD of Git history as the most recent status.
     * @deprecated Now done through GitHub PRs
     */
    @Deprecated
    private Proposal createProposalFromLastState(File file) throws Exception {
        FileReader proposalReader = new FileReader(file);
        List<String> lines = proposalReader.readAllLines();

        String featureName = null;
        List<String> rfcPrs = new ArrayList<>();
        List<String> trackingIssues = new ArrayList<>();

        //Iterate through all lines
        for (int i = 0; i < lines.size() ; i++) {
            //Remove any unnecessary markdown formatting
            String line = lines.get(i).replaceFirst("- ", "");
            String lineLC = line.toLowerCase();

            if(lineLC.startsWith("feature name")) {
                featureName = extractFeatureName(line);
                break;
            }

            if(lineLC.startsWith("rfc pr")) {
                lineLC = lineLC.replace("rfc pr: ", "");
                rfcPrs = parseMarkdownURLs(lineLC);
            }

            if(lineLC.startsWith("rust issue") || lineLC.startsWith("tracking issue")) {
                lineLC = lineLC.replace("rust issue: ", "").replace("tracking issue", "");
                trackingIssues = parseMarkdownURLs(lineLC);
            }

            //Early break if possible to save time
            if(lineLC.startsWith("## Summary")) break;
        }

        //Early RFCs did not explicitly contain all fields... or have n/a
        if(featureName == null || featureName.equals("n/a")) {
            featureName = extractFeatureNameBackup(file.getName());
        }

        ProposalId pId = new ProposalId(project.getId(), extractFeatureId(file.getName()));

        return new Proposal(pId,
                project,
                null,
                featureName,
                "?",
                null,
                ""
        );
    }

    /**
     * Extracts the URL from a line with markdown url formatting.
     * @deprecated See method createProposalFromLastState
     */
    @Deprecated
    private static List<String> parseMarkdownURLs(String line) throws MalformedURLException {
        String[] linkedPrs = line.split(", ");

        List<String> prs = new ArrayList<>();
        for (int i = 0; i < linkedPrs.length; i++) {
            String url = parseMarkdownURL(linkedPrs[i]);

            if(url.startsWith("http")) {
                prs.add(url);
            }
        }

        return prs;
    }

    /**
     * Helper function
     */
    @Deprecated
    private static String parseMarkdownURL(String text) {
        return text.replaceFirst("\\[.*\\]", "")
                .replace("(", "")
                .replace(")", "")
                .split(" ")[0];
    }

    @Deprecated
    private static String extractFeatureId(String filename) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(filename.split("-")).toList().getFirst();
    }

    @Deprecated
    private static String extractFeatureName(String line) {
        return line.toLowerCase().replace("feature name: ", "").replace("`", "");
    }

    @Deprecated
    private static String extractFeatureNameBackup(String filename) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(filename.split("-")).skip(1).collect(Collectors.joining(" ")).replace(".md", "");
    }

}
