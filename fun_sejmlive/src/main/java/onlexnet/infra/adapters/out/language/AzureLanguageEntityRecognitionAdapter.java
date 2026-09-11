package onlexnet.infra.adapters.out.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import onlexnet.app.ports.out.EntityRecognitionPort;
import onlexnet.app.ports.out.RecognizedEntities;
import onlexnet.app.ports.out.RecognizedEntity;

/**
 * Recognizes and links named entities using the Azure AI Language {@code analyze-text} REST API
 * (Named Entity Recognition + Entity Linking).
 */
public final class AzureLanguageEntityRecognitionAdapter implements EntityRecognitionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureLanguageEntityRecognitionAdapter.class);
    private static final String API_VERSION = "2023-04-01";
    private static final String ANALYZE_TEXT_PATH = "/language/:analyze-text?api-version=" + API_VERSION;
    // Language API caps document length; truncating keeps requests well under that limit.
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final String CATEGORY_PERSON = "Person";
    private static final String CATEGORY_ORGANIZATION = "Organization";
    private static final String CATEGORY_LOCATION = "Location";

    private final @Nullable RestClient restClient;

    /**
     * Creates an adapter using the given resource endpoint and key. When either is blank, the
     * adapter is disabled and {@link #recognize(String)} always returns an empty result.
     */
    public AzureLanguageEntityRecognitionAdapter(String endpoint, String key) {
        this.restClient = (endpoint == null || endpoint.isBlank() || key == null || key.isBlank())
                ? null
                : RestClient.builder()
                        .baseUrl(endpoint)
                        .defaultHeader("Ocp-Apim-Subscription-Key", key)
                        .build();
    }

    /** Test-only constructor allowing a pre-built (e.g. mock-server-bound) RestClient. */
    AzureLanguageEntityRecognitionAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RecognizedEntities recognize(String text) {
        var restClient = this.restClient;
        if (restClient == null || text == null || text.isBlank()) {
            return RecognizedEntities.empty();
        }
        var truncated = truncate(text);
        try {
            var entities = fetchEntities(restClient, truncated);
            var canonicalByMention = fetchLinkedNames(restClient, truncated);
            return groupByCategory(entities, canonicalByMention);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to recognize entities via Azure AI Language", exception);
            return RecognizedEntities.empty();
        }
    }

    private List<NerEntity> fetchEntities(RestClient restClient, String text) {
        var response = callAnalyzeText(restClient, "EntityRecognition", text, NerResponse.class);
        var documents = response == null || response.results() == null ? null : response.results().documents();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        var entities = documents.getFirst().entities();
        return entities == null ? List.of() : entities;
    }

    private Map<String, String> fetchLinkedNames(RestClient restClient, String text) {
        var response = callAnalyzeText(restClient, "EntityLinking", text, LinkingResponse.class);
        var documents = response == null || response.results() == null ? null : response.results().documents();
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }
        var linkedEntities = documents.getFirst().entities();
        if (linkedEntities == null) {
            return Map.of();
        }
        var canonicalByMention = new LinkedHashMap<String, String>();
        for (var linked : linkedEntities) {
            if (linked.matches() == null || linked.name() == null) {
                continue;
            }
            for (var match : linked.matches()) {
                if (match.text() != null) {
                    canonicalByMention.put(match.text().toLowerCase(Locale.ROOT), linked.name());
                }
            }
        }
        return canonicalByMention;
    }

    private <T> @Nullable T callAnalyzeText(RestClient restClient, String kind, String text, Class<T> responseType) {
        var payload = Map.of(
                "kind", kind,
                "parameters", Map.of("modelVersion", "latest"),
                "analysisInput", Map.of(
                        "documents", List.of(Map.of(
                                "id", "1",
                                "language", "pl",
                                "text", text))));
        return restClient.post()
                .uri(ANALYZE_TEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(responseType);
    }

    private static RecognizedEntities groupByCategory(List<NerEntity> entities, Map<String, String> canonicalByMention) {
        var persons = new ArrayList<RecognizedEntity>();
        var organizations = new ArrayList<RecognizedEntity>();
        var locations = new ArrayList<RecognizedEntity>();
        for (var entity : entities) {
            if (entity.text() == null || entity.category() == null) {
                continue;
            }
            var canonicalName = canonicalByMention.get(entity.text().toLowerCase(Locale.ROOT));
            var recognized = new RecognizedEntity(entity.text(), canonicalName);
            switch (entity.category()) {
                case CATEGORY_PERSON -> persons.add(recognized);
                case CATEGORY_ORGANIZATION -> organizations.add(recognized);
                case CATEGORY_LOCATION -> locations.add(recognized);
                default -> { /* other NER categories are not relevant for this summary */ }
            }
        }
        return new RecognizedEntities(List.copyOf(persons), List.copyOf(organizations), List.copyOf(locations));
    }

    private static String truncate(String text) {
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NerResponse(@Nullable NerResults results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NerResults(@Nullable List<NerDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NerDocument(@Nullable List<NerEntity> entities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NerEntity(@Nullable String text, @Nullable String category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkingResponse(@Nullable LinkingResults results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkingResults(@Nullable List<LinkingDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkingDocument(@Nullable List<LinkedEntity> entities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkedEntity(@Nullable String name, @Nullable List<LinkedMatch> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkedMatch(@Nullable String text) {
    }
}
