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
public final class SejmCollectBillsActivityFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ACTIVITY_BILLS)
    public CollectActivityResult collectBills(
            @DurableActivityTrigger(name = "request") final CollectActivityRequest request,
            final ExecutionContext execCtx) {
        return this.support.collectBills(request, execCtx);
    }
}
