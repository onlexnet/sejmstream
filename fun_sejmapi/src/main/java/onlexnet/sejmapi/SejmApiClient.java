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
     * Fetches a simple Sejm API resource as a generic JSON-compatible object.
     *
     * @param path relative Sejm API path such as {@code sejm/term}
     * @return JSON payload mapped to a generic object
     */
    List<SejmTerm> fetchSimpleData(String path);
}

@Component
class DefaultSejmApiClient implements SejmApiClient {

    // API docs: https://api.sejm.gov.pl/sejm.html
    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    @Override
    public List<SejmTerm> fetchSimpleData(String path) {
        final var normalizedPath = normalizePath(path);
        return this.restClient.get()
                .uri(normalizedPath)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SejmTerm>>() {
                });
    }

    private String normalizePath(final String path) {
        if (path == null || path.isBlank()) {
            return "sejm/term";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
