import org.eclipse.jgit.api.Git;
import org.hibernate.Session;
import org.hibernate.Transaction;
import scraper.data.Project;
import scraper.parser.rust.RustDataReader;
import scraper.parser.rust.RustRepoReader;
import util.HibernateUtil;
import util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RustScraper {

    public static Map<String, String> arguments = new HashMap<>();

    public static HibernateUtil db2;
    public static RustDataReader dr;
    public static RustRepoReader rr;

    public static void main(String[] args) throws Exception {
        Log.log("Started Scraper");

        arguments = ArgumentHandler.parseArgs(args);
        try {
            ArgumentHandler.validateOptions(arguments);
        } catch (IllegalArgumentException e) {
            Log.error("Exception parsing arguments: ", e);
            System.exit(-1);
        }

        //File path setup
        String rootFolder = "";
        if(arguments.containsKey("local")) {
            rootFolder = arguments.get("local");
        } else if(arguments.containsKey("remote")) {
            rootFolder = arguments.get("remote");
        }

        String gitRootFolder = rootFolder + "rfcs/";
        String cacheFolder = rootFolder + "cache/";
        String dbPath = rootFolder + "dataset.sqlite";

        clearOldData(dbPath);
        new File(cacheFolder).mkdir(); //Ensure cache folder exists even for first time runs.

        db2 = new HibernateUtil(dbPath);
        Session s = db2.getSession();

        Project project = new Project(5, "Rust", "RFC", "MIT or Apache-2.0");

        //Project must already persist in database due to autogeneration of index and dependency on this.
        persistOne(s, project); //Generate internal ids for proposals, otherwise PK violation below.

        if(arguments.get("project").equals("rust")) {
            if(arguments.containsKey("remote")) {
                scrapeGitHubPRs(s, project, cacheFolder);

                Log.log("Pulling latest changes from GitHub");
                downloadRustRepository(gitRootFolder);
            }


            //Scrape online PRs and Discussions only if remote, otherwise use cached pages
            //This requires the above to link to proper proposals (PR urls in .md files)
            if(arguments.containsKey("remote")) {
                scrapeGitFiles(s, project, gitRootFolder);
            }
        }

        s.close();
        Log.log("Exiting");
    }

    private static void scrapeGitHubPRs(Session s, Project project, String cacheFolder) {
        Log.log("RustRepoReader: Begin building");
        try {
            rr = new RustRepoReader(s, arguments.get("token"), cacheFolder, false, project,5000000); //dr.getProposals(), dr.getAuthorIndex());

            rr.scrapeGitHubData();

            Log.log("DB: Persisting Proposals");
            persistAll(s, rr.getProposals());
            Log.log("DB: Persisting Comments");
            persistAll(s, rr.getComments());
        } catch (Exception e) {
            Log.error("Failed reading data from GitHub: " + e);
            System.exit(-1);
        }
    }


    private static void downloadRustRepository(String rootFolder) throws Exception {
        File rFFile = new File(rootFolder);
        if(rFFile.isDirectory()) {
            Git.open(rFFile)
                    .pull();
        } else {
            Git.cloneRepository()
                    .setURI("https://github.com/rust-lang/rfcs.git")
                    .setDirectory(rFFile)
                    .call();
        }
    }

    private static void scrapeGitFiles(Session s, Project project, String rootFolder) {
        Log.log("RustDataReader: Begin building");
        try {
            dr = new RustDataReader(s, project, rr, rootFolder, rr.getProposals());

            dr.readProjectMarkdownFiles();

            Log.log("DB: Persisting Proposal Revisions");
            persistAll(s, dr.getProposalRevisions());
            Log.log("DB: Persisting Revision Authors");
            persistAll(s, dr.getRevisionAuthors());
        } catch (Exception e) {
            Log.error("Failed reading data from rust project: " + e);
            System.exit(-1);
        }
    }

    private static <T> void persistAll(Session s, List<T> objects) {
        //Many transactions preferred over a single one
        //Might take longer, but ensures the program does not fail if a single proposal is faulty.
        for (int i = 0; i < objects.size(); i++) {
            try {
                Log.oLog("DB: Persisting objects (%d/%d): %s".formatted(i, objects.size(), objects.get(i)));
                Transaction tx = s.beginTransaction();
                s.persist(objects.get(i));
                tx.commit();
            } catch(Exception e) {
                Log.warn("Could not persist object: " + objects.get(i));
            }
        }
    }

    private static void persistOne(Session s, Object object) {
        try {
            Transaction tx = s.beginTransaction();
            s.persist(object);
            tx.commit();
        } catch(Exception e) {
            Log.warn("Could not persist object: " + object);
        }
    }

    private static void clearOldData(String dbPath) {
        // Start SQLite Connection
        File dbFile = new File(dbPath);

        if(dbFile.exists() && !dbFile.canWrite()) {
            Log.error("Missing write access for SQLite file.");
            System.exit(-1);
        }

        if(dbFile.isFile()) {
            dbFile.delete();
        }
    }
}