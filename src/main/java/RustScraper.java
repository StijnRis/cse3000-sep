import org.hibernate.Session;
import org.hibernate.Transaction;
import scraper.parser.rust.RustDataReader;
import scraper.data.Project;
import util.HibernateUtil;
import util.Log;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RustScraper {

    public static Map<String, String> arguments = new HashMap<>();

    public static HibernateUtil db2;
    public static RustDataReader dr;


    public static void main(String[] args) throws SQLException {
        Log.log("Started Scraper");

        arguments = ArgumentHandler.parseArgs(args);
        try {
            ArgumentHandler.validateOptions(arguments);
        } catch (IllegalArgumentException e) {
            Log.error("Exception parsing arguments: ", e);
            System.exit(-1);
        }

        //Local only for now
        String rootFolder = arguments.get("local");
        String dbPath = rootFolder + "../dataset.sqlite";
        clearOldData(dbPath);

        db2 = new HibernateUtil(dbPath);
        Session s = db2.getSession();

        if(arguments.get("project").equals("rust")) {
            scrapeLocalFiles(s, rootFolder);
        }

        s.close();
    }

    private static void scrapeLocalFiles(Session s, String rootFolder) {
        Project project = new Project(5, "Rust", "RFC", "MIT or Apache-2.0");

        //Project must already persist in database due to autogeneration of index and dependency on this.
        persistOne(s, project); //Generate internal ids for proposals, otherwise PK violation below.

        try {
            dr = new RustDataReader(s, project, rootFolder);
            dr.readProjectMarkdownFiles();
        } catch (Exception e) {
            Log.error("Failed reading data from rust project: " + e);
            System.exit(-1);
        }

        persistAll(s, dr.getProposals());//Generate internal ids for proposals, otherwise PK violation below.
        persistAll(s, dr.getProposalRevisions());
        persistAll(s, dr.getRevisionAuthors());
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