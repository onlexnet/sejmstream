package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

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

    private final SejmDailyDigestPersistence dailyDigestPersistence;

    public TermSnapshotCollectedEvent materialize(CollectOrchestratorEventV1DTO collectEvent) {
        var collectionDateNumber = requiredDateNumber(collectEvent.getCollectionDate(), "collectionDate");
        var collectionDate = JsonDateNumbers.fromYyyyMmDd(collectionDateNumber);

        return new TermSnapshotCollectedEvent(
                collectionDateNumber,
                requiredText(collectEvent.getSource(), "source"),
                requiredText(collectEvent.getOrchestrationInstanceId(), "orchestrationInstanceId"),
                loadInterpellationFingerprints(collectionDate),
                loadKeysByType(collectionDate, DATA_TYPE_WRITTEN_QUESTION),
                loadKeysByType(collectionDate, DATA_TYPE_PRINT),
                loadKeysByType(collectionDate, DATA_TYPE_BILL));
    }

    private Map<String, String> loadInterpellationFingerprints(LocalDate collectionDate) {
        var rows = this.dailyDigestPersistence.findByDateAndType(collectionDate, DATA_TYPE_INTERPELLATION);
        var fingerprintsByKey = new TreeMap<String, String>();
        for (var row : rows) {
            var key = extractStringColumn(row, "item_key");
            var json = extractJsonColumn(row, "item_json");
            fingerprintsByKey.put(key, sha256Hex(json));
        }
        return Map.copyOf(fingerprintsByKey);
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
}
