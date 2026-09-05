package onlexnet.infra.adapters.in.azurefunc.collectactivity;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;

@Component
@Slf4j
@RequiredArgsConstructor
public final class SejmCollectInterpellationsActivityFunction {

    private final SejmCollectActivitySupport activitySupport;

    @FunctionName(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS)
    public CollectActivityResultWire collectInterpellations(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        this.activitySupport.validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = this.activitySupport.getCurrentTermNum();
            Log.info(execCtx, "Starting interpellations collection for term=" + termNum + ", date=" + date);
            var count = this.activitySupport.collectService().collectInterpellations(termNum, date);
            Log.info(execCtx, "Completed interpellations collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectInterpellations completed: {} items", count);
            var interpellationFingerprints = this.activitySupport.loadInterpellationFingerprints(date);
            return this.activitySupport.buildActivityResult(
                    count,
                    termNum,
                    date,
                    List.copyOf(new TreeSet<>(interpellationFingerprints.keySet())),
                    interpellationFingerprints);
        } catch (Exception e) {
            log.error("Activity collectInterpellations failed", e);
            execCtx.getLogger().severe(
                    "Activity collectInterpellations failed: " + SejmCollectActivitySupport.buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect interpellations: " + SejmCollectActivitySupport.buildFailureMessage(e),
                    e);
        }
    }
}