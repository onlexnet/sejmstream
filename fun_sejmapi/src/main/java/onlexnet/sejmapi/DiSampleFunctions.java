package onlexnet.sejmapi;

import java.util.Optional;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Simple function demonstrating constructor-based dependency injection.
 */
public final class DiSampleFunctions {

    /** Constructor-injected dependency provided by FunctionInstanceInjector. */
    private final SimpleMessageService messageService;

    public DiSampleFunctions(final SimpleMessageService messageService) {
        this.messageService = messageService;
    }

    @FunctionName("SejmApiDemo_DiSample")
    public HttpResponseMessage diSample(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "di-sample")
                    final HttpRequestMessage<Optional<String>> request) {

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(this.messageService.buildMessage())
                .build();
    }
}
