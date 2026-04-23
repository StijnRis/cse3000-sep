import scraper.DataReader;
import scraper.proposal.RustRFC;
import util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {

    public static Map<String, String> arguments = new HashMap<>();

    public static void main(String[] args) {
        Log.log("Started Scraper");

        arguments = ArgumentHandler.parseArgs(args);
        try {
            ArgumentHandler.validateOptions(arguments);
        } catch (IllegalArgumentException e) {
            Log.error("Exception parsing arguments", e);
            System.exit(-1);
        }

        //Read Data

        //Local only for now
        String path = arguments.get("local");


        if(arguments.get("project").equals("rust")) {
            try {
                Set<RustRFC> rfcs = DataReader.readRustProject(path);
            } catch (Exception e) {
                Log.error("Failed reading data from rust project" + e);
                System.exit(-1);
            }
        }
    }
}