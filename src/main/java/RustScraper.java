import scraper.DataReader;
import scraper.proposal.RustRFC;
import util.Log;
import util.SQLiteDB;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RustScraper {

    public static Map<String, String> arguments = new HashMap<>();
    public static Set<RustRFC> rfcs = null;
    public static SQLiteDB db = null;

    public static void main(String[] args) {
        Log.log("Started Scraper");

        arguments = ArgumentHandler.parseArgs(args);
        try {
            ArgumentHandler.validateOptions(arguments);
        } catch (IllegalArgumentException e) {
            Log.error("Exception parsing arguments: ", e);
            System.exit(-1);
        }

        //Read Data

        //Local only for now
        String rootFolder = arguments.get("local");

        if(arguments.get("project").equals("rust")) {
            try {
                rfcs = DataReader.readRustProject(rootFolder);
            } catch (Exception e) {
                Log.error("Failed reading data from rust project: " + e);
                System.exit(-1);
            }
        }

        // Start SQLite Connection
        String dbPath = rootFolder + "../dataset.sqlite";
        File dbFile = new File(dbPath);

        if(!dbFile.canWrite()) {
            Log.error("Missing write access for SQLite file.");
            System.exit(-1);
        }

        if(dbFile.isFile()) {
            dbFile.delete();
        }

        db = new SQLiteDB(dbPath);

        try {
            db.setupSchema();
        } catch (Exception e) {
            Log.error("Exception setting up database: ", e);
            System.exit(-1);
        }

    }
}