package onlexnet.sejmapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Exposes a simple OpenAPI document and Swagger UI for this demo Function App.
 */
public final class ApiDocumentationFunctions {

    /** Swagger UI function name. */
    static final String SWAGGER_UI_FUNCTION_NAME = "SejmApiDemo_SwaggerUi";
    /** OpenAPI specification function name. */
    static final String OPENAPI_FUNCTION_NAME = "SejmApiDemo_OpenApi";
    /** OpenAPI resource path inside classpath. */
    static final String OPENAPI_RESOURCE_PATH = "openapi/openapi.yaml";
    /** Swagger UI bootstrap page path inside classpath. */
    static final String SWAGGER_UI_RESOURCE_PATH = "swagger/index.html";

    /**
     * Serves OpenAPI v3 description for this module.
     *
     * @param request incoming HTTP request
     * @return OpenAPI YAML document
     */
    @FunctionName(OPENAPI_FUNCTION_NAME)
    public HttpResponseMessage openApi(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "openapi.yaml")
                    final HttpRequestMessage<Optional<String>> request) {
        return this.loadTextResourceResponse(
                request,
                OPENAPI_RESOURCE_PATH,
                "application/yaml; charset=utf-8");
    }

    /**
     * Serves Swagger UI configured to read this app's OpenAPI YAML endpoint.
     *
     * @param request incoming HTTP request
     * @return Swagger UI HTML page
     */
    @FunctionName(SWAGGER_UI_FUNCTION_NAME)
    public HttpResponseMessage swaggerUi(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "swagger")
                    final HttpRequestMessage<Optional<String>> request) {
        return this.loadTextResourceResponse(
                request,
                SWAGGER_UI_RESOURCE_PATH,
                "text/html; charset=utf-8");
    }

    private HttpResponseMessage loadTextResourceResponse(
            final HttpRequestMessage<Optional<String>> request,
            final String resourcePath,
            final String contentType) {
        try {
            var body = this.loadTextResource(resourcePath);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", contentType)
                    .body(body)
                    .build();
        } catch (IOException ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to load resource: " + resourcePath)
                    .build();
        }
    }

    private String loadTextResource(final String resourcePath)
            throws IOException {
        try (InputStream input = this.getClass().getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}