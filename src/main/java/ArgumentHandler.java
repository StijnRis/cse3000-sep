import util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ArgumentHandler {

    public static String[] validOptions = new String[]{"local", "remote", "project"};
    public static String[] validProjects = new String[]{"rust", "swift"};

    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> arguments = new HashMap<>();


        if (args.length % 2 != 0) {
            Log.error("Invalid number of arguments provided");
            System.exit(-1);
        }

        //Guaranteed to be divisible by 2
        for(int i = 0; i < (args.length / 2)+1; i=i+2)  {
            String option = args[i];
            String value = args[i+1];

            if(!option.startsWith("-")) {
                Log.error("Malformed argument option: " + option);
            }

            option = option.substring(1);

            //Make sure provided option is one that exists
            String finalOption = option;
            if(Arrays.stream(validOptions).noneMatch(opt -> Objects.equals(opt, finalOption))) {
                Log.error("Unknown argument option: " + option);
                System.exit(-1);
            }

            //Layer of safety for later
            if(value == null) {
                Log.error("Null value provided");
                System.exit(-1);
            }

            arguments.put(option, value);
        }

        return arguments;
    }

    public static void validateOptions(Map<String, String> arguments) throws IllegalArgumentException {
        //One of these needs to be provided
        if(arguments.containsKey("local")) {
            validateLocal(arguments.get("local"));
        } else if(arguments.containsKey("remote")) {
            validateRemote(arguments.get("remote"));
        } else {
            throw new IllegalArgumentException("Missing either local or remote path");
        }

        validateProject(arguments.get("project"));

    }

    private static void validateProject(String value) throws IllegalArgumentException {
        if(value == null) throw new IllegalArgumentException("Project must be specified");
        if(Arrays.stream(validProjects).noneMatch(project -> Objects.equals(project, value))) {
            Log.error("Unsupported project type: " + value);
            System.exit(-1);
        }
    }

    private static void validateLocal(String value) throws IllegalArgumentException {
        File rootDir = new File(value);

        if(!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("Provided path is not an existing directory.");
        }
    }

    private static void validateRemote(String value) {
        //TODO
    }
}
