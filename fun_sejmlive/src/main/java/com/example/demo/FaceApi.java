package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.restfb.DefaultFacebookClient;
import com.restfb.types.Post;

public interface FaceApi {

    void post(String message);

    void deleteAllPost();
}

@Component
class FaceApiImpl implements FaceApi {

    private static final Logger log = LoggerFactory.getLogger(FaceApiImpl.class);

    private final DefaultFacebookClient pageClient;

    public FaceApiImpl(DefaultFacebookClient pageClient) {
        this.pageClient = pageClient;
    }

    @Override
    public void post(String message) {

        var imageUrl = "https://www.yttags.com/blog/wp-content/uploads/2023/02/image-urls-for-testing.webp";
        var publishPhoto = pageClient.publish("me/photos",
                com.restfb.types.FacebookType.class,
                com.restfb.Parameter.with("url", imageUrl),
                com.restfb.Parameter.with("caption", "Hello from RestFB!"),
                com.restfb.Parameter.with("is_published", false));
        log.debug("Published photo id={}", publishPhoto.getId());

        pageClient.publish("me/feed",
                com.restfb.types.FacebookType.class,
                com.restfb.Parameter.with("message", message),
                com.restfb.Parameter.with("is_published", false));
    }

    @Override
    public void deleteAllPost() {
        var feed = pageClient.fetchConnection("me/feed", Post.class);
        for (var post : feed.getData()) {
            log.debug("Deleting post id={}", post.getId());
            pageClient.deleteObject(post.getId());
        }
    }
}
