package scraper.parser.swift;

import lombok.Getter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import scraper.data.Project;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitHubService;
import scraper.parser.GitReader;
import util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwiftDataParser {

    private final Repository repository;
    private final Project project;
    private final GitReader gitReader;

    //Result storage
    @Getter
    private List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    public int authorIndex;

    public SwiftDataParser(Repository repository, Project project, String startCommitHash, int authorIndex) {
        this.repository = repository;
        this.project = project;
        this.authorIndex = authorIndex;

        this.gitReader = new GitReader(repository, startCommitHash);
    }

    public ProposalRevision createRevisionFromCommit(Proposal proposal, GitHubService.PullRequest pr, int sequence) throws Exception {
        Log.log("Swift Parser: Creating revisions for " + proposal.getId().getProposalId());
        gitReader.readProjectGitInternal();
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(ObjectId.fromString(pr.getHead().getSha()));
        String content = gitReader.getFileContents(commit.getTree(), commit);
        String implemented = getImplementedAt(content);
        String title = content.split("\n")[0].substring(2);

        String id = getIdFromContent(new Scanner(content));
        if(!id.equals(proposal.getId().getProposalId()) && !id.equals("SE-NNNN")) throw new Exception();

        ProposalRevisionId prId = new ProposalRevisionId(
                project.getId(),
                proposal.getId().getProposalId(),
                sequence
        );

        return new ProposalRevision(prId, proposal, title, pr.getCreated_at(), content, implemented);
    }

    public static String getIdFromContent(Scanner scanner) {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            Matcher m = Pattern.compile("\\w{2}-\\d{4}").matcher(line); //XX-0000 format
            Matcher m2 = Pattern.compile("\\w{2}-\\w{4}").matcher(line);
            if (m.find()) {
                String result = m.group();
                if (!result.equals("SE-0000")) {
                    scanner.close();
                    return result;
                }
            }
            if(m2.find()) {
                String result = m2.group();
                if (result.startsWith("SE")) {
                    scanner.close();
                    return m2.group(); //Unassigned id.
                }
            }
        }

        scanner.close();
        return "";
    }

    public static String getPitchFromContent(Scanner scanner) {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().toLowerCase();

            Matcher m = Pattern.compile("\\(\\[pitch\\]\\((?<=\\()(.*?)(?=\\))\\)\\)").matcher(line);
            if (m.find()) {
                return m.group().substring(9).replace(")", "");
            }
        }

        scanner.close();
        return null;
    }

    public static String getImplementedAt(String content) {
        Scanner scanner = new Scanner(content);

        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().toLowerCase();

                Matcher m = Pattern.compile("implemented").matcher(line);
                if (m.find()) {
                    Matcher m2 = Pattern.compile("(?<=\\()(.*?)(?=\\))").matcher(line);
                    if (m2.find()) {
                        return m2.group();
                    }
                    return "";
                }
            }
        } catch (Exception e) {
            return "";
        }

        scanner.close();
        return "";
    }
}
