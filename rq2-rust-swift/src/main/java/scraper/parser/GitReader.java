package scraper.parser;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import util.Log;
import util.LogFollowCommand;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GitReader {

    private FileRepositoryBuilder builder;
    private Repository repo;
    private LogFollowCommand lfc;

    /**
     * Build a GitReader to explore the history of a file
     * @param rootDirPath Git Root
     * @param filePath File to get all commits for
     */
    public GitReader(String rootDirPath, String filePath) throws IOException {
        builder = new FileRepositoryBuilder();
        repo = builder.setGitDir(new File(rootDirPath + ".git/"))
                .readEnvironment()
                .findGitDir()
                .build();
        lfc = new LogFollowCommand(repo, filePath);
    }

    /**
     * @return List of all commits for the file specified during creation
     */
    public List<RevCommit> readProjectGit() throws Exception {
        Log.log("GitReader: Started");

        ArrayList<RevCommit> proposals = new ArrayList<>();
        proposals.addAll(lfc.call());

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
