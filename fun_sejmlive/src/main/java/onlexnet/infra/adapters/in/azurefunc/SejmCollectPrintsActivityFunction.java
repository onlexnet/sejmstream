package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

@Component
@RequiredArgsConstructor
public final class SejmCollectPrintsActivityFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ACTIVITY_PRINTS)
    public CollectActivityResult collectPrints(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        return this.support.collectPrints(request, execCtx);
    }
}
