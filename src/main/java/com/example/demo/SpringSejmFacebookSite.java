package com.example.demo;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.types.Page;
import com.restfb.types.Post;

@Component
public class SpringSejmFacebookSite implements CommandLineRunner {

    @Value("${FB_TOKEN}")
    String fbToken;

    @Override
    public void run(String... args) throws Exception {
        var client = RestClient.create("https://api.sejm.gov.pl");
        var type = new ParameterizedTypeReference<List<MP>>() {
        };
        var body = client.get().uri("sejm/{term}/MP", "term10").retrieve().body(type);

        var activeCount = body.stream().filter(it -> it.active()).count();
        var message ="Lista posłów: " + activeCount;

        var fbClient = new DefaultFacebookClient(fbToken, Version.LATEST);
        Connection<Page> pages = fbClient.fetchConnection("me/accounts", Page.class);

        var pat = pages.getData().stream()
                .filter(it -> it.getName().equals("SejmStream2"))
                .findFirst()
                .get()
                .getAccessToken();

        var pageClient = new DefaultFacebookClient(pat, Version.LATEST);  
        var feed = pageClient.fetchConnection("me/feed", Post.class);
        for (var post : feed.getData()) {
            System.out.println("Post: " + post.getMessage());
        }

        // // generate a simple image and publish in the feed
        // var imageUrl = "https://www.yttags.com/blog/wp-content/uploads/2023/02/image-urls-for-testing.webp";
        // var publishPhoto = pageClient.publish("me/photos", com.restfb.types.FacebookType.class,
        //         com.restfb.Parameter.with("url", imageUrl),
        //         com.restfb.Parameter.with("caption", "Hello from RestFB!"),
        //         com.restfb.Parameter.with("is_published", false));
        // System.out.println("Published photo ID: " + publishPhoto.getId());

        // create a post
        // var publishPost = pageClient.publish("me/feed", com.restfb.types.FacebookType.class,
        //         com.restfb.Parameter.with("message", message),
        //         com.restfb.Parameter.with("is_published", false));
        // System.out.println("Published post ID: " + publishPost.getId());
    }

}

record MP(String accusativeName, boolean active) { }
