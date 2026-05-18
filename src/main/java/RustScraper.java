import org.eclipse.jgit.api.Git;
import org.hibernate.Session;
import org.hibernate.Transaction;
import scraper.data.Project;
import scraper.parser.DataReader;
import scraper.parser.RepoReader;
import scraper.parser.rust.RustDataReader;
import scraper.parser.rust.RustRepoReader;
import scraper.parser.swift.SwiftDataReader;
import scraper.parser.swift.SwiftRepoReader;
import util.HibernateUtil;
import util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RustScraper {

    public static Map<String, String> arguments = new HashMap<>();

    public static HibernateUtil db2;
    public static RustRepoReader rrr;
    public static RustDataReader rdr;
    public static SwiftRepoReader srr;
    public static SwiftDataReader sdr;

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
        String rootFolder = arguments.get("local");
        String cacheFolder = rootFolder + "cache/";
        String dbPath = rootFolder + "dataset.sqlite";

        clearOldData(dbPath);
        new File(cacheFolder).mkdir(); //Ensure cache folder exists even for first time runs.

        db2 = new HibernateUtil(dbPath);
        Session s = db2.getSession();

        boolean runRust = arguments.get("project").equals("rust");
        boolean runSwift = arguments.get("project").equals("swift");
        boolean runAll = arguments.get("project").equals("all");

        if(runRust || runAll) {
            scrapeRust(rootFolder, cacheFolder, s);
        }
        if(runSwift || runAll) {
            scrapeSwift(rootFolder, cacheFolder, s);
        }

        s.close();
        Log.log("Exiting");
    }

    private static void scrapeSwift(String rootFolder, String cacheFolder, Session s) throws Exception {
        Log.log("Swift: Started");
        String gitRootFolder = rootFolder + "swift-evolution/";
        Project project = new Project(7, "Swift", "SE", "Apache-2.0");

        //Project must already persist in database to prevent references to transient entities.
        persistOne(s, project);

        srr = new SwiftRepoReader(s, arguments.get("token"), cacheFolder,
                Boolean.parseBoolean(arguments.getOrDefault("ignorecache", "false")),
                project, 7000000);

        scrapeGitHubPRs(s, srr);
        downloadRepository(gitRootFolder, "https://github.com/swiftlang/swift-evolution.git");

        Log.log("Swift: Building DataReader");
        sdr = new SwiftDataReader(s, project, srr, gitRootFolder, srr.getProposals());
        scrapeGitFiles(s, sdr);
    }

    private static void scrapeRust(String rootFolder, String cacheFolder, Session s) throws Exception {
        Log.log("Rust: Started");
        String gitRootFolder = rootFolder + "rfcs/";
        Project project = new Project(5, "Rust", "RFC", "MIT or Apache-2.0");

        //Project must already persist in database to prevent references to transient entities.
        persistOne(s, project);

        rrr = new RustRepoReader(s, arguments.get("token"), cacheFolder,
                Boolean.parseBoolean(arguments.getOrDefault("ignorecache", "false")),
                project,5000000);

        scrapeGitHubPRs(s, rrr);
        downloadRepository(gitRootFolder, "https://github.com/rust-lang/rfcs.git");

        Log.log("Rust: Building DataReader");
        rdr = new RustDataReader(s, project, rrr, gitRootFolder, rrr.getProposals());
        scrapeGitFiles(s, rdr);
    }

    private static void scrapeGitHubPRs(Session s, RepoReader reader) {
        try {
            reader.scrapeGitHubData();

            Log.log("DB: Persisting Proposals");
            persistAll(s, reader.getProposals());
            Log.log("DB: Persisting Comments");
            persistAll(s, reader.getComments());
        } catch (Exception e) {
            Log.error("Failed reading data from GitHub: " + e);
            System.exit(-1);
        }
    }

    private static void downloadRepository(String rootFolder, String uri) throws Exception {
        Log.log("Pulling Repository: " + uri);

        File rFFile = new File(rootFolder);
        if(rFFile.isDirectory()) {
            Git.open(rFFile)
                    .pull();
        } else {
            Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(rFFile)
                    .call();
        }
    }

    private static void scrapeGitFiles(Session s, DataReader reader) {
        try {
            reader.readProjectFiles();

            Log.log("DB: Persisting Proposal Revisions");
            persistAll(s, reader.getProposalRevisions());
            Log.log("DB: Persisting Revision Authors");
            persistAll(s, reader.getRevisionAuthors());
        } catch (Exception e) {
            Log.error("Failed reading data from project: " + e);
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