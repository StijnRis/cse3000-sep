package scraper.parser.rust;


import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import scraper.data.Project;
import scraper.data.author.Affiliation;
import scraper.data.author.Organisation;
import scraper.data.author.Person;
import scraper.data.author.PersonUsername;
import scraper.data.proposal.Comment;
import scraper.data.proposal.Proposal;
import scraper.data.proposal.StageHistory;
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import scraper.data.proposal.revision.ProposalRevisionAuthorId;
import scraper.data.proposal.revision.ProposalRevisionId;
import scraper.parser.GitHubService;
import util.Log;

import java.io.*;
import java.lang.reflect.Type;
import java.sql.Date;
import java.util.*;

public class RustRepoReader {

    @Getter
    @Setter
    private int authorIndex; //Assigned project id (5) * 1 million

    private final GitHubService service;

    private final Session session;
    private final String cacheDir;
    private final boolean ignoreCached;
    private final Project project;

    //Result storage
    @Getter
    private List<Proposal> proposals = new ArrayList<>();

    @Getter
    private List<ProposalRevisionAuthor> proposalRevisionAuthors = new ArrayList<>();

    @Getter
    private List<Comment> comments = new ArrayList<>();

    //State variables
    private int rateLimit = 1;
    private int commentIndex = 5000000; //Assigned project id (5) * 1 million
    private int organisationIndex = 5000000;

    public RustRepoReader(Session session, String authToken, String cacheDir, boolean ignoreCached, Project project, int authorIndex) throws IOException {
        this.session = session;
        this.cacheDir = cacheDir;
        this.ignoreCached = ignoreCached;
        this.project = project;
        this.authorIndex = authorIndex;

        Retrofit retrofit = buildRetrofit(authToken);
        service = retrofit.create(GitHubService.class);
    }

    public void scrapeGitHubData() throws IOException {
        Log.log("GitHub: Indexing PRs");
        List<GitHubService.PullRequest> prs = getPullRequestsFromAPI();

        int success = 0;
        int failure = 0;

        for (GitHubService.PullRequest pr : prs) {
            Log.log("GitHub Scraping: Started on " + pr.getNumber());
            Proposal proposal = null;

            try {
                Log.oLog("GitHub Scraping: Creating Proposal");
                Person author = getOrCreateAuthor(pr.getUser());
                String id = String.format("%04d", pr.getNumber());


                proposal = Proposal.create(session, project, id, author, pr.getTitle(), "", pr, pr.getHead().getSha());
                proposals.add(proposal);

                createStageHistory(pr, proposal);

                Comment prComment = new Comment(commentIndex, author, proposal, null, pr.getCreated_at(),
                        pr.getBody() != null ? pr.getBody() : "");
                commentIndex++;
                comments.add(prComment);

                Log.oLog("GitHub Scraping: Grabbing Conversations");
                List<GitHubService.Conversation> conversations = getConversationFromAPI(pr);

                //Make sure in order.
                Comment lastComment = prComment;
                for (int i = 0; i < conversations.size(); i++) {
                    GitHubService.Conversation conv = conversations.get(i);

                    Comment newComment = new Comment(commentIndex,
                            getOrCreateAuthor(conv.getUser()),
                            proposal,
                            lastComment,
                            conv.getCreated_at(),
                            conv.getBody() != null ? conv.getBody() : ""
                    );
                    commentIndex++;
                    comments.add(newComment);
                    lastComment = newComment;
                }

                Log.oLog("GitHub Scraping: Grabbing Review Comments");
                List<GitHubService.ReviewComment> revComments = getReviewCommentsFromAPI(pr);
                HashMap<Integer, Comment> lastCommentAtLine = new HashMap<Integer, Comment>();
                for(GitHubService.ReviewComment revComment : revComments) {
                    Comment comment = new Comment(commentIndex,
                            getOrCreateAuthor(revComment.getUser()),
                            proposal,
                            lastCommentAtLine.getOrDefault(revComment.getLine(), prComment),
                            revComment.getCreated_at(),
                            revComment.getBody() != null ? revComment.getBody() : ""
                    );
                    commentIndex++;
                    comments.add(comment);
                    lastCommentAtLine.put(revComment.getLine(), comment);
                }

                success++;
            } catch (Exception e) {
                Log.log("GitHub Scraping: Failed on proposal " + proposal.getId().getProposalId());
                e.printStackTrace();
                failure++;
            }
        }

        double r = (double) failure / (success+failure);

        //Metric reporting for my own debugging of the parser
        Log.log(String.format("GitHub Scraping:  %s completed, %s failed. Failure rate: %s", success, failure, r));
    }

