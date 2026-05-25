package scraper.parser;

import lombok.Getter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import util.LogFollowCommand;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GitReader {

    private final Repository repo;
    private final LogFollowCommand lfc;

    @Getter
    private String lastPath;

    /**
     * Build a GitReader to explore the history of a file
     * @param repo Git Repo
     * @param startCommitHash Last Commit in the PR
     */
    public GitReader(Repository repo, String startCommitHash) {
        this.repo = repo;
        lfc = new LogFollowCommand(repo, startCommitHash);
    }

    /**
     * @return List of all commits for the file specified during creation
     */
    public List<RevCommit> readProjectGitInternal() throws Exception {
        ArrayList<RevCommit> proposals = new ArrayList<>(lfc.call());
        lastPath = lfc.getLastPath();

        return proposals;
    }

    /**
     * Gets the contents of a file at a specific commit in the Git History
     * @param tree FileTree to explore
     * @param commit Commit to consider
     * @return String representation of the file specified at that time
     */
    public String getFileContents(RevTree tree, RevCommit commit) throws Exception {
        String path = lfc.getPaths().get(commit.getId());
        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            if (!treeWalk.next()) {
                throw new IllegalStateException("Did not find expected file");
            }

            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repo.open(objectId);

            return new String(loader.openStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
