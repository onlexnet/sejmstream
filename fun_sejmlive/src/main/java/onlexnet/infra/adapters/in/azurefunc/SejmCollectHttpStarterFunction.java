package onlexnet.infra.adapters.in.azurefunc;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public final class SejmCollectHttpStarterFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.HTTP_STARTER_FUNCTION_NAME)
    public HttpResponseMessage httpStart(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.POST
            }, authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext clientCtx,
            ExecutionContext execCtx) {
        return this.support.httpStart(request, clientCtx, execCtx);
    }
}
