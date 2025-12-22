package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Version;
import com.restfb.types.Page;
import com.restfb.types.Post;

@Component
public class SpringSejmFacebookSite implements CommandLineRunner {

    private final static Logger log = LoggerFactory.getLogger(SpringSejmFacebookSite.class);
    @Value("${FB_TOKEN}")
    String fbToken;

    @Override
    public void run(String... args) throws Exception {
        var restClient = RestClient.create("https://api.sejm.gov.pl");

        var termType = new ParameterizedTypeReference<List<Term>>() {
        };
        var termInfo = restClient.get().uri("sejm/term").retrieve().body(termType);
        for (var termItem : termInfo) {
            log.info(termItem.toString());
        }
        var activeTerm = termInfo.stream().filter(it -> it.current()).findAny().get();

        var type = new ParameterizedTypeReference<List<MP>>() {
        };
        var listMP = restClient.get().uri("sejm/term{termNo}/MP", activeTerm.num()).retrieve().body(type);

        for (var mp : listMP) {
            log.info("wczytujemy dane posla {}", mp.firstLastName());
            restClient.get().uri("sejm/term{termNo}/MP/{mpId}/votings/stats", activeTerm.num(), mp.id()).retrieve()
                    .body(new ParameterizedTypeReference<List<VotingStats>>() { });
        }

        var activeCount = listMP.stream().filter(it -> it.active()).count();
        var message = String.format("Lista posłów: %s, kadencja nr %s ", activeCount, activeTerm.num());

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

        log.info(message);
    }

}

// https://api.sejm.gov.pl/sejm/term10/MP/1
record MP(String firstLastName, int id, String club, boolean active) {
}

record Term(boolean current, LocalDate from, int num, LocalDate to) {
}

record VotingStats(
        // czy jest usprawiedliwienie nieobecności
        boolean absenceExcuse,
        // data posiedzenia
        LocalDate date,
        // liczba opuszczonych głosowań
        int numMissed,
        // liczba głosowań w danym dniu posiedzenia
        int numVotings,
        // liczba oddanych głosów
        int numVoted,
        // numer posiedzenia
        int sitting

) {
}
