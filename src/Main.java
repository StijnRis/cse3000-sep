import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static String[] validOptions = new String[]{"data"};
    public static Map<String, String> arguments = new HashMap<>();

    public static void main(String[] args) {
        Log.log("Started Scraper");

        if (args.length % 2 != 0) {
            Log.error("Invalid number of arguments provided");
            System.exit(-1);
        }

        //Guaranteed to be divisible by 2
        for(int i = 0; i < args.length / 2; i=i+2)  {
            String option = args[i];
            String value = args[i+1];

            if(!option.startsWith("-")) {
                Log.error("Malformed argument option: " + option);
            }

            option = option.substring(1);

            String finalOption = option;
            if(!Arrays.stream(validOptions).anyMatch(opt -> opt == finalOption)) {
                arguments.put(option, value);
            }
        }

        arguments.forEach((opt, value) -> Log.log(opt + " " + value));

    }
}