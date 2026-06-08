package com.example.funsejmapi;

import java.util.Optional;
import java.util.stream.IntStream;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

/**
 * Demo Durable Functions workflow for the fun_sejmapi module.
 */
public final class DemoDurableFunctions {

    /** Durable HTTP starter function name. */
    static final String HTTP_STARTER_FUNCTION_NAME = "SejmApiDemo_HttpStart";
    /** Durable orchestrator function name. */
    static final String ORCHESTRATOR_FUNCTION_NAME = "SejmApiDemo_Orchestrator";
    /** Durable activity function name. */
    static final String ACTIVITY_FUNCTION_NAME = "SejmApiDemo_Activity";

    /**
     * Starts a new demo orchestration instance and returns durable status URLs.
     *
     * @param request incoming HTTP request with optional workflow payload
     * @param durableContext durable client context used to schedule orchestration
     * @return HTTP 202 response with status endpoints
     */
    @FunctionName(HTTP_STARTER_FUNCTION_NAME)
    public HttpResponseMessage httpStart(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.POST },
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    final HttpRequestMessage<
                            Optional<DemoWorkflowRequest>> request,
            @DurableClientInput(name = "durableContext")
                    final DurableClientContext durableContext) {

        var normalizedRequest =
                DemoWorkflowRequest.normalize(request.getBody().orElse(null));
        var instanceId = durableContext.getClient()
                .scheduleNewOrchestrationInstance(
                        ORCHESTRATOR_FUNCTION_NAME,
                        normalizedRequest);

        return durableContext.createCheckStatusResponse(request, instanceId);
    }

    /**
     * Executes orchestration logic for the demo workflow.
     *
     * @param orchestrationContext durable orchestration context
     * @return workflow result returned from the activity
     */
    @FunctionName(ORCHESTRATOR_FUNCTION_NAME)
    public DemoWorkflowResult runOrchestrator(
            @DurableOrchestrationTrigger(name = "orchestrationContext")
                    final TaskOrchestrationContext orchestrationContext) {

        var normalizedRequest = DemoWorkflowRequest.normalizeForOrchestrator(
                orchestrationContext.getInput(DemoWorkflowRequest.class),
                orchestrationContext.getInstanceId());
        return orchestrationContext
                .callActivity(
                        ACTIVITY_FUNCTION_NAME,
                        normalizedRequest,
                        DemoWorkflowResult.class)
                .await();
    }

    /**
     * Generates a demo-only payload for local durable workflow execution.
     *
     * @param request normalized or raw workflow request
     * @param executionContext Azure Functions execution context
     * @return demo workflow result
     */
    @FunctionName(ACTIVITY_FUNCTION_NAME)
    public DemoWorkflowResult runDemoActivity(
            @DurableActivityTrigger(name = "request")
                    final DemoWorkflowRequest request,
            final ExecutionContext executionContext) {

        var normalizedRequest = DemoWorkflowRequest.normalize(request);
        var rows = IntStream.rangeClosed(1, normalizedRequest.sampleSize())
                .mapToObj(index -> "sample-row-" + index)
                .toList();

        return new DemoWorkflowResult(
                normalizedRequest.correlationId(),
                "demo-only",
                rows);
    }
}
