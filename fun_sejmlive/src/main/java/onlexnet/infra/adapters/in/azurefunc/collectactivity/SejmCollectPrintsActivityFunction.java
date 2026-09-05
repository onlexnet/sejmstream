package onlexnet.infra.adapters.in.azurefunc.collectactivity;

import java.time.LocalDate;
import java.util.Map;

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
public final class SejmCollectPrintsActivityFunction {

    private final SejmCollectActivitySupport activitySupport;

    @FunctionName(SejmCollectFunctions.ACTIVITY_PRINTS)
    public CollectActivityResultWire collectPrints(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        this.activitySupport.validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = this.activitySupport.getCurrentTermNum();
            Log.info(execCtx, "Starting prints collection for term=" + termNum + ", date=" + date);
            var count = this.activitySupport.collectService().collectPrints(termNum, date);
            Log.info(execCtx, "Completed prints collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectPrints completed: {} items", count);
            return this.activitySupport.buildActivityResult(
                    count,
                    termNum,
                    date,
                    this.activitySupport.loadKeysByType(date, "PRINT"),
                    Map.of());
        } catch (Exception e) {
            log.error("Activity collectPrints failed", e);
            execCtx.getLogger().severe("Activity collectPrints failed: " + SejmCollectActivitySupport.buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect prints: " + SejmCollectActivitySupport.buildFailureMessage(e), e);
        }
    }
}