package com.example.demo;

import org.springframework.beans.factory.annotation.Value;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.types.Page;
import com.restfb.types.Post;

public interface FaceApi {
    void post(String message);
}

class FaceApiImpl implements FaceApi {
    @Value("${FB_TOKEN}")
    String fbToken;

    @Override
    public void post(String message) {

        var fbClient = new DefaultFacebookClient(fbToken, Version.LATEST);
        var pages = fbClient.fetchConnection("me/accounts", Page.class);

        var pat = pages.getData().stream()
                .filter(it -> it.getName().equals("SejmStream2"))
                .findFirst()
                .get()
                .getAccessToken();
        var pageClient = new DefaultFacebookClient(pat, Version.LATEST);
        var feed = pageClient.fetchConnection("me/feed", Post.class);
        for (var post : feed.getData()) {
            System.out.println("delete post: " + post.getMessage());
            var postId = post.getId();
            pageClient.deleteObject(postId);
        }
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
}
