package scraper.parser.swift;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.sql.Date;

public interface DiscourseService {

    @GET("/c/evolution/{name}/{id}.json")
    Call<ResponseBody> getCategory(@Path("name") String name, @Path("id") int id, @Query("page") int page);

    @GET("/t/{slug}/{topic_id}/{post_number}.json")
    Call<ResponseBody> getReplies(@Path("slug") String slug,  @Path("topic_id") int topic_id,  @Path("post_number") int post_number);

    @Getter
    class Post {
        int id;
        int topic_id;
        String name;
        String username;
        Date created_at;
        String cooked;
        int post_number;
        int reply_to_post_number;
    }

    @Getter
    class Topic {
        int id;
        String title;
        String slug;
        int posts_count;
        int reply_count;
        Date created_at;
        int category_id;
        Poster[] posters;
    }

    @Getter
    class Poster {
        int user_id;
        String description;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    class User {
        int id;
        String username;
        String name;
    }

    @Getter
    class TopicList {
        Topic[] topics;
        int per_page;
        String more_topics_url;
    }

    @Getter
    class Category {
        User[] users;
        TopicList topic_list;
    }

    @Getter
    class TopicWithReplies extends Topic {
        PostStream post_stream;
    }

    @Getter
    class PostStream {
        Post[] posts;
        int[] stream;
    }
}
