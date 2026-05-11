package scraper.parser.rust;


import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import okhttp3.ResponseBody;
import org.hibernate.Session;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import scraper.data.author.Person;
import scraper.data.author.PersonUsername;
import scraper.data.proposal.Comment;
import scraper.data.proposal.Proposal;
import scraper.parser.GitHubService;
import util.Log;

import java.io.*;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RustRepoReader {

    @Getter
    private int authorIndex; //Assigned project id (5) * 1 million

    private Retrofit retrofit;
    private GitHubService service;

    private Session session;
    private String cacheDir;
    private boolean ignoreCached;
    private List<Proposal> proposals;

    //Result storage
    @Getter
    private List<Comment> comments = new ArrayList<>();

    //State variables
    private int rateLimit = 1;
    private int commentIndex = 5000000; //Assigned project id (5) * 1 million

    public RustRepoReader(Session session, String cacheDir, boolean ignoreCached, List<Proposal> proposals, int authorIndex) {
        this.session = session;
        this.cacheDir = cacheDir;
        this.ignoreCached = ignoreCached;
        this.proposals = proposals;
        this.authorIndex = authorIndex;

//        HttpLoggingInterceptor ic = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);
//        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(ic).build();

        retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com")
//                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        service = retrofit.create(GitHubService.class);
    }

    public void scrapeGitHubData() {
        Log.log("GitHub Scraping: Collecting Linked PRs");

        for (Proposal proposal : proposals) {
            Log.log("GitHub Scraping: Started on " + proposal.getTopic());

            int success = 0;
            int failure = 0;

            try {
                GitHubService.PullRequest pr = getPullRequestFromAPI(proposal);
                List<GitHubService.Conversation> conversations = getConversationFromAPI(proposal);

                Comment lastComment =  new Comment(commentIndex,
                        getOrCreateAuthor(pr.getUser()),
                        proposal,
                        null,
                        pr.getCreated_at(),
                        pr.getBody()
                );
                commentIndex++;
                comments.add(lastComment);
                success++;

                //Make sure in order.
                for (int i = 0; i < conversations.size(); i++) {
                    GitHubService.Conversation conv = conversations.get(i);

                    Comment newComment = new Comment(commentIndex,
                            getOrCreateAuthor(conv.getUser()),
                            proposal,
                            lastComment,
                            conv.getCreated_at(),
                            conv.getBody()
                    );
                    commentIndex++;
                    comments.add(newComment);
                    lastComment = newComment;
                    success++;
                }
            } catch (Exception e) {
                Log.log("GitHub Scraping: Could not get PR Page: " + proposal.getTopic());
                e.printStackTrace();
                failure++;
            }

            double r = (double) success / (success+failure);

            //Metric reporting for my own debugging of the parser
            Log.log(String.format("GitHub Scraping: %s completed, %s failed. Failure rate: %s", success, failure, r));
        }
    }

    private Person getOrCreateAuthor(GitHubService.GitHubUser user) throws IOException {
        user = getFullUserFromAPI(user);

        //Attempt to match to existing
        Optional<Person> author = Person.findAuthorByGitHubName(session, user.getLogin());
        if(author.isEmpty()) author = Person.findAuthorByEmail(session, user.getEmail());
        if(author.isEmpty()) author = Person.findAuthorByName(session, user.getName());

        if(author.isEmpty()) { //Create new
            Person person = Person.create(session, authorIndex, user.getName());
            authorIndex++;

            PersonUsername.create(session, person, "github", user.getLogin());
            if(user.getEmail() != null) PersonUsername.create(session, person, "email", user.getEmail());
            //Todo company

            return person;
        }

        //Ensure all data is present if author was found
        if (user.getLogin() != null && Person.findAuthorByGitHubName(session, user.getLogin()).isEmpty()) {
            PersonUsername.create(session, author.get(), "github", user.getLogin());
        }

        if (user.getEmail() != null && Person.findAuthorByEmail(session, user.getEmail()).isEmpty()) {
            PersonUsername.create(session, author.get(), "email", user.getEmail());
        }

        //This should always be last!
        if (user.getName() != null && Person.findAuthorByName(session, user.getName()).isEmpty()) {
            Person updated = author.get();
            updated.setFullName(user.getName());
            return updated.save(session);
        }

        return author.get();
    }

    private GitHubService.GitHubUser getFullUserFromAPI(GitHubService.GitHubUser user) throws IOException {
        File possibleCache = new File(cacheDir + "gh_user_" + user.getLogin() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseUserFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping GH User " + user.getLogin());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getUser(user.getLogin()));
        return parseUserFromFile(possibleCache);
    }

    private GitHubService.GitHubUser parseUserFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(file));
        return gson.fromJson(reader, GitHubService.GitHubUser.class);
    }

    private GitHubService.PullRequest getPullRequestFromAPI(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + "pr_" + pr.getId().getProposalId() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parsePrFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping PR " + pr.getId().getProposalId());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getPr("rust-lang", "rfcs", getPrNum(pr)));
        return parsePrFromFile(possibleCache);
    }

    private GitHubService.PullRequest parsePrFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        return gson.fromJson(reader, GitHubService.PullRequest.class);
    }

    private List<GitHubService.Conversation> getConversationFromAPI(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + "conv_" + pr.getId().getProposalId() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseConservationsFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping Conversations for " + pr.getId().getProposalId());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getPrConversations("rust-lang", "rfcs", getPrNum(pr)));
        return parseConservationsFromFile(possibleCache);
    }

    private List<GitHubService.Conversation> parseConservationsFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        GitHubService.Conversation[] arr = gson.fromJson(reader, GitHubService.Conversation[].class);
        return List.of(arr);
    }

    private String getPrNum(Proposal pr) {
        String[] splitUrl = pr.getRfcPrs().getFirst().split("/");
        return splitUrl[splitUrl.length-1];
    }

    private void writeCallToFile(File file, Call<ResponseBody> call) throws IOException {
        Log.log("GitHub Scraping: Sending Get Request for " + call.request().url().toString());

        Response<ResponseBody> response = call.execute();

        String rL = response.headers().get("x-ratelimit-remaining");
        rateLimit = Integer.parseInt(rL != null ? rL : "1");

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(file)
        );

        writer.write(response.body().string());
        writer.close();
    }





    @Deprecated
    private List<Comment> getComments(Proposal proposal, Document doc) throws IOException {
        List<Comment> comments = new ArrayList<>();

        Element discussion = doc.getElementsByClass("js-discussion").getFirst();
        Element dBody = discussion.getElementsByAttributeValueContaining("data-partial-name", "Body").getFirst();
        Element dTimeline = discussion.getElementsByAttributeValueContaining("data-partial-name", "Timeline").getFirst();

        //dBody contains PR Body, "initial comment"
        Comment prBody = createFromDiv(dBody, proposal);

        comments.add(prBody);

        return comments;
    }

    /**
     * Extracts comments data from HTML source
     * @param element Should be a data-partial-name element with either Body or Timeline
     */
    @Deprecated
    private Comment createFromDiv(Element element, Proposal proposal) throws IOException {
        Element commentFull = element.selectXpath("./div/div/div").getFirst();

        Element commentHeader = commentFull.getElementsByClass("timeline-comment-header").getFirst()
                .selectXpath("./h3/div").getFirst();

        //Person author = getOrCreateAuthor(commentHeader.selectXpath("./strong/a").getFirst());
        Date date = Date.valueOf(LocalDate.ofInstant(Instant.parse(
                commentHeader.selectXpath("./a/relative-time").getFirst().attribute("datetime").getValue()),
                ZoneId.systemDefault()
        ));

        String commentBody = commentFull.getElementsByClass("comment-body").getFirst()
                .getElementsByTag("p").getFirst().text();

        Comment comment = new Comment(commentIndex,
                null,
                proposal,
                null,
                date,
                commentBody
        );

        commentIndex++;
        return comment;
    }

    @Deprecated
    private Document getGitHubAuthorPage(Element authorHeader) throws IOException {
        File possibleCache = new File(cacheDir + "github_" + authorHeader.text() + ".html");
        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return Jsoup.parse(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping GitHub Page for " + authorHeader.text());
        }

        return getOnlineDocument("https://github.com" + authorHeader.attr("href"), possibleCache);
    }

    @Deprecated
    private Document getPRPage(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + "pr_" + pr.getId().getProposalId() + ".html");
        if(!ignoreCached && possibleCache.isFile()) {
            Log.log("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return Jsoup.parse(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping " + pr.getId().getProposalId());
        }

        return getOnlineDocument(pr.getRfcPrs().getFirst(), possibleCache);
    }

    @Deprecated
    private Document getOnlineDocument(String url, File cacheFile) throws IOException {
        Log.log("GitHub Scraping: Sending Get Request for " + url);
        Document doc = Jsoup.connect(url).ignoreHttpErrors(true).get();

        String rL = doc.connection().response().header("x-ratelimit-remaining");
        rateLimit = Integer.parseInt(rL != null ? rL : "1");

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(cacheFile)
        );

        writer.write(doc.toString());
        writer.close();

        return doc;
    }

}
