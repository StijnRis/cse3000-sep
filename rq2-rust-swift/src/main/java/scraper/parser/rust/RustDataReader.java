package scraper.parser.rust;

import org.eclipse.jgit.errors.MissingObjectException;
import org.hibernate.Session;
import scraper.data.*;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.parser.DataReader;
import util.Log;

import java.io.*;
import java.util.*;

public class RustDataReader extends DataReader {

    private final RustRepoReader rr;

    public RustDataReader(Session session, Project project, RustRepoReader rr, String rootDirPath, List<Proposal> proposals) throws IOException {
        super(session, project, rootDirPath, proposals);
        this.rr = rr;

        authorIndex = rr.getAuthorIndex();
    }

    /**
     * Reads all RustRFCs to build a proposal list. Expects a directory to be set from the constructor using the layout in the rfcs repository.
     */
    @Override
    public void readProjectFiles() throws IllegalArgumentException {
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

        List<ProposalRevision> revisions;
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
}
