package scraper.parser.swift;

import org.hibernate.Session;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionAuthorId;
import scraper.parser.DataReader;
import scraper.parser.GitHubService;
import util.Log;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwiftDataReader extends DataReader {

    private final SwiftRepoReader sr;

    public SwiftDataReader(Session session, Project project, SwiftRepoReader sr, String rootDirPath, List<Proposal> proposals) throws IOException {
        super(session, project, rootDirPath, proposals);
        this.sr = sr;

        authorIndex = sr.getAuthorIndex();
    }

    @Override
    public void readProjectFiles() throws IllegalArgumentException {
        Log.log("Git: Adding Revisions");

        int success = 0;
        int failure = 0;

        for (Proposal proposal : proposals) {
            Log.log("Git: Started on " + proposal.getId().getProposalId());

            List<GitHubService.PullRequest> pullRequests = proposal.getPullRequests();
            int revisionIndex = 0;
            for (int i = 0; i < pullRequests.size(); i++) {
                GitHubService.PullRequest pr = pullRequests.get(i);
                try {
                    ProposalRevision rev = createRevisionFromPR(proposal, pr, revisionIndex);
                    if(rev != null) revisionIndex++;
                    success++;
                } catch (Exception e) {
                    Log.warn("Git: Failed reading history for " + proposal.getId().getProposalId());
                    failure++;
                }
            }
        }

        revisionAuthors.addAll(sr.getProposalRevisionAuthors());

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("Swift Parsing: %s completed, %s failed. Failure rate: %s", success, failure, r));
    }

    public ProposalRevision createRevisionFromPR(Proposal proposal, GitHubService.PullRequest pr, int sequence) throws IOException {
        SwiftDataParser parser = new SwiftDataParser(repository, project, pr.getHead().getSha(), authorIndex);

        ProposalRevision revision;
        try {
            revision = parser.createRevisionFromCommit(proposal, pr, sequence);
        } catch (Exception e) {
            //Retrieve from online only if NON SE
            //if(proposal.getId().getProposalId().startsWith("SE")) return null;
            sr.setAuthorIndex(authorIndex);
            revision = sr.createRevision(proposal, pr, sequence);
            authorIndex = sr.getAuthorIndex();
        }

        List<Person> authors = getAuthorsFromContent(new Scanner(revision.getContent()));
        for (Person author : authors) {
            ProposalRevisionAuthorId praId = new ProposalRevisionAuthorId(
                    proposal.getId().getProjectId(),
                    proposal.getId().getProposalId(),
                    sequence,
                    author.getId()
            );

            revisionAuthors.add(new ProposalRevisionAuthor(praId, revision, author));
        }

        proposalRevisions.add(revision);
        return revision;
    }

    public void createProposalIndex() throws IllegalArgumentException {
        Log.log("Git: Creating proposals");

        int success = 0;
        int failure = 0;

        File rootDir = new File(rootDirPath + "proposals/");
        if(!rootDir.exists()) throw new IllegalArgumentException("Proposals directory does not exist");

        for(File file : rootDir.listFiles()) {
            if(!file.isFile()) continue;
            if(!file.getName().endsWith(".md")) continue;
            if(file.getName().startsWith("0519-borrow")) continue; //single exception

            Log.log("Git: Started on " + file.getName());

            try {
                Proposal p = parseEvolutionFile(file);
                proposals.add(p);
                success++;
            } catch (Exception e) {
                Log.warn("Git: Failed creating proposal " + file.getName());
                failure++;
            }
        }

        //revisionAuthors.addAll(rr.getProposalRevisionAuthors());

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("Swift Parsing: %s completed, %s failed. Failure rate: %s", success, failure, r));
    }

    public Proposal parseEvolutionFile(File file) throws IOException {
        Scanner scanner = new Scanner(file);
        if(file.getName().startsWith("0443")) scanner.nextLine(); //hardcoded single exception....

        String id = SwiftDataParser.getIdFromContent(scanner);
        String pitch = SwiftDataParser.getPitchFromContent(new  Scanner(file));

        return Proposal.create(session,
                project,
                id,
                "",
                "",
                file.getName().substring(5), //remove XXXX-
                new ArrayList<>(),
                pitch
        );
    }

    public List<Person> getAuthorsFromContent(Scanner scanner) {
        ArrayList<Person> authors = new ArrayList<>();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            Matcher m2 = Pattern.compile("(?<=\\* Author: ).*|(?<=\\* Authors: ).*|(?<=\\* Author\\(s\\): ).*").matcher(line);
            if (m2.find()) {
                String[] authorsRaw = m2.group().split(",");

                for (String authorRaw : authorsRaw) {
                    Matcher m3 = Pattern.compile("(?<=\\[)(.*?)(?=\\])").matcher(authorRaw);
                    String name = m3.find() ? m3.group() : authorRaw.strip();

                    Matcher m4 = Pattern.compile("(?<=\\()(.*?)(?=\\))").matcher(authorRaw);
                    String url = m4.find() ? m4.group() : "";

                    Person author = getOrCreateAuthor(name, url);
                    authors.add(author);
                }

                break;
            }
        }

        scanner.close();
        return authors;
    }

    public Person getOrCreateAuthor(String name, String url) {
        Optional<Person> opt = Person.findAuthorByName(session, name);
        if(opt.isPresent()) return opt.get();

        if(url.isBlank()) {
            Person author = Person.create(session, authorIndex, name);
            authorIndex++;
            return author;
        }

        try {
            sr.setAuthorIndex(authorIndex);
            Person author = sr.getOrCreateAuthorFromKnown(name, url);
            authorIndex = sr.getAuthorIndex();

            author.setFullName(name);
            author.save(session);

            return author;
        } catch (Exception e) {
            return getOrCreateAuthor(name, "");
        }
    }
}
