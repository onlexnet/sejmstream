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
public final class SejmCollectQuestionsActivityFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.ACTIVITY_QUESTIONS)
    public CollectActivityResult collectQuestions(
            @DurableActivityTrigger(name = "request") CollectActivityRequest request,
            ExecutionContext execCtx) {
        return this.support.collectQuestions(request, execCtx);
    }
}
