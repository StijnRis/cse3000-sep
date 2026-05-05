import org.hibernate.Session;
import org.hibernate.Transaction;
import scraper.RustDataReader;
import scraper.data.Project;
import util.HibernateUtil;
import util.Log;
import util.SQLiteDB;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class RustScraper {

    public static Map<String, String> arguments = new HashMap<>();

    public static SQLiteDB db;
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

        Project project = new Project("Unspecified", "X", "NONE");

        if(arguments.get("project").equals("rust")) {
            project = new Project("Rust", "RFC", "TBD");
        }

        db2 = new HibernateUtil(dbPath);
        Session s = db2.getSession();
        Transaction tx = s.beginTransaction();

        //Project must already persist in database due to autogeneration of index and dependency on this.
        s.persist(project);
        tx.commit(); //Generate internal ids for proposals, otherwise PK violation below.
        tx = s.beginTransaction();

        //Read Data
        try {
            dr = new RustDataReader(project);
            dr.readProject(rootFolder);
        } catch (Exception e) {
            Log.error("Failed reading data from rust project: " + e);
            System.exit(-1);
        }

        //Dont use lambdas because db locks
        for (int i = 0; i < dr.getProposals().size(); i++) {
            s.persist(dr.getProposals().get(i));
        }

        tx.commit(); //Generate internal ids for proposals, otherwise PK violation below.

        tx = s.beginTransaction();

        for (int i = 0; i < dr.getProposalRevisions().size(); i++) {
            s.persist(dr.getProposalRevisions().get(i));
        }

        tx.commit();
        s.close();
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