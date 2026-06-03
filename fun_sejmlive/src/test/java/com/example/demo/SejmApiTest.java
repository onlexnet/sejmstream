package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SejmApiTest {
    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    @Test
    void contextLoads() {
        var listType = new ParameterizedTypeReference<List<Proceeding>>() {
        };
        var proceedings = restClient.get().uri("sejm/term10/proceedings").retrieve().body(listType);

        var votingType = new ParameterizedTypeReference<List<Voting>>() {
        };
        for (var proceeding : proceedings) {
            log.info(proceeding.toString());
            var voting = restClient.get().uri("sejm/term10/votings/" + proceeding.number()).retrieve().body(votingType);
            log.info(voting.toString());
        }

    }

}

record Proceeding(List<LocalDate> dates, String number) {
}

record Voting(int yes, int no, int notParticipating) {

}
