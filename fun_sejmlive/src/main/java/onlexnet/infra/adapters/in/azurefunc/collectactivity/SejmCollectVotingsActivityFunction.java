package onlexnet.infra.adapters.in.azurefunc.collectactivity;

import java.time.LocalDate;
import java.util.List;
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
public final class SejmCollectVotingsActivityFunction {

    private final SejmCollectActivitySupport activitySupport;

    @FunctionName(SejmCollectFunctions.ACTIVITY_VOTINGS)
    public CollectActivityResultWire collectVotings(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        this.activitySupport.validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = this.activitySupport.getCurrentTermNum();
            Log.info(execCtx, "Starting votings collection for term=" + termNum + ", date=" + date);
            var count = this.activitySupport.collectService().collectVotings(termNum, date);
            Log.info(execCtx, "Completed votings collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectVotings completed: {} items", count);
            return this.activitySupport.buildActivityResult(count, termNum, date, List.of(), Map.of());
        } catch (Exception e) {
            log.error("Activity collectVotings failed", e);
            execCtx.getLogger().severe("Activity collectVotings failed: " + SejmCollectActivitySupport.buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect votings: " + SejmCollectActivitySupport.buildFailureMessage(e), e);
        }
    }
}