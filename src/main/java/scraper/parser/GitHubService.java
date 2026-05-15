package scraper.parser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.sql.Date;

public interface GitHubService {

    @GET("/repos/{owner}/{repo}/pulls")
    Call<ResponseBody> getPrList(@Path("owner") String owner, @Path("repo") String repo, @Query("state") String state, @Query("sort") String sort, @Query("direction") String direction, @Query("per_page") Integer per_page, @Query("page") Integer page);

    @GET("/repos/{owner}/{repo}/pulls/{pull_number}")
    Call<ResponseBody> getPr(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("repos/{owner}/{repo}/issues/{pull_number}/comments")
    Call<ResponseBody> getPrConversations(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("/repos/{owner}/{repo}/pulls/{pull_number}/comments")
    Call<ResponseBody> getPrComments(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("/repos/{owner}/{repo}/pulls/{pull_number}/commits")
    Call<ResponseBody> getPrCommits(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("/repos/{owner}/{repo}/commits/{ref}")
    Call<ResponseBody> getCommit(@Path("owner") String owner, @Path("repo") String repo, @Path("ref") String ref);

    @GET("/repos/{owner}/{repo}/contents/{file}")
    Call<ResponseBody> getFile(@Path("owner") String owner, @Path("repo") String repo, @Path("file") String file, @Query("ref") String ref);

    @GET("/users/{username}")
    Call<ResponseBody> getUser(@Path("username") String username);

    @Getter
    class RawFile {
        String name;
        String path;
        String content;
    }

    @Getter
    class PRCommit {
        String sha;
        Commit commit;
        CommitFile[] files;
        GitHubUser author;
    }

    @Getter
    class CommitFile {
        String filename;
    }

    @Getter
    class Commit {
        String message;
        Author author;
    }

    @Getter
    class Author {
        String name;
        String email;
        Date date;
    }

    @Getter
    class PullRequest {
        int number;
        String title;
        String body;
        Date created_at;
        Date closed_at;
        Date merged_at;
        GitHubUser user;
        GitHead head;
    }

    @Getter
    class GitHead {
        String ref;
        String sha;
    }

    @Getter
    class ReviewComment {
        String body;
        Date created_at;
        int line; //cursed way to find out what comments reply to each other
        GitHubUser user;
    }

    @Getter
    class Conversation {
        String body;
        Date created_at;
        GitHubUser user;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    class GitHubUser {
        String login;
        String name;
        String email;
        String company;
    }
}
