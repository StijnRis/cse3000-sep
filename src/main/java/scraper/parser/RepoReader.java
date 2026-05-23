package scraper.parser;

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
import scraper.data.proposal.revision.ProposalRevision;
import scraper.data.proposal.revision.ProposalRevisionAuthor;
import util.Log;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public abstract class RepoReader {
    protected final String OWNER;
    protected final String REPO;

    protected GitHubService service;

    protected Session session;
    protected String cacheDir;
    protected boolean ignoreCached;
    protected Project project;

    //State variables
    @Getter
    @Setter
    protected int authorIndex; //Assigned project id * 1 million
    @Getter
    protected int commentIndex; //Assigned project id * 1 million
    protected int rateLimit = 1;
    protected int organisationIndex;


    //Result storage
    @Getter
    protected List<Proposal> proposals = new ArrayList<>();

    @Getter
    protected List<ProposalRevisionAuthor> proposalRevisionAuthors = new ArrayList<>();

    @Getter
    protected List<Comment> comments = new ArrayList<>();


    public RepoReader(Session session, String owner, String repo, String authToken, String cacheDir, boolean ignoreCached, Project project, int authorIndex) {
        this.session = session;
        this.OWNER = owner;
        this.REPO = repo;
        this.cacheDir = cacheDir;
        this.ignoreCached = ignoreCached;
        this.project = project;
        this.authorIndex = authorIndex;

        Retrofit retrofit = buildRetrofit(authToken);
        service = retrofit.create(GitHubService.class);
    }

    public abstract void scrapeGitHubData() throws IOException;

    //public abstract List<ProposalRevision> createRevisionHistory(Proposal proposal) throws IOException;

    //Discussion and comment extraction

    protected void getCommentsFromPR(GitHubService.PullRequest pr, Proposal proposal, Comment prComment) throws IOException {
        Log.oLog("GitHub Scraping: Grabbing Conversations");
        List<GitHubService.Conversation> conversations = getConversationFromAPI(OWNER, REPO, pr);

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
    }

    protected void getReviewComments(GitHubService.PullRequest pr, Proposal proposal, Comment prComment) throws IOException {
        Log.oLog("GitHub Scraping: Grabbing Review Comments");
        List<GitHubService.ReviewComment> revComments = getReviewCommentsFromAPI(OWNER, REPO, pr);
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
    }


    //Discussion related

    protected List<GitHubService.Conversation> getConversationFromAPI(String owner, String repo, GitHubService.PullRequest pr) throws IOException {
        File cache = new File(cacheDir + "conv_" + owner + "_" + repo + "_" + pr.getNumber() + ".json");

        return List.of(prepareData(cache,
                GitHubService.Conversation[].class,
                service.getPrConversations(owner, repo, String.valueOf(pr.getNumber()))
        ));
    }

    protected List<GitHubService.ReviewComment> getReviewCommentsFromAPI(String owner, String repo, GitHubService.PullRequest pr) throws IOException {
        File cache = new File(cacheDir + "revc_" + owner + "_" + repo + "_" + pr.getNumber() + ".json");

        return List.of(prepareData(cache,
                GitHubService.ReviewComment[].class,
                service.getPrComments(owner, repo, String.valueOf(pr.getNumber()))
        ));
    }



    //Commits and File related

    protected List<GitHubService.PRCommit> getCommitListFromAPI(String owner, String repo, GitHubService.PullRequest pr) throws IOException {
        File cache = new File(cacheDir + "pr_commits_" + owner + "_" + repo + "_" + pr.getNumber() + ".json");

        return List.of(prepareData(cache,
                GitHubService.PRCommit[].class,
                service.getPrCommits(owner, repo, String.valueOf(pr.getNumber()))
        ));
    }

    protected GitHubService.PRCommit getCommitFromAPI(String owner, String repo, String ref) throws IOException {
        File cache = new File(cacheDir + "commit_" + owner + "_" + repo + "_" + ref + ".json");

        return prepareData(cache,
                GitHubService.PRCommit.class,
                service.getCommit(owner, repo, ref)
        );
    }

    protected GitHubService.RawFile getFileFromAPI(String owner, String repo, String ref, String filePath) throws IOException {
        String[] pathSplit = filePath.split("/");
        String fileName = pathSplit[pathSplit.length - 1];
        File cache = new File(cacheDir + "file_" + owner + "_" + repo + "_" + ref + "_" + fileName + ".json");

        return prepareData(cache,
                GitHubService.RawFile.class,
                service.getFile(owner, repo, filePath, ref)
        );
    }



    //Author related

    public Person getOrCreateAuthorFromKnown(String name, String url) throws IOException {
        String[] splitUrl = url.split("/");
        String login = splitUrl.length > 1 ? splitUrl[splitUrl.length-1] : "";
        GitHubService.GitHubUser user = new GitHubService.GitHubUser(login, name, "", "");
        return getOrCreateAuthor(user);
    }

    protected Person getOrCreateAuthor(GitHubService.GitHubUser user) throws IOException {
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

    private GitHubService.GitHubUser getFullUserFromAPI(GitHubService.GitHubUser user) throws IOException {
        File cache = new File(cacheDir + "gh_user_" + user.getLogin() + ".json");

        return prepareData(cache,
                GitHubService.GitHubUser.class,
                service.getUser(user.getLogin())
        );
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



    //PR Index Building

    protected List<GitHubService.PullRequest> getPullRequestsFromAPI(String owner, String repo) throws IOException {
        File subCache = new File(cacheDir + "_" + owner + "_" + repo + "_prIndex_0.json");
        if(!ignoreCached && subCache.isFile()) {
            Log.oLog("GitHub Scraping: Only subcache exists, compiling");
            return compilePRSubCaches(owner, repo);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Cannot index PRs");
            throw new IOException("Rate Limit Reached");
        }

        int subIndex = 0;
        boolean hasNextPage = true;

        while(hasNextPage) {
            File prIndexSubCache = new File(cacheDir + "_" + owner + "_" + repo + "_prIndex_" + subIndex + ".json");
            Headers headers = writeCallToFile(prIndexSubCache, service.getPrList(owner, repo, "all", "created", "asc", 100, subIndex+1));
            subIndex++;

            String linkHeader = headers.get("link");
            if(linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
                hasNextPage = false;
            }
        }

        return compilePRSubCaches(owner, repo);
    }

    private List<GitHubService.PullRequest> compilePRSubCaches(String owner, String repo) throws IOException {
        List<GitHubService.PullRequest> arr = new ArrayList<>();

        int subIndex = 0;
        File prIndexSubCache = new File(cacheDir + "_" + owner + "_" + repo + "_prIndex_" + subIndex + ".json");

        while(prIndexSubCache.isFile()) {
            Log.oLog("GitHub Scraping: Compiling subcache, at file " + prIndexSubCache.getName());
            arr.addAll(List.of(readFromFile(prIndexSubCache, GitHubService.PullRequest[].class)));

            subIndex++;
            prIndexSubCache = new File(cacheDir + "_" + owner + "_" + repo + "_prIndex_" + subIndex + ".json");
        }

        return arr;
    }



    //Methods for storing or retrieving data from cache, fetching API calls if needed

    protected <T> T prepareData(File cacheFile, Type type, Call<ResponseBody> call) throws IOException {
        if(!ignoreCached && cacheFile.isFile()) {
            Log.oLog("GitHub Scraping: Cache hit: " + cacheFile.getName());
            return readFromFile(cacheFile, type);
        }

        if(rateLimit <= 0) {
            Log.warn("GitHub Scraping: GitHub Rate Limit reached. Skipping " + call.request().url());
            throw new IOException("Rate Limit Reached");
        }

        writeCallToFile(cacheFile, call);
        return readFromFile(cacheFile, type);
    }

    private <T> T readFromFile(File file, Type type) throws FileNotFoundException {
        Gson gson = new Gson().newBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create();
        JsonReader reader = new JsonReader(new FileReader(file));
        return (T) gson.fromJson(reader, type);
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

    protected Retrofit buildRetrofit(String authToken) {
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
}
