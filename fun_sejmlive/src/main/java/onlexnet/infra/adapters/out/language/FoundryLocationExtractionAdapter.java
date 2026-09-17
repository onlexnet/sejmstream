package onlexnet.infra.adapters.out.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.LocationCandidate;
import onlexnet.app.ports.out.LocationExtractionPort;

/**
 * Extracts Polish locality mentions from interpellation text using an LLM deployed in Azure AI
 * Foundry (Azure OpenAI-compatible chat completions REST API), instructed with a dedicated
 * geographic-extraction prompt to normalize inflected Polish place names to their canonical form.
 */
public final class FoundryLocationExtractionAdapter implements LocationExtractionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoundryLocationExtractionAdapter.class);
    private static final String API_VERSION = "2024-02-15-preview";
    // Foundry/Azure OpenAI chat completions cap prompt size well above typical interpellation
    // length; truncating keeps requests well under the model context window.
    private static final int MAX_TEXT_LENGTH = 12000;
    // Precision over recall (see prompt rule #9/quality bar): drop candidates the model itself
    // marked as low-confidence rather than surfacing noisy/false-positive localities.
    private static final double MIN_CONFIDENCE = 0.5;

    private static final String SYSTEM_PROMPT = """
            You are a high-precision geographic extractor for Polish parliamentary texts.

            Task:
            Extract all locality references related to the interpellation text and normalize them to canonical Polish place names that are geographically unambiguous.

            Rules:
            1. Extract only places in Poland.
            2. Include cities, towns, villages, districts, municipalities, and other explicitly named localities when they refer to a real place in Poland.
            3. Handle Polish inflections and case changes, for example:
               - "w Białymstoku" -> "Białystok"
               - "do Poznania" -> "Poznań"
               - "z Gdańska" -> "Gdańsk"
               - "w Łodzi" -> "Łódź"
            4. Do not include organizations, institutions, people, ministries, offices, or generic geographic descriptions that are not a place name.
            5. Do not include administrative units unless they are explicitly a locality name and clearly refer to a town/village/district.
            6. Use the official canonical Polish locality name whenever possible.
            7. Preserve the exact raw mention from the text in the output field `rawMention`.
            8. Deduplicate repeated mentions of the same place.
            9. If the location is uncertain or not clearly identifiable, either:
               - return it only with a low confidence, or
               - omit it if it is not strong enough.
            10. If there are no valid localities, return an empty array.

            Output format:
            Return valid JSON only, with no markdown fences, no comments, and no extra prose.

            JSON schema:
            [
              {
                "rawMention": "string",
                "canonicalName": "string",
                "province": "string or null",
                "county": "string or null",
                "latitude": "number or null",
                "longitude": "number or null",
                "confidence": 0.0
              }
            ]

            Quality bar:
            - Prefer precision over recall.
            - Avoid false positives.
            - Prefer canonical names that are geographically clear and official.
            - For ambiguous references, prefer omission over incorrect normalization.
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final @Nullable RestClient restClient;
    private final String chatCompletionsPath;

    /**
     * Creates an adapter targeting the given Foundry/Azure OpenAI endpoint and model deployment.
     * When endpoint, key, or deployment name is blank, the adapter is disabled and
     * {@link #extractLocations(String)} always returns an empty list.
     */
    public FoundryLocationExtractionAdapter(String endpoint, String key, String deploymentName) {
        this.chatCompletionsPath = "/openai/deployments/" + deploymentName + "/chat/completions?api-version=" + API_VERSION;
        this.restClient = (endpoint == null || endpoint.isBlank()
                || key == null || key.isBlank()
                || deploymentName == null || deploymentName.isBlank())
                        ? null
                        : RestClient.builder()
                                .baseUrl(endpoint)
                                .defaultHeader("api-key", key)
                                .build();
    }

    /** Test-only constructor allowing a pre-built (e.g. mock-server-bound) RestClient. */
    FoundryLocationExtractionAdapter(RestClient restClient, String deploymentName) {
        this.restClient = restClient;
        this.chatCompletionsPath = "/openai/deployments/" + deploymentName + "/chat/completions?api-version=" + API_VERSION;
    }

    @Override
    public List<LocationCandidate> extractLocations(String text) {
        var restClient = this.restClient;
        if (restClient == null || text == null || text.isBlank()) {
            return List.of();
        }
        try {
            var content = callChatCompletion(restClient, truncate(text));
            return content == null ? List.of() : parseCandidates(content);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to extract locations via Foundry location extraction model", exception);
            return List.of();
        }
    }

    private @Nullable String callChatCompletion(RestClient restClient, String text) {
        var payload = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)),
                "temperature", 0);
        var response = restClient.post()
                .uri(this.chatCompletionsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(ChatCompletionResponse.class);
        var choices = response == null ? null : response.choices();
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        var message = choices.getFirst().message();
        return message == null ? null : message.content();
    }

    private static List<LocationCandidate> parseCandidates(String content) {
        List<LocationCandidateDto> dtos;
        try {
            dtos = OBJECT_MAPPER.readValue(content, OBJECT_MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, LocationCandidateDto.class));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Foundry location extraction model returned invalid JSON", exception);
            return List.of();
        }
        var candidates = new ArrayList<LocationCandidate>();
        for (var dto : dtos) {
            if (dto.rawMention() == null || dto.rawMention().isBlank()
                    || dto.canonicalName() == null || dto.canonicalName().isBlank()) {
                continue;
            }
            var confidence = dto.confidence() == null ? 0.0 : dto.confidence();
            if (confidence < MIN_CONFIDENCE) {
                continue;
            }
            candidates.add(new LocationCandidate(
                    dto.rawMention(), dto.canonicalName(), dto.province(), dto.county(),
                    dto.latitude(), dto.longitude(), confidence));
        }
        return List.copyOf(candidates);
    }

    private static String truncate(String text) {
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(@Nullable List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(@Nullable ChatMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(@Nullable String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocationCandidateDto(
            @Nullable String rawMention,
            @Nullable String canonicalName,
            @Nullable String province,
            @Nullable String county,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Double confidence) {
    }
}
