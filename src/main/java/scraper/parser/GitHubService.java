package scraper.parser;

import lombok.Getter;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.sql.Date;

public interface GitHubService {

    @GET("/repos/{owner}/{repo}/pulls/{pull_number}")
    Call<ResponseBody> getPr(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("repos/{owner}/{repo}/issues/{pull_number}/comments")
    Call<ResponseBody> getPrConversations(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("/repos/{owner}/{repo}/pulls/{pull_number}/comments")
    Call<ResponseBody> getPrComments(@Path("owner") String owner, @Path("repo") String repo, @Path("pull_number") String pull_number);

    @GET("/users/{username}")
    Call<ResponseBody> getUser(@Path("username") String username);

    @Getter
    class PullRequest {
        String body;
        Date created_at;
        Date closed_at;
        Date merged_at;
        GitHubUser user;
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
    class GitHubUser {
        String login;
        String name;
        String email;
        String company;
    }
}
