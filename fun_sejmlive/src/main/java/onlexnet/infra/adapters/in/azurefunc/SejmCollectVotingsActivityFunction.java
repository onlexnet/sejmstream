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
public final class SejmCollectVotingsActivityFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ACTIVITY_VOTINGS)
    public CollectActivityResult collectVotings(
            @DurableActivityTrigger(name = "request") final CollectActivityRequest request,
            final ExecutionContext execCtx) {
        return this.support.collectVotings(request, execCtx);
    }
}
