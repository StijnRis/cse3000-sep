package scraper.parser.rust;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionAuthorId;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitReader;
import util.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class RustDataParser {

    private Session session;
    private Project project;
    private Proposal proposal;
    private GitReader gitReader;
    private String fileName;

    //Result storage
    @Getter
    private List<ProposalRevision> revisions = new ArrayList<>();
    @Getter
    private List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    public int authorIndex;

    public RustDataParser(Session session, Project project, Proposal proposal, String rootDirPath, String path, int authorIndex) throws IOException {
        this.session = session;
        this.project = project;
        this.proposal = proposal;
        this.gitReader = new GitReader(rootDirPath, path);

        String[] pathSplit = path.split("/");
        this.fileName = pathSplit[pathSplit.length - 1];
        this.authorIndex = authorIndex;
    }

    /**
     * Creates a list of all proposal revisions, treating every commit to the file history as a separate revision.
     */
    public List<ProposalRevision> createRevisionHistory() throws Exception {
        List<RevCommit> commits = gitReader.readProjectGit();

        for (int i = 0; i < commits.size(); i++) {
            RevCommit commit = commits.get(i);

            String fileContent = gitReader.getFileContents(commit.getTree(), commit);
            ProposalData data = extractDataFromLines(fileContent);

            Instant time = commit.getAuthorIdent().getWhenAsInstant();
            Date createdAt = Date.valueOf(LocalDate.ofInstant(time, ZoneId.systemDefault()));

            ProposalRevisionId prId = new ProposalRevisionId(
                    project.getId(),
                    proposal.getId().getProposalId(),
                    i
            );

            ProposalRevision pr = new ProposalRevision(
                    prId,
                    proposal,
                    data.featureName,
                    createdAt,
                    fileContent,
                    ""
            );

            revisions.add(pr);

            extractAuthor(prId, pr, commit.getAuthorIdent());
        }

        return revisions;
    }

    /**
     * Extracts author data from a commit
     */
    private void extractAuthor(ProposalRevisionId prId, ProposalRevision pr, PersonIdent ident) throws Exception {
        Person person = getOrCreateAuthor(ident.getName());

        ProposalRevisionAuthorId praId = new ProposalRevisionAuthorId(
                prId.getProjectId(),
                prId.getProposalId(),
                prId.getRevisionIndex(),
                person.getId()
        );

        revisionAuthors.add(new ProposalRevisionAuthor(praId, pr, person));
    }

    /**
     * Helper function to keep code easily readable.
     * Checks database if the person already exists, and creates them if not.
     */
    private Person getOrCreateAuthor(String name) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> root = cq.from(Person.class);
        cq.select(root).where(cb.equal(root.get("fullName"), name));

        Query<Person> query = session.createQuery(cq);
        List<Person> results = query.getResultList();

        if(results.isEmpty()) { //Create new
            Person person = new Person(authorIndex, name);
            authorIndex++;

            Transaction tx = session.beginTransaction();
            session.persist(person);
            tx.commit();
            return person;
        } else {
            return results.getFirst();
        }
    }

    /**
     * Extracts some basic information from the first few lines of the markdown file containing the proposal.
     */
    private ProposalData extractDataFromLines(String fileContent) throws Exception {
        Scanner scanner = new Scanner(fileContent);
        ProposalData data = new ProposalData();

        while (scanner.hasNextLine()) {
            //Remove any unnecessary markdown formatting
            String line = scanner.nextLine().replaceFirst("- ", "");
            String lineLC = line.toLowerCase();

            if(lineLC.startsWith("feature name")) {
                data.featureName = extractFeatureName(line);
                break;
            }

            if(lineLC.startsWith("rfc pr")) {
                lineLC = lineLC.replace("rfc pr: ", "");
                data.rfcPrs = parseMarkdownURLs(lineLC);
            }

            if(lineLC.startsWith("rust issue") || lineLC.startsWith("tracking issue")) {
                lineLC = lineLC.replace("rust issue: ", "").replace("tracking issue", "");
                data.trackingIssues = parseMarkdownURLs(lineLC);
            }

            //Early break if possible to save time
            if(lineLC.startsWith("## Summary")) break;
        }

        //Early RFCs did not explicitly contain all fields... or have n/a
        if(data.featureName == null || data.featureName.equals("n/a")) {
            data.featureName = extractFeatureNameBackup(fileName);
        }

        return data;
    }

    /**
     * Extracts the URL from a line with markdown url formatting.
     */
    private static List<URL> parseMarkdownURLs(String line) throws MalformedURLException {
        String[] linkedPrs = line.split(", ");

        List<URL> prs = new ArrayList<>();
        for (int i = 0; i < linkedPrs.length; i++) {
            String url = parseMarkdownURL(linkedPrs[i]);

            if(url.startsWith("http")) {
                prs.add(URI.create(url).toURL());
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


    /**
     * Deprecated, using commit date as date now.
     */
    @Deprecated
    private static Date extractStartDate(String line) throws Exception {
        String[] asText = line.toLowerCase().replace("start date: ", "").split("[-/]");
        if(asText.length != 3) throw new Exception("Failed parsing date: " + line);

        LocalDate date = LocalDate.EPOCH;

        try {
            date = LocalDate.of(
                    Integer.parseInt(asText[0].replaceAll("\\D", "")),
                    Integer.parseInt(asText[1].replaceAll("\\D", "")),
                    Integer.parseInt(asText[2].replaceAll("\\D", "")));
        } catch (Exception e) {
            //silently fail
        }

        return Date.valueOf(date);
    }

    private static String extractFeatureName(String line) {
        return line.toLowerCase().replace("feature name: ", "").replace("`", "");
    }

    private static String extractFeatureNameBackup(String fileName) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(fileName.split("-")).skip(1).collect(Collectors.joining(" ")).replace(".md", "");
    }
}

@Getter
@Setter
class ProposalData {
    String featureName;
    List<URL> rfcPrs;
    List<URL> trackingIssues;
}