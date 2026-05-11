package scraper.parser.rust;

import lombok.Getter;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RustDataReader {

    @Getter
    private List<Proposal> proposals = new ArrayList<>();

    @Getter
    private List<ProposalRevision> proposalRevisions = new ArrayList<>();

    @Getter
    private List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    @Getter
    private int authorIndex = 5000000; //Assigned project id (5) * 1 million

    private Session session;
    private Project project;
    private String rootDirPath;

    public RustDataReader(Session session, Project project, String rootDirPath) {
        this.session = session;
        this.project = project;
        this.rootDirPath = rootDirPath;
    }

    /**
     * Reads all RustRFCs to build a proposal list. Expects a directory to be set from the constructor using the layout in the rfcs repository.
     */
    public void readProjectMarkdownFiles() throws IllegalArgumentException {
        Log.log("RFC Parsing: Collecting Files");

        HashSet<Proposal> rfcs = new HashSet<>();

        File rootDir = new File(rootDirPath);
        File textDir = null;

        try {
            textDir = Objects.requireNonNull(rootDir.listFiles(file -> file.isDirectory() && file.getName().equals("text")))[0];
        } catch(Exception e) {
            throw new IllegalArgumentException("Provided source directory does not have the correct structure");
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        Arrays.stream(Objects.requireNonNull(textDir.listFiles())).forEach(file -> {
            //temporary for during coding
            if(success.get() + failure.get() < 3) {

                Log.log("RFC Parsing: Started on " + file.getName());

                if (file.isFile()) {
                    Proposal rfc = null;
                    try {
                        parseRustRFC(file);
                        success.getAndIncrement();
                    } catch (Exception e) {
                        Log.warn("RFC Parsing: Could not parse RFC File: " + file.getName());
                        e.printStackTrace();
                        failure.getAndIncrement();
                    }
                }
            }
        });

        int s = success.get();
        int f = failure.get();
        double r = (double) f / (s+f);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("RFC Parsing: %s completed, %s failed. Failure rate: %s", s, f, r));
    }

    /**
     * Parses an RFC file into a Proposal with associated revision history & data.
     * @param file Expects File pointing to a markdown file in the format of Rust RFCs
     */
    public void parseRustRFC(File file) throws Exception {
        //i am not dealing with a single duplicate proposal_id due to a moved issue.
        if(file.getName().contains("2071-impl-trait-type-alias")) return;


        Proposal proposal = createProposalFromLastState(file);

        RustDataParser parser = new RustDataParser(
                session,
                project,
                proposal,
                rootDirPath,
                "text/" + file.getName(),
                authorIndex
        );

        List<ProposalRevision> revisions = parser.createRevisionHistory();
        authorIndex = parser.authorIndex;

        proposal.setProposer(
                parser.getRevisionAuthors().getLast().getAuthor()
        );

        proposals.add(proposal);
        proposalRevisions.addAll(revisions);
        revisionAuthors.addAll(parser.getRevisionAuthors());
    }

    /**
     * Proposal Entity needs some data to be created, using the HEAD of Git history as the most recent status.
     */
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
                rfcPrs,
                trackingIssues);
    }

    /**
     * Extracts the URL from a line with markdown url formatting.
     */
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
    private static String parseMarkdownURL(String text) {
        return text.replaceFirst("\\[.*\\]", "")
                .replace("(", "")
                .replace(")", "")
                .split(" ")[0];
    }

    private static String extractFeatureId(String filename) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(filename.split("-")).toList().getFirst();
    }

    private static String extractFeatureName(String line) {
        return line.toLowerCase().replace("feature name: ", "").replace("`", "");
    }

    private static String extractFeatureNameBackup(String filename) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(filename.split("-")).skip(1).collect(Collectors.joining(" ")).replace(".md", "");
    }

}
