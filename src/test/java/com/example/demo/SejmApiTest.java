package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

public class SejmApiTest {
    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    @Test
    void contextLoads() {
        var listType = new ParameterizedTypeReference<List<Proceeding>>() {
        };
        var result = restClient.get().uri("sejm/term10/proceedings").retrieve().body(listType);



    }

}

record Proceeding(List<LocalDate> dates, int number) {
}
