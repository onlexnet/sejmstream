package onlexnet.sejmapi;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import onlexnet.app.ports.out.SejmApiClient;

/**
 * Exposes a simple anonymous HTTP endpoint for Sejm API data.
 */
@Component
public final class SejmApiHttpFunctions {

    private final SejmApiClient sejmApiClient;

    public SejmApiHttpFunctions(final SejmApiClient sejmApiClient) {
        this.sejmApiClient = sejmApiClient;
    }

    @FunctionName("SejmApiDemo_SimpleData")
    public HttpResponseMessage simpleData(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "sejm-api/simple")
                    final HttpRequestMessage<Optional<String>> request) {
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(this.sejmApiClient.fetchTerms())
                .build();
    }
}
