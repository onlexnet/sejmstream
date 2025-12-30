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
    private final FaceApi faceApi;
    private final SejmApi sejmApi;

    SpringSejmFacebookSite(FaceApi faceApi, SejmApi sejmApi) {
        this.faceApi = faceApi;
        this.sejmApi = sejmApi;
    }

    @Override
    public void run(String... args) throws Exception {
        var termInfo = sejmApi.getTerms();
        var activeTerm = termInfo.stream().filter(it -> it.current()).findAny().get();

        var listMP = sejmApi.getMPs(activeTerm.num());

        for (var mp : listMP) {
            log.info("wczytujemy dane posla {}", mp.firstLastName());
            sejmApi.getVotingStats(activeTerm.num(), mp.id());
        }

        var activeCount = listMP.stream().filter(it -> it.active()).count();
        var message = String.format("Lista posłów: %s, kadencja nr %s ", activeCount, activeTerm.num());
        faceApi.post(message);
        log.info(message);
    }
}
