package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;

@Component
@RequiredArgsConstructor
public final class SejmCollectInterpellationsActivityFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS)
    public CollectActivityResultWire collectInterpellations(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        return this.support.collectInterpellations(request, execCtx);
    }
}
