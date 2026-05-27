package scraper.parser;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.hibernate.Session;
import scraper.data.Project;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class DataReader {

    @Getter
    protected List<Proposal> proposals;

    @Getter
    protected List<ProposalRevision> proposalRevisions = new ArrayList<>();

    @Getter
    protected List<ProposalRevisionAuthor> revisionAuthors = new ArrayList<>();

    @Getter
    @Setter
    protected int authorIndex; //Assigned project id * 1 million

    protected final Session session;
    protected final Repository repository;
    protected final Project project;
    protected final String rootDirPath;

    public DataReader(Session  session, Project project, String rootDirPath, List<Proposal> proposals) throws IOException {
        this.session = session;
        this.proposals = proposals;
        this.project = project;
        this.rootDirPath = rootDirPath;

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(rootDirPath + ".git/"))
                .readEnvironment()
                .findGitDir()
                .build();
    }

    public abstract void readProjectFiles() throws IllegalArgumentException;
}
