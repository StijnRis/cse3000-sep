import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ArgumentHandler {

    public static String[] validOptions = new String[]{"local", "remote", "project", "token"};
    public static String[] validProjects = new String[]{"rust", "swift"};

    public static Map<String, String> parseArgs(String[] args) throws IllegalArgumentException {
        Map<String, String> arguments = new HashMap<>();


        if (args.length % 2 != 0 || args.length == 0) {
            throw new IllegalArgumentException("Invalid number of arguments provided");
        }

        for(int i = 0; i < args.length; i=i+2)  {
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
        arguments.forEach((arg, _) -> {
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

        if(!value.endsWith("/")) {
            throw new IllegalArgumentException("Path must end with / for internal reasons.");
        }
    }

    private static void validateRemote(String value) {
        if(!value.endsWith("/")) {
            throw new IllegalArgumentException("Path must end with / for internal reasons.");
        }
        //TODO
    }
}
