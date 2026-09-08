package onlexnet.infra.adapters.in.azurefunc.collectorchestrator;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResultDTO;

/**
 * JSON-safe result exchanged between a Durable activity and its orchestrator.
 *
 * <p>All temporal values use numeric JSON representation because the Azure Functions Java worker serializes
 * activity return values with Gson while Durable Task deserializes them with Jackson.
 */
public record CollectActivityResultWire(
        Integer count,
        @Nullable Integer termNum,
    @Nullable Integer collectionDate,
        @Nullable List<String> itemKeys,
        @Nullable Map<String, String> interpellationFingerprints) {

    public static CollectActivityResultWire from(CollectActivityResultDTO result) {
        return new CollectActivityResultWire(
                result.getCount(),
                result.getTermNum(),
                result.getCollectionDate(),
                result.getItemKeys(),
                result.getInterpellationFingerprints());
    }

    public CollectActivityResultDTO toSchemaModel() {
        var result = new CollectActivityResultDTO();
        result.setCount(this.count);
        result.setTermNum(this.termNum);
        result.setCollectionDate(this.collectionDate);
        result.setItemKeys(this.itemKeys);
        result.setInterpellationFingerprints(this.interpellationFingerprints);
        return result;
    }
}