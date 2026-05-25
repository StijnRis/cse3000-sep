package scraper.parser.rust;

import lombok.Getter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hibernate.Session;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.author.PersonIdentifier;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionAuthorId;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitReader;
import util.Log;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class RustDataParser {

    private final Session session;
    private final Project project;
    private final Proposal proposal;
    private final GitReader gitReader;
    private String fileName;

    //Result storage
    @Getter
    private List<ProposalRevision> revisions = new ArrayList<>();
    @Getter
    private List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    public int authorIndex;

    public RustDataParser(Session session, Repository repository, Project project, Proposal proposal, String rootDirPath, String startCommitHash, int authorIndex) throws IOException {
        this.session = session;
        this.project = project;
        this.proposal = proposal;
        this.authorIndex = authorIndex;

        this.gitReader = new GitReader(repository, startCommitHash); //todo efd2744b82ef44ce5359a293f346e4ba0a1a83c7
    }

    /**
     * Creates a list of all proposal revisions, treating every commit to the file history as a separate revision.
     */
    public List<ProposalRevision> createRevisionHistory() throws Exception {
        Log.log("RFC Parser: Creating revisions for " + proposal.getId().getProposalId());

        List<RevCommit> commits = gitReader.readProjectGitInternal();

        String[] pathSplit = gitReader.getLastPath().split("/");
        fileName = pathSplit[pathSplit.length - 1];

        for (int i = 0; i < commits.size(); i++) {
            RevCommit commit = commits.get(i);

            String fileContent = gitReader.getFileContents(commit.getTree(), commit);

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
                    extractDataFromLines(fileContent),
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
        Person person = getOrCreateAuthor(ident);

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
    private Person getOrCreateAuthor(PersonIdent ident) {
        Optional<Person> author = Person.findAuthorByName(session, ident.getName());
        if(author.isEmpty()) author = Person.findAuthorByEmail(session, "github.com", ident.getEmailAddress());

        if(author.isEmpty()) { //Create new
            Person person = Person.create(session, authorIndex, ident.getName());
            authorIndex++;

            PersonIdentifier.create(session, person, "github.com", "email", ident.getEmailAddress());
            return person;
        } else {
            return author.get();
        }
    }

    /**
     * Extract feature name from the first few lines of the markdown file containing the proposal.
     */
    private String extractDataFromLines(String fileContent) throws Exception {
        Scanner scanner = new Scanner(fileContent);
        String featureName = null;

        while (scanner.hasNextLine()) {
            //Remove any unnecessary markdown formatting
            String line = scanner.nextLine().replaceFirst("- ", "");
            String lineLC = line.toLowerCase();

            if(lineLC.startsWith("feature name")) {
                featureName = extractFeatureName(line);
                break;
            }

            //Early break if possible to save time
            if(lineLC.startsWith("## Summary")) break;
        }

        //Early RFCs did not explicitly contain all fields... or have n/a
        if(featureName == null || featureName.equals("n/a")) {
            featureName = extractFeatureNameBackup(fileName);
        }

        return featureName;
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