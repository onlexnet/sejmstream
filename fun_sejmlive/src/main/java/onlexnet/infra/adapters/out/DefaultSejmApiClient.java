package onlexnet.infra.adapters.out;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import onlexnet.app.ports.out.SejmApiClient;

@Component
public class DefaultSejmApiClient implements SejmApiClient {

    // API docs: https://api.sejm.gov.pl/sejm.html
    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    @Override
    public List<SejmTerm> fetchTerms() {
        return this.restClient.get()
                .uri("sejm/term")
                .retrieve()
                .body(new ParameterizedTypeReference<List<SejmTerm>>() {
                });
    }
}
