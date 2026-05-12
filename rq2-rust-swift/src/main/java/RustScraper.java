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

        if(arguments.get("project").equals("rust")) {
            if(arguments.containsKey("remote")) {
                Log.log("Pulling latest changes from GitHub");
                downloadRustRepository(gitRootFolder);
            }

            scrapeLocalFiles(s, gitRootFolder);

            //Scrape online PRs and Discussions only if remote, otherwise use cached pages
            //This requires the above to link to proper proposals (PR urls in .md files)
            if(arguments.containsKey("remote")) {
                scrapeGitHubPRs(s, cacheFolder);
            }
        }

        s.close();
    }

    private static void scrapeGitHubPRs(Session s, String cacheFolder) {
        try {
            rr = new RustRepoReader(s, arguments.get("token"), cacheFolder, false, dr.getProposals(), dr.getAuthorIndex());

            rr.scrapeGitHubData();

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

    private static void scrapeLocalFiles(Session s, String rootFolder) {
        Project project = new Project(5, "Rust", "RFC", "MIT or Apache-2.0");

        //Project must already persist in database due to autogeneration of index and dependency on this.
        persistOne(s, project); //Generate internal ids for proposals, otherwise PK violation below.

        try {
            dr = new RustDataReader(s, project, rootFolder);

            dr.readProjectMarkdownFiles();

            persistAll(s, dr.getProposals());//Generate internal ids for proposals, otherwise PK violation below.
            persistAll(s, dr.getProposalRevisions());
            persistAll(s, dr.getRevisionAuthors());
        } catch (Exception e) {
            Log.error("Failed reading data from rust project: " + e);
            System.exit(-1);
        }
    }

    private static <T> void persistAll(Session s, List<T> objects) {
        Transaction tx = s.beginTransaction();
        for (int i = 0; i < objects.size(); i++) {
            s.persist(objects.get(i));
        }
        tx.commit();
    }

    private static void persistOne(Session s, Object object) {
        Transaction tx = s.beginTransaction();
        s.persist(object);
        tx.commit();
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