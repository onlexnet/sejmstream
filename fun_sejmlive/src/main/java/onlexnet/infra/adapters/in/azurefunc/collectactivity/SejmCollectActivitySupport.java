package onlexnet.infra.adapters.in.azurefunc.collectactivity;

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
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;
import onlexnet.shared.Guards;

/**
 * Shared helpers for collect activity entrypoints.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public final class SejmCollectActivitySupport {

    private final SejmCollectOperations collectService;
    private final SejmApiClient sejmApiClient;
    private final SejmDailyDigestPersistence dailyDigestPersistence;
    private final JsonValidator jsonValidator;
    private CachedTerm cachedTermNum = CachedTerm.NONE;

    private sealed interface CachedTerm permits CachedTerm.None, CachedTerm.Resolved {
        enum None implements CachedTerm {
            NONE
        }

        record Resolved(int num) implements CachedTerm {
        }

        CachedTerm NONE = None.NONE;
    }

    SejmCollectOperations collectService() {
        return this.collectService;
    }

    void validateActivityRequest(CollectActivityRequest request) {
        var normalizedRequest = request == null ? new CollectActivityRequest() : request;
        this.jsonValidator.validateReceived(JsonValidator.COLLECT_ACTIVITY_REQUEST, normalizedRequest);
    }

    int getCurrentTermNum() {
        if (this.cachedTermNum instanceof CachedTerm.Resolved resolved) {
            return resolved.num();
        }
        var terms = Guards.requireNonEmpty(
                this.sejmApiClient.fetchTerms(),
                () -> new IllegalStateException("No Sejm terms found"));
        var termNum = terms.stream()
                .filter(t -> t != null && t.current())
                .mapToInt(SejmApiClient.SejmTerm::num)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current Sejm term found among " + terms.size() + " terms"));
        this.cachedTermNum = new CachedTerm.Resolved(termNum);
        log.debug("Current Sejm term: {}", termNum);
        return termNum;
    }

    CollectActivityResultWire buildActivityResult(
            int count,
            int termNum,
            LocalDate date,
            List<String> itemKeys,
            Map<String, String> interpellationFingerprints) {
        var result = new CollectActivityResult();
        result.setCount(count);
        result.setTermNum(termNum);
        result.setCollectionDate(date);
        result.setItemKeys(List.copyOf(itemKeys));
        result.setInterpellationFingerprints(Map.copyOf(interpellationFingerprints));
        return CollectActivityResultWire.from(
                this.jsonValidator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, result));
    }

    Map<String, String> loadInterpellationFingerprints(LocalDate date) {
        var rows = this.dailyDigestPersistence.findByDateAndType(date, "INTERPELLATION");
        var byKey = new TreeMap<String, String>();
        for (var row : rows) {
            var key = extractStringColumn(row, "item_key");
            var json = extractJsonColumn(row, "item_json");
            byKey.put(key, sha256Hex(json));
        }
        return Map.copyOf(byKey);
    }

    List<String> loadKeysByType(LocalDate date, String dataType) {
        var rows = this.dailyDigestPersistence.findByDateAndType(date, dataType);
        var keys = new TreeSet<String>();
        for (var row : rows) {
            keys.add(extractStringColumn(row, "item_key"));
        }
        return List.copyOf(keys);
    }

    static String buildFailureMessage(Exception exception) {
        var cause = exception.getCause();
        if (cause == null) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        var causeMessage = cause.getMessage() == null ? "(no message)" : cause.getMessage();
        return cause.getClass().getSimpleName() + ": " + causeMessage;
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
}