    public List<ProposalRevision> createRevisionHistory(Proposal proposal) throws IOException {
        List<ProposalRevision> revisions = new ArrayList<>();
        List<GitHubService.PRCommit> commits = getCommitListFromAPI(proposal.getPullRequest());

        for (int i = 0; i < commits.size(); i++) {
            try {
                GitHubService.PRCommit commit = commits.get(i);
                GitHubService.PRCommit prc = getCommitFromAPI(commit.getSha());
                GitHubService.RawFile file = null;

                //Some safeties to avoid weird states with missing files and existing authors.
                int index = 0;
                while (file == null && index < commits.size()) {
                    try {
                        file = getFileFromAPI(commit.getSha(), prc.getFiles()[index].getFilename());
                    } catch(Exception e) {} //silent fail
                    index++;
                }

                if (file == null) continue;

                Base64.Decoder decoder = Base64.getDecoder();
                String content = new String(decoder.decode(file.getContent().replace("\n", "")));

                Date createdAt = commit.getCommit().getAuthor().getDate();

                ProposalRevisionId prId = new ProposalRevisionId(
                        project.getId(),
                        proposal.getId().getProposalId(),
                        i
                );

                ProposalRevision pr = new ProposalRevision(
                        prId,
                        proposal,
                        commit.getCommit().getMessage(),
                        createdAt,
                        content,
                        ""
                );

                revisions.add(pr);

                GitHubService.GitHubUser user = prc.getAuthor();
                if (user == null)
                    user = new GitHubService.GitHubUser(null, prc.getCommit().getAuthor().getName(), prc.getCommit().getAuthor().getEmail(), null);

                Person author = getOrCreateAuthor(user);

                proposalRevisionAuthors.add(new ProposalRevisionAuthor(new ProposalRevisionAuthorId(
                        project.getId(), proposal.getId().getProposalId(), i, author.getId()
                ), pr, author));
            } catch(Exception e) {
                Log.warn("GitHub Scraping: Failed creating revisions for " + proposal.getId().getProposalId());
            }
        }

        return revisions;
    }

    private GitHubService.RawFile getFileFromAPI(String ref, String filePath) throws IOException {
        String[] pathSplit = filePath.split("/");
        String fileName = pathSplit[pathSplit.length - 1];
        File possibleCache = new File(cacheDir + "file_" + ref + "_" + fileName + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseFileFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping commit " + ref);
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getFile("rust-lang", "rfcs", filePath, ref));
        return parseFileFromFile(possibleCache);
    }

