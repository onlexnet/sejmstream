package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.types.Page;
import com.restfb.types.Post;

public interface FaceApi {

    void post(String message);

    void deleteAllPost();
}

@Component
class FaceApiImpl implements FaceApi {

    private final DefaultFacebookClient pageClient;
    

    public FaceApiImpl(DefaultFacebookClient pageClient) {
        this.pageClient = pageClient;
    }

    @Override
    public void post(String message) {

        // // generate a simple image and publish in the feed
        // var imageUrl =
        // "https://www.yttags.com/blog/wp-content/uploads/2023/02/image-urls-for-testing.webp";
        // var publishPhoto = pageClient.publish("me/photos",
        // com.restfb.types.FacebookType.class,
        // com.restfb.Parameter.with("url", imageUrl),
        // com.restfb.Parameter.with("caption", "Hello from RestFB!"),
        // com.restfb.Parameter.with("is_published", false));
        // System.out.println("Published photo ID: " + publishPhoto.getId());

        // create a post

        var publishPost = pageClient.publish("me/feed",
                com.restfb.types.FacebookType.class,
                com.restfb.Parameter.with("message", message),
                com.restfb.Parameter.with("is_published", false));

    }

    @Override
    public void deleteAllPost() {
       
        
        var feed = pageClient.fetchConnection("me/feed", Post.class);
        for (var post : feed.getData()) {
            System.out.println("delete post: " + post.getMessage());
            var postId = post.getId();
            pageClient.deleteObject(postId);
        }
    }
}
