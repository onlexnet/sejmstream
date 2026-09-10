package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;
import onlexnet.shared.Guards;
import onlexnet.shared.JsonDateNumbers;

/**
 * Builds a term snapshot event from collect-orchestrator v1 metadata and persisted digest rows.
 */
@Component
@RequiredArgsConstructor
public final class TermSnapshotCollectedEventMaterializer {

    private static final String DATA_TYPE_INTERPELLATION = "INTERPELLATION";
    private static final String DATA_TYPE_WRITTEN_QUESTION = "WRITTEN_QUESTION";
    private static final String DATA_TYPE_PRINT = "PRINT";
    private static final String DATA_TYPE_BILL = "BILL";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final SejmDailyDigestPersistence dailyDigestPersistence;

    public TermSnapshotCollectedEvent materialize(CollectOrchestratorEventV1DTO collectEvent) {
        var collectionDateNumber = requiredDateNumber(collectEvent.getCollectionDate(), "collectionDate");
        var collectionDate = JsonDateNumbers.fromYyyyMmDd(collectionDateNumber);
        var interpellationData = loadInterpellationData(collectionDate);

        return new TermSnapshotCollectedEvent(
                collectionDateNumber,
                requiredText(collectEvent.getSource(), "source"),
                requiredText(collectEvent.getOrchestrationInstanceId(), "orchestrationInstanceId"),
                interpellationData.fingerprints(),
                interpellationData.presentationByKey(),
                loadKeysByType(collectionDate, DATA_TYPE_WRITTEN_QUESTION),
                loadKeysByType(collectionDate, DATA_TYPE_PRINT),
                loadKeysByType(collectionDate, DATA_TYPE_BILL));
    }

    private InterpellationData loadInterpellationData(LocalDate collectionDate) {
        var rows = this.dailyDigestPersistence.findByDateAndType(collectionDate, DATA_TYPE_INTERPELLATION);
        var fingerprintsByKey = new TreeMap<String, String>();
        var presentationByKey = new TreeMap<String, TermSnapshotCollectedEvent.InterpellationPresentation>();
        for (var row : rows) {
            var key = extractStringColumn(row, "item_key");
            var json = extractJsonColumn(row, "item_json");
            fingerprintsByKey.put(key, sha256Hex(json));
            presentationByKey.put(key, extractInterpellationPresentation(row, key, json));
        }
        return new InterpellationData(Map.copyOf(fingerprintsByKey), Map.copyOf(presentationByKey));
    }

    private static TermSnapshotCollectedEvent.InterpellationPresentation extractInterpellationPresentation(
            Map<String, Object> row,
            String interpellationKey,
            String itemJson) {
        try {
            var itemNode = OBJECT_MAPPER.readTree(itemJson);
            var titleFromColumn = optionalStringColumn(row, "item_title");
            var title = firstNonBlank(titleFromColumn, optionalTextNode(itemNode, "title"));

            String webDescriptionUrl = null;
            JsonNode linksNode = itemNode.path("links");
            if (linksNode.isObject()) {
                webDescriptionUrl = optionalTextNode(linksNode, "webDescription");
            }

            return new TermSnapshotCollectedEvent.InterpellationPresentation(title, webDescriptionUrl);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Invalid interpellation item_json for key '" + interpellationKey + "'",
                    exception);
        }
    }

    private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static @Nullable String optionalTextNode(JsonNode node, String fieldName) {
        var field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        var text = field.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private List<String> loadKeysByType(LocalDate collectionDate, String dataType) {
        var rows = this.dailyDigestPersistence.findByDateAndType(collectionDate, dataType);
        var keys = new TreeSet<String>();
        for (var row : rows) {
            keys.add(extractStringColumn(row, "item_key"));
        }
        return List.copyOf(keys);
    }

    private static String extractStringColumn(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            throw new IllegalStateException("Missing required column '" + key + "' in digest row");
        }
        return String.valueOf(value);
    }

    private static @Nullable String optionalStringColumn(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            return null;
        }
        var text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static String extractJsonColumn(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            throw new IllegalStateException("Missing required column '" + key + "' in digest row");
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (!"org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return String.valueOf(value);
        }
        try {
            var getValueMethod = value.getClass().getMethod("getValue");
            var extracted = getValueMethod.invoke(value);
            return extracted == null ? "" : String.valueOf(extracted);
        } catch (ReflectiveOperationException exception) {
            return String.valueOf(value);
        }
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String requiredText(String value, String fieldName) {
        return Guards.requireNonEmpty(
                value,
                () -> new IllegalStateException("Collect event field " + fieldName + " must not be blank"));
    }

    private static int requiredDateNumber(Integer value, String fieldName) {
        var requiredValue = Guards.requireState(
                value,
                () -> "Collect event field " + fieldName + " must not be null");
        JsonDateNumbers.fromYyyyMmDd(requiredValue);
        return requiredValue;
    }

    private record InterpellationData(
            Map<String, String> fingerprints,
            Map<String, TermSnapshotCollectedEvent.InterpellationPresentation> presentationByKey) {
    }
}
