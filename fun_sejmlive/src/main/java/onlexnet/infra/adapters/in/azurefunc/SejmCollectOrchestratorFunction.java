package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectResult;

@Component
@RequiredArgsConstructor
public final class SejmCollectOrchestratorFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME)
    public CollectResult runOrchestrator(
            @DurableOrchestrationTrigger(name = "orchestrationContext") final TaskOrchestrationContext orchestrationContext) {
        return this.support.runOrchestrator(orchestrationContext);
    }
}
