// Source - https://stackoverflow.com/a/11504177
// Posted by OneWorld
// Retrieved 2026-05-05, License - CC BY-SA 3.0
// Customized for use in this project

package util;

import lombok.Getter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create a Log command that enables the follow option: git log --follow -- < path >
 */
public class LogFollowCommand {

    private final Repository repository;

    @Getter
    private Map<ObjectId, String> paths = new HashMap<>();

    @Getter
    private String lastPath;

    private String startCommitHash;
    private String path;
    private Git git;

    /**
     * Create a Log command that enables the follow option: git log --follow -- < path >
     */
    public LogFollowCommand(Repository repository, String startCommitHash){
        this.repository = repository;
        this.startCommitHash = startCommitHash;
        git = new Git(repository);
    }

    /**
     * Returns the result of a git log --follow -- < path >
     * @return
     * @throws IOException
     * @throws MissingObjectException
     * @throws GitAPIException
     */
    public ArrayList<RevCommit> call() throws IOException, MissingObjectException, GitAPIException {
        ArrayList<RevCommit> commits = new ArrayList<RevCommit>();
        RevWalk walk = new RevWalk(repository);
        RevCommit start = walk.parseCommit(ObjectId.fromString(startCommitHash));

        path = getInitialFileName(start);
        lastPath = path;

        do {
            Iterable<RevCommit> log = git.log().addPath(path).call();
            if(start != null) log = git.log().add(ObjectId.fromString(start.getName())).addPath(path).call();
            for (RevCommit commit : log) {
                if (commits.contains(commit)) {
                    start = null;
                } else {
                    start = commit;
                    commits.add(commit);
                    paths.put(commit.getId(), path);

                    Log.oLog("RFC Parsing - GitReader: Traversing commit " + commit.getName());
                }
            }
            if (start == null) return commits;
        }
        while ((path = getRenamedPath( start)) != null);

        return commits;
    }

    public String getInitialFileName(RevCommit start) throws IOException, GitAPIException {
        ObjectReader reader = repository.newObjectReader();
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        oldTreeIter.reset(reader, start.getTree().getId());
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, repository.parseCommit(start.getParent(0).getId()).getTree().getId());

        List<DiffEntry> compare = git.diff()
                .setNewTree(newTreeIter)
                .setOldTree(oldTreeIter)
                .call();

        return compare.getLast().getOldPath();
    }

    /**
     * Checks for renames in history of a certain file. Returns null, if no rename was found.
     * Can take some seconds, especially if nothing is found... Here might be some tweaking necessary or the LogFollowCommand must be run in a thread.
     * @param start
     * @return String or null
     * @throws IOException
     * @throws MissingObjectException
     * @throws GitAPIException
     */
    private String getRenamedPath( RevCommit start) throws IOException, MissingObjectException, GitAPIException {
        int iteration = 0;

        Iterable<RevCommit> allCommitsLater = git.log().add(start).call();
        for (RevCommit commit : allCommitsLater) {

            TreeWalk tw = new TreeWalk(repository);
            tw.addTree(commit.getTree());
            tw.addTree(start.getTree());
            tw.setRecursive(true);
            RenameDetector rd = new RenameDetector(repository);
            rd.addAll(DiffEntry.scan(tw));
            List<DiffEntry> files = rd.compute();
            for (DiffEntry diffEntry : files) {
                if ((diffEntry.getChangeType() == DiffEntry.ChangeType.RENAME || diffEntry.getChangeType() == DiffEntry.ChangeType.COPY) && diffEntry.getNewPath().contains(path)) {
                    Log.log("RFC Parsing - GitReader: Path change " + diffEntry.toString());
                    return diffEntry.getOldPath();
                }
            }

            //Chosen arbitrarily, appears to give full results without needing to loop over 7000+ commits for no reason to ensure no matches.
            //Commit history for changed files is usually within a timeframe of a few commits for this repository.
            iteration++;
            if(iteration > 200) return null;
        }
        return null;
    }
}
