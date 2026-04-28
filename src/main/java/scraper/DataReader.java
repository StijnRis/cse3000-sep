package scraper;

import scraper.proposal.RustRFC;
import scraper.proposal.Status;
import scraper.proposal.VersionedProposal;
import util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DataReader {

    public static HashSet<RustRFC> readRustProject(String rootDirPath) throws IllegalArgumentException, IOException {
        Log.log("Begin parsing Rust RFCs");

        HashSet<RustRFC> rfcs = new HashSet<>();

        File rootDir = new File(rootDirPath);
        File textDir = Objects.requireNonNull(rootDir.listFiles(file -> file.isDirectory() && file.getName().equals("text")))[0];

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        Arrays.stream(Objects.requireNonNull(textDir.listFiles())).forEach(file -> {
            //temporary for during coding
            if(success.get() + failure.get() < 10) {

                Log.log("RFC Parsing -> Started on " + file.getName());

                if (file.isFile()) {
                    RustRFC rfc = null;
                    try {
                        rfc = parseRustRFC(file);
                    } catch (Exception e) {
                        Log.warn("Could not parse RFC File: " + file.getName());
                        e.printStackTrace();
                        failure.getAndIncrement();
                    }

                    if (rfc != null) {
                        rfcs.add(rfc);
                        success.getAndIncrement();
                    }
                }
            }
        });

        int s = success.get();
        int f = failure.get();
        double r = (double) f / (s+f);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("RFC Parsing -> %s completed, %s failed. Failure rate: %s", s, f, r));

        return rfcs;
    }

    public static RustRFC parseRustRFC(File proposal) throws Exception {
        FileReader proposalReader = new FileReader(proposal);
        List<String> lines = proposalReader.readAllLines();

        String featureName = null;
        LocalDate timestamp = null;
        List<URL> rfcPrs = List.of();
        List<URL> trackingIssues = List.of();

        //Iterate through all lines
        for (int i = 0; i < lines.size() ; i++) {
            //Remove any unnecessary markdown formatting
            String line = lines.get(i).replaceFirst("- ", "");
            String lineLC = line.toLowerCase();

            if(lineLC.startsWith("feature name")) {
                featureName = extractFeatureName(line);
            }

            if(lineLC.startsWith("start date")) {
                timestamp = extractStartDate(line);
            }

            if(lineLC.startsWith("rfc pr")) {
                lineLC = lineLC.replace("rfc pr: ", "");
                rfcPrs = parseMarkdownURLs(lineLC);
            }

            if(lineLC.startsWith("rust issue") || lineLC.startsWith("tracking issue")) {
                lineLC = lineLC.replace("rust issue: ", "").replace("tracking issue", "");
                trackingIssues = parseMarkdownURLs(lineLC);
            }
        }

        //Rust only has one version per proposal, unlike some other projects
        VersionedProposal versionedProposal = new VersionedProposal(Status.UNKNOWN, timestamp, "", "", "");


        //Early RFCs did not explicitly contain all fields... or have n/a
        if(featureName == null || featureName.equals("n/a")) {
            featureName = extractFeatureNameBackup(proposal.getName());
        }


        return new RustRFC("Rust", proposal.getName(), featureName, Status.UNKNOWN, "", List.of(versionedProposal), rfcPrs, trackingIssues);
    }

    private static List<URL> parseMarkdownURLs(String line) throws MalformedURLException {
        String[] linkedPrs = line.split(", ");

        List<URL> prs = new ArrayList<>();
        for (int i = 0; i < linkedPrs.length; i++) {
            String url = parseMarkdownURL(linkedPrs[i]);

            if(url.startsWith("http")) {
                prs.add(URI.create(url).toURL());
            }
        }

        return prs;
    }

    private static String parseMarkdownURL(String text) {
        return text.replaceFirst("\\[.*\\]", "").replace("(", "").replace(")", "");
    }

    private static LocalDate extractStartDate(String line) throws Exception {
        String[] asText = line.toLowerCase().replace("start date: ", "").split("-");
        if(asText.length != 3) throw new Exception("Failed parsing date: " + line);
        return LocalDate.of(Integer.parseInt(asText[0]), Integer.parseInt(asText[1]), Integer.parseInt(asText[2]));
    }

    private static String extractFeatureName(String line) {
        return line.toLowerCase().replace("feature name: ", "").replace("`", "");
    }

    private static String extractFeatureNameBackup(String filename) {
        //Remove the number identifier prefix, remove trailing filetype and turn dashes into spaces
        return Arrays.stream(filename.split("-")).skip(1).collect(Collectors.joining(" ")).replace(".md", "");
    }

}
