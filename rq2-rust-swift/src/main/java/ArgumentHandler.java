import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ArgumentHandler {

    public static String[] validOptions = new String[]{"local", "remote", "project"};
    public static String[] validProjects = new String[]{"rust", "swift"};

    public static Map<String, String> parseArgs(String[] args) throws IllegalArgumentException {
        Map<String, String> arguments = new HashMap<>();


        if (args.length % 2 != 0 || args.length == 0) {
            throw new IllegalArgumentException("Invalid number of arguments provided");
        }

        //Guaranteed to be divisible by 2
        for(int i = 0; i < (args.length / 2)+1; i=i+2)  {
            String option = args[i];
            String value = args[i+1];

            if(!option.startsWith("-")) {
                throw new IllegalArgumentException("Malformed argument option: " + option);
            }

            option = option.substring(1);

            //Layer of safety for later
            if(value == null) {
                throw new IllegalArgumentException("Null value provided");
            }

            arguments.put(option, value);
        }

        return arguments;
    }

    public static void validateOptions(Map<String, String> arguments) throws IllegalArgumentException {
        arguments.forEach((arg, value) -> {
            if(Arrays.stream(validOptions).noneMatch(opt -> Objects.equals(opt, arg))) {
                throw new IllegalArgumentException("Unknown argument option: " + arg);
            }
        });

        boolean hasLocal = arguments.containsKey("local");
        boolean hasRemote = arguments.containsKey("remote");

        //One of these needs to be provided
        if(hasLocal && hasRemote) {
            throw new IllegalArgumentException("More than one source specified");
        } else if(hasLocal) {
            validateLocal(arguments.get("local"));
        } else if(hasRemote) {
            validateRemote(arguments.get("remote"));
        } else {
            throw new IllegalArgumentException("Missing either local or remote path");
        }

        validateProject(arguments.get("project"));

    }

    private static void validateProject(String value) throws IllegalArgumentException {
        if(value == null) throw new IllegalArgumentException("Project must be specified");
        if(Arrays.stream(validProjects).noneMatch(project -> Objects.equals(project, value))) {
            throw new IllegalArgumentException("Unsupported project type: " + value);
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
