package onlexnet.infra.adapters.in.azurefunc;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

/**
 * JSON-safe result exchanged between a Durable activity and its orchestrator.
 *
 * <p>All temporal values use their ISO-8601 string representation because the Azure Functions Java worker serializes
 * activity return values with Gson while Durable Task deserializes them with Jackson.
 */
public record CollectActivityResultWire(
        Integer count,
        @Nullable Integer termNum,
        @Nullable String collectionDate,
        @Nullable List<String> itemKeys,
        @Nullable Map<String, String> interpellationFingerprints) {

    static CollectActivityResultWire from(CollectActivityResult result) {
        var date = result.getCollectionDate();
        return new CollectActivityResultWire(
                result.getCount(),
                result.getTermNum(),
                date == null ? null : date.toString(),
                result.getItemKeys(),
                result.getInterpellationFingerprints());
    }

    CollectActivityResult toSchemaModel() {
        var result = new CollectActivityResult();
        result.setCount(this.count);
        result.setTermNum(this.termNum);
        result.setCollectionDate(parseCollectionDate(this.collectionDate));
        result.setItemKeys(this.itemKeys);
        result.setInterpellationFingerprints(this.interpellationFingerprints);
        return result;
    }

    private static @Nullable LocalDate parseCollectionDate(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Collect activity result has invalid ISO collectionDate: " + value, e);
        }
    }
}