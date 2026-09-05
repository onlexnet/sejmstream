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
public final class SejmCollectBillsActivityFunction {

    private final SejmCollectActivitySupport activitySupport;

    @FunctionName(SejmCollectFunctions.ACTIVITY_BILLS)
    public CollectActivityResultWire collectBills(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        this.activitySupport.validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = this.activitySupport.getCurrentTermNum();
            Log.info(execCtx, "Starting bills collection for term=" + termNum + ", date=" + date);
            var count = this.activitySupport.collectService().collectBills(termNum, date);
            Log.info(execCtx, "Completed bills collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectBills completed: {} items", count);
            return this.activitySupport.buildActivityResult(
                    count,
                    termNum,
                    date,
                    this.activitySupport.loadKeysByType(date, "BILL"),
                    Map.of());
        } catch (Exception e) {
            var failure = SejmCollectActivitySupport.buildFailureMessage(e);
            log.warn("Activity collectBills failed, continuing with partial result: {}", failure, e);
            execCtx.getLogger().warning("Activity collectBills failed, continuing with count=0: " + failure);
            return this.activitySupport.buildActivityResult(
                    0,
                    this.activitySupport.getCurrentTermNum(),
                    LocalDate.now(),
                    List.of(),
                    Map.of());
        }
    }
}