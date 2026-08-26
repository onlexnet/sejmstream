package onlexnet.infra.adapters.in.facebook;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import lombok.RequiredArgsConstructor;

/**
 * HTTP trigger for manually publishing today's Sejm digest to Facebook.
 */
@Component
@RequiredArgsConstructor
public final class FacebookPublishingHttpFunction {

    private final FacebookPublishingFunctionSupport support;

    @FunctionName(FacebookPublishingFunctions.HTTP_FUNCTION_NAME)
    public HttpResponseMessage publishDailyDigestHttp(
            @HttpTrigger(name = "request", methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = FacebookPublishingFunctions.HTTP_FUNCTION_ROUTE)
            final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext execCtx) {
        return this.support.publishDailyDigestHttp(request, execCtx);
    }
}
