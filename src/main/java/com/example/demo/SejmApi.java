package com.example.demo;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

public interface SejmApi {
    List<Term> getTerms();
}

class SejmApiImpl implements SejmApi {

    @Override
    public List<Term> getTerms() {
        var restClient = RestClient.create("https://api.sejm.gov.pl");
        var termType = new ParameterizedTypeReference<List<Term>>() {
        };
        return restClient.get().uri("sejm/term").retrieve().body(termType);
  
    }

}
