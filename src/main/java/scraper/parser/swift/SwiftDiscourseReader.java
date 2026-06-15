package scraper.parser.swift;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import org.hibernate.Session;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.data.author.PersonIdentifier;
import scraper.data.proposal.Comment;
import scraper.data.proposal.NormalizedStatus;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.ProposalStatus;
import util.Log;

import java.io.*;
import java.lang.reflect.Type;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwiftDiscourseReader {

    private Session session;
    private Project project;
    private SwiftDataReader sdr;
    private String cacheDir;
    private boolean ignoreCached;
    private int commentIndex;

    protected DiscourseService service;
    protected int rateLimit = 50;
    private LocalDateTime rateLimitTime = LocalDateTime.now().plusSeconds(10);

    //State variables
    private HashMap<String, Integer> historyIndex = new HashMap<>();
    private HashMap<Integer, DiscourseService.User> users = new HashMap<>();


    //Results
    @Getter
    protected List<Comment> comments = new ArrayList<>();

    @Getter
    protected List<Proposal> proposals;

    public SwiftDiscourseReader(Session session, Project project, SwiftDataReader sdr,  String cacheDir, boolean ignoreCached, int commentIndex, List<Proposal> proposals) {
        this.session = session;
        this.project = project;
        this.sdr = sdr;
        this.cacheDir = cacheDir;
        this.ignoreCached = ignoreCached;
        this.commentIndex = commentIndex;
        this.proposals = proposals;

        Retrofit retrofit = buildRetrofit();
        service = retrofit.create(DiscourseService.class);
    }

    public List<Comment> buildStageHistory() {
        Log.log("Swift Discourse: Retrieving Announcements");

        Map<DiscourseService.Topic, TopicData> results = new HashMap<>();
        boolean hasMore = true;
        int page = 0;
        while (hasMore) {
            try {
                Log.log("Swift Discourse: Subcategory - Announcements - Page " + page);
                DiscourseService.Category cat = getCategory("announce", 12, page);
                if(cat.getTopic_list().getTopics().length < 30) hasMore = false;

                results.putAll(processCategoryPage(cat));
                page++;
            } catch (Exception e) {
                Log.warn("Swift Discourse: Exception while processing Reviews\n" + e.getMessage());
            }
        }

        Log.log("Swift Discourse: Creating Stage History");

        //Stage History
        for (Map.Entry<DiscourseService.Topic, TopicData> entry : results.entrySet()) {
            DiscourseService.Topic topic = entry.getKey();
            TopicData data = entry.getValue();

            String rawStatus = getStatus(topic.getTitle().toLowerCase());
            NormalizedStatus status = getNormalizedStatus(rawStatus);

            createStageHistory(data.getProposal(), rawStatus, status, topic.getCreated_at());
        }

        return comments;
    }

    private static NormalizedStatus getNormalizedStatus(String rawStatus) {
        NormalizedStatus status = NormalizedStatus.unknown;
        if (rawStatus.contains("accepted") || rawStatus.contains("amended") || rawStatus.contains("amendment")) {
            status = NormalizedStatus.accepted;
        } else if (rawStatus.contains("rejected")) {
            status = NormalizedStatus.rejected;
        } else if (rawStatus.contains("review") || rawStatus.contains("deferred")) {
            status = NormalizedStatus.review;
        } else if (rawStatus.contains("withdrawn") || rawStatus.contains("revision")) {
            status = NormalizedStatus.withdrawn;
        } else if (rawStatus.contains("expired") || rawStatus.contains("returned")) {
            status = NormalizedStatus.draft;
        }
        return status;
    }

    public List<Comment> getDiscussions() {
        Log.log("Swift Discourse: Retrieving Detected Pitches");

        List<Proposal> prWithPitches = proposals.stream().filter(p -> p.getLastCommitHash() != null).toList();
        for(Proposal pr : prWithPitches) {
            try {
                Log.log("Swift Discourse: Processing Pitch for " + pr.getId().getProposalId());
                String[] split = pr.getLastCommitHash().split("/");
                String slug = split[split.length - 2];
                int id = Integer.parseInt(split[split.length - 1]);

                //Little workaround but with caching it doesnt result in more calls
                DiscourseService.TopicWithReplies twr = getRepliesFromPost(slug, id, 0);

                processTopic(pr, twr);

                createStageHistory(pr, "pitch", NormalizedStatus.draft, twr.getCreated_at());
            } catch (Exception e) {
                Log.warn("Swift Discourse: Exception while processing Pitch for " + pr.getId().getProposalId());
            }
        }

        Log.log("Swift Discourse: Retrieving Discussions");

        boolean hasMore = true;
        int page = 0;
        while (hasMore) {
            try {
                Log.log("Swift Discourse: Subcategory - Proposal Reviews - Page " + page);
                DiscourseService.Category cat = getCategory("proposal-reviews", 21, page);
                if(cat.getTopic_list().getTopics().length < 30) hasMore = false;

                Map<DiscourseService.Topic, TopicData> results = processCategoryPage(cat);

                for (Map.Entry<DiscourseService.Topic, TopicData> entry : results.entrySet()) {
                    DiscourseService.Topic topic = entry.getKey();
                    TopicData data = entry.getValue();

                    createStageHistory(data.getProposal(), "proposal review", NormalizedStatus.review, topic.getCreated_at());
                }

                page++;
            } catch (Exception e) {
                Log.warn("Swift Discourse: Exception while processing Reviews\n" + e.getMessage());
            }
        }

        return comments;
    }

    private void createStageHistory(Proposal pr, String rawStatus, NormalizedStatus status, Date createdAt) {
        int index = historyIndex.getOrDefault(pr.getId().getProposalId(), 0);
        ProposalStatus.create(session, pr, index, rawStatus, status, createdAt);
        historyIndex.put(pr.getId().getProposalId(), index+1);
    }

    private String getStatus(String title) {
        Matcher m = Pattern.compile("(?<=\\[)(.*?)(?=\\])").matcher(title);
        if (m.find()) {
            return m.group();
        }

        return "unknown";
    }

    private Map<DiscourseService.Topic, TopicData> processCategoryPage(DiscourseService.Category category) {
        for (DiscourseService.User user : category.getUsers()) {
            users.putIfAbsent(user.getId(), user);
        }

        int success = 0;
        int failure = 0;
        Map<DiscourseService.Topic, TopicData> results = new HashMap<>();

        for (DiscourseService.Topic topic : category.getTopic_list().getTopics()) {
            try {
                Log.log("Swift Discourse: Processing " + topic.getTitle());

                String id = getId(topic.getTitle());
                if(id == null) {
                    Log.warn("Swift Discourse: Skipping " + topic.getTitle() + " (Found no ID)");
                    continue;
                }

                Proposal pr = findOrCreate(id, topic);
                List<DiscourseService.Post> posts = processTopic(pr, topic);

                results.put(topic, new TopicData(posts, pr));

                success++;
            } catch (Exception e) {
                Log.warn("Swift Discourse: Exception while processing Topic" + e.getMessage());

                failure++;
            }
        }

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("GitHub Scraping:  %s completed, %s failed. Failure rate: %s", success, failure, r));

        return results;
    }

    private Proposal findOrCreate(String id, DiscourseService.Topic topic) {
        Proposal pr =  proposals.stream().filter(p -> p.getId().getProposalId().equals(id)).findFirst().orElse(null);
        if(pr == null) {
            DiscourseService.Poster poster = Arrays.stream(topic.getPosters()).filter(p -> p.description.equals("Original Poster")).findFirst().orElse(null);
            DiscourseService.User user = new DiscourseService.User(0, "ghost", "ghost");
            if(poster != null) user = users.get(poster.getUser_id());
            Person author = sdr.getOrCreateAuthor(user.name, "");
            PersonIdentifier.createIfMissing(session, author, "swift.org", "username", user.username);

            pr = Proposal.create(session, project, id, "",
                    "", null, null, null);
            proposals.add(pr);
        }

        return pr;
    }

    private String getId(String title) {
        Matcher m = Pattern.compile("\\w{2}-\\d{4}").matcher(title); //XX-0000 format
        if (m.find()) {
            String result = m.group();
            if (!result.equals("SE-0000")) {
                return result;
            }
        }

        return null;
    }

    private List<DiscourseService.Post> processTopic(Proposal pr, DiscourseService.Topic topic) throws IOException, InterruptedException {
        int replyNum = 0;

        Map<Integer, DiscourseService.Post> posts = new HashMap();

        while(topic.getPosts_count() > posts.size() && replyNum < topic.getPosts_count()) {
            DiscourseService.TopicWithReplies tWR = getRepliesFromPost(topic.getSlug(), topic.getId(), replyNum);

            for(DiscourseService.Post post : tWR.getPost_stream().getPosts()) {
                int postNum = post.getPost_number();
                posts.put(postNum, post);
                replyNum = Math.max(postNum, replyNum);
            }
        }

        Comment last = null;
        for(DiscourseService.Post post : posts.values()) {
            Person author = sdr.getOrCreateAuthor(post.name, "");

            PersonIdentifier.createIfMissing(session, author, "swift.org", "username", post.username);

            Comment prComment = new Comment(commentIndex, author, pr, last, post.getCreated_at(),
                    post.getCooked());
            commentIndex++;
            last = prComment;

            comments.add(prComment);
        }

        return posts.values().stream().toList();
    }

    private DiscourseService.Category getCategory(String name, int id, int page) throws IOException, InterruptedException {
        File cache = new File(cacheDir + "discourse_category_" + id + "_" + page + ".json");

        return prepareData(cache,
                DiscourseService.Category.class,
                service.getCategory(name, id, page));
    }

    protected DiscourseService.TopicWithReplies getRepliesFromPost(String slug, int topic_id, int page) throws IOException, InterruptedException {
        File cache = new File(cacheDir + "discourse_replies_" + slug + "_" + topic_id + "_" + page + ".json");

        return prepareData(cache,
                DiscourseService.TopicWithReplies.class,
                service.getReplies(slug, topic_id, page)
        );
    }

    protected <T> T prepareData(File cacheFile, Type type, Call<ResponseBody> call) throws IOException, InterruptedException {
        if(!ignoreCached && cacheFile.isFile()) {
            Log.oLog("Discourse Scraping: Cache hit: " + cacheFile.getName());
            return readFromFile(cacheFile, type);
        }

        if(rateLimitTime.isBefore(LocalDateTime.now())) {
            rateLimit = 50;
            rateLimitTime = LocalDateTime.now().plusSeconds(10);
        }

        if(rateLimit <= 0) {
            Log.warn("Discourse Scraping: Guessed Rate Limit reached. Waiting... ");
            Thread.sleep(1000*15);
        }

        writeCallToFile(cacheFile, call);
        return readFromFile(cacheFile, type);
    }

    private <T> T readFromFile(File file, Type type) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        return (T) gson.fromJson(reader, type);
    }

    private Headers writeCallToFile(File file, retrofit2.Call<ResponseBody> call) throws IOException {
        Log.oLog("Discourse Scraping: API Call (Rate limit: %d): %s".formatted(rateLimit, call.request().url()));

        Response<ResponseBody> response = call.execute();
        rateLimit--;

        if(response.body() == null) {
            rateLimit = 0;
            throw new IOException("Empty body from response");
        }

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(file)
        );

        writer.write(response.body().string());
        writer.close();

        return response.headers();
    }

    protected Retrofit buildRetrofit() {
        return new Retrofit.Builder()
                .baseUrl("https://forums.swift.org")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private class TopicData {
        List<DiscourseService.Post> posts;
        Proposal proposal;
    }
}