    private GitHubService.RawFile parseFileFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(file));
        return gson.fromJson(reader, GitHubService.RawFile.class);
    }

    private GitHubService.PRCommit getCommitFromAPI(String ref) throws IOException {
        File possibleCache = new File(cacheDir + "commit_" + ref + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseCommitFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping commit " + ref);
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getCommit("rust-lang", "rfcs", ref));
        return parseCommitFromFile(possibleCache);
    }

    private GitHubService.PRCommit parseCommitFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        return gson.fromJson(reader, GitHubService.PRCommit.class);
    }

    private List<GitHubService.PRCommit> getCommitListFromAPI(GitHubService.PullRequest pr) throws IOException {
        File possibleCache = new File(cacheDir + "pr_commits_" + pr.getNumber() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseCommitListFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping Conversations for " + pr.getNumber());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getPrCommits("rust-lang", "rfcs", String.valueOf(pr.getNumber())));
        return parseCommitListFromFile(possibleCache);
    }

    private List<GitHubService.PRCommit> parseCommitListFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        GitHubService.PRCommit[] arr = gson.fromJson(reader, GitHubService.PRCommit[].class);
        return List.of(arr);
    }

    private Person getOrCreateAuthor(GitHubService.GitHubUser user) throws IOException {
        if(user == null) user = new GitHubService.GitHubUser("ghost", null, null, null);
        if(user.getLogin() != null) user = getFullUserFromAPI(user);

        //Attempt to match to existing
        Optional<Person> author = Person.findAuthorByGitHubName(session, user.getLogin());
        if(author.isEmpty()) author = Person.findAuthorByEmail(session, user.getEmail());
        if(author.isEmpty()) author = Person.findAuthorByName(session, user.getName());

        if(author.isEmpty()) { //Create new
            Person person = Person.create(session, authorIndex, user.getName());
            authorIndex++;

            if(user.getLogin() != null) PersonUsername.create(session, person, "github", user.getLogin());
            if(user.getEmail() != null) PersonUsername.create(session, person, "email", user.getEmail());
            if(user.getCompany() != null) getOrCreateAffiliation(user.getCompany(), person);

            return person;
        }

        //Ensure all data is present if author was found
        if (user.getLogin() != null && Person.findAuthorByGitHubName(session, user.getLogin()).isEmpty()) {
            PersonUsername.create(session, author.get(), "github", user.getLogin());
        }

        if (user.getEmail() != null && Person.findAuthorByEmail(session, user.getEmail()).isEmpty()) {
            PersonUsername.create(session, author.get(), "email", user.getEmail());
        }

        if (user.getCompany() != null) getOrCreateAffiliation(user.getCompany(), author.get());

        //This should always be last!
        if (user.getName() != null && Person.findAuthorByName(session, user.getName()).isEmpty()) {
            Person updated = author.get();
            updated.setFullName(user.getName());
            return updated.save(session);
        }

        return author.get();
    }

    private Affiliation getOrCreateAffiliation(String orgName, Person person) {
        Optional<Organisation> org = Organisation.findByName(session, orgName);
        if(org.isEmpty()) {
            org = Optional.of(Organisation.create(session, organisationIndex, orgName));
            organisationIndex++;
        }

        Optional<Affiliation> aff = Affiliation.find(session, org.get(), person);
        if(aff.isEmpty()) return Affiliation.create(session, org.get(), person);
        return aff.get();
    }

    private GitHubService.GitHubUser getFullUserFromAPI(GitHubService.GitHubUser user) throws IOException {
        File possibleCache = new File(cacheDir + "gh_user_" + user.getLogin() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
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

    private void createStageHistory(GitHubService.PullRequest pr, Proposal proposal) {
        StageHistory.create(session, proposal, 0, "created", pr.getCreated_at());

        if(pr.getMerged_at() != null) {
            StageHistory.create(session, proposal, 1, "merged", pr.getMerged_at());
        } else if(pr.getClosed_at() != null) {
            StageHistory.create(session, proposal, 1, "closed", pr.getClosed_at());
        }
    }

    private GitHubService.PullRequest getPullRequestFromAPI(Proposal pr) throws IOException {
        File possibleCache = new File(cacheDir + "pr_" + pr.getId().getProposalId() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parsePrFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping PR " + pr.getId().getProposalId());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getPr("rust-lang", "rfcs", String.valueOf(pr.getPullRequest().getNumber())));
        return parsePrFromFile(possibleCache);
    }

    private GitHubService.PullRequest parsePrFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        return gson.fromJson(reader, GitHubService.PullRequest.class);
    }

    private List<GitHubService.Conversation> getConversationFromAPI(GitHubService.PullRequest pr) throws IOException {
        File possibleCache = new File(cacheDir + "conv_" + pr.getNumber() + ".json");

        if(!ignoreCached && possibleCache.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + possibleCache.getName());
            return parseConservationsFromFile(possibleCache);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping Conversations for " + pr.getNumber());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(possibleCache, service.getPrConversations("rust-lang", "rfcs", String.valueOf(pr.getNumber())));
        return parseConservationsFromFile(possibleCache);
    }

    private List<GitHubService.Conversation> parseConservationsFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        GitHubService.Conversation[] arr = gson.fromJson(reader, GitHubService.Conversation[].class);
        return List.of(arr);
    }

    private List<GitHubService.ReviewComment> getReviewCommentsFromAPI(GitHubService.PullRequest pr) throws IOException {
        File cacheReviewComments = new File(cacheDir + "revc_" + pr.getNumber() + ".json");

        if(!ignoreCached && cacheReviewComments.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + cacheReviewComments.getName());
            return parseReviewCommentsFromFile(cacheReviewComments);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping Conversations for " + pr.getNumber());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(cacheReviewComments, service.getPrComments("rust-lang", "rfcs", String.valueOf(pr.getNumber())));

        return parseReviewCommentsFromFile(cacheReviewComments);
    }

    private List<GitHubService.ReviewComment> parseReviewCommentsFromFile(File file) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        GitHubService.ReviewComment[] arr = gson.fromJson(reader, GitHubService.ReviewComment[].class);
        return List.of(arr);
    }

    private List<GitHubService.PullRequest> getPullRequestsFromAPI() throws IOException {
        File subCache = new File(cacheDir + "_prIndex_0.json");
        if(!ignoreCached && subCache.isFile()) {
            Log.oLog("GitHub Scraping: Only subcache exists, compiling");
            return compilePRSubCaches();
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Cannot index PRs");
            throw new IOException("Rate Limit Reached");
        }

        int subIndex = 0;
        boolean hasNextPage = true;

        while(hasNextPage) {
            File prIndexSubCache = new File(cacheDir + "_prIndex_" + subIndex + ".json");
            Headers headers = writeCallToFile(prIndexSubCache, service.getPrList("rust-lang", "rfcs", "all", "created", "asc", 100, subIndex+1));
            subIndex++;

            String linkHeader = headers.get("link");
            if(linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
                hasNextPage = false;
            }
        }

        return compilePRSubCaches();
    }

    private List<GitHubService.PullRequest> compilePRSubCaches() throws IOException {
        List<GitHubService.PullRequest> arr = new ArrayList<>();
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();

        int subIndex = 0;
        File prIndexSubCache = new File(cacheDir + "_prIndex_" + subIndex + ".json");

        while(prIndexSubCache.isFile()) {
            Log.oLog("GitHub Scraping: Compiling subcache, at file " + prIndexSubCache.getName());
            JsonReader reader = new JsonReader(new FileReader(prIndexSubCache));
            arr.addAll(List.of(gson.fromJson(reader, GitHubService.PullRequest[].class)));

            subIndex++;
            prIndexSubCache = new File(cacheDir + "_prIndex_" + subIndex + ".json");
        }

        return arr;
    }

    private Headers writeCallToFile(File file, Call<ResponseBody> call) throws IOException {
        Log.oLog("GitHub Scraping: API Call (Rate limit: %d): %s".formatted(rateLimit, call.request().url()));

        Response<ResponseBody> response = call.execute();

        String rL = response.headers().get("x-ratelimit-remaining");
        rateLimit = Integer.parseInt(rL != null ? rL : "1");

        if(response.body() == null) throw new IOException("Empty body from response");

        //Save document to disk as cache for rate limiting reasons
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(file)
        );

        writer.write(response.body().string());
        writer.close();

        return response.headers();
    }

    private Retrofit buildRetrofit(String authToken) {
        Retrofit.Builder retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com")
                .addConverterFactory(GsonConverterFactory.create());

        if (authToken == null) return retrofit.build();

        // Source - https://stackoverflow.com/a/41082979
        // Posted by iagreen
        // Retrieved 2026-05-12, License - CC BY-SA 3.0

        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
            @NotNull
            @Override
            public okhttp3.Response intercept(@NotNull Chain chain) throws IOException {
                Request newRequest  = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build();
                return chain.proceed(newRequest);
            }
        }).build();

        return retrofit.client(client).build();
    }

    /* Remnants of trying to parse HTML documents. Leaving it in for history.
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
    */

}
