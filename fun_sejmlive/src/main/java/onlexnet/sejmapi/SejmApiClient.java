package onlexnet.sejmapi;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal Sejm API client used by the anonymous HTTP function.
 */
public interface SejmApiClient {

    /**
     * Fetches the current list of Sejm terms.
     *
     * @return list of Sejm terms
     */
    List<SejmTerm> fetchTerms();
}

@Component
class DefaultSejmApiClient implements SejmApiClient {

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
