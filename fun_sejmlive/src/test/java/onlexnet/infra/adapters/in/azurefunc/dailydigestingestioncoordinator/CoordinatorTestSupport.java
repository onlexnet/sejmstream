package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import java.util.List;
import java.util.UUID;

import com.google.protobuf.StringValue;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.JacksonDataConverter;
import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.EntityBatchRequest;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.EntityBatchResult;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.OperationRequest;

import onlexnet.app.usecases.DailyDigestIngestionState;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.base.TaskEntityGateway;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRuntimeObservedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectRuntimeStatusDTO;

final class CoordinatorTestSupport {

    static final JacksonDataConverter CONVERTER = new JacksonDataConverter();
    static final EntityInstanceId ENTITY_ID = new EntityInstanceId(
            SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    private CoordinatorTestSupport() { }

    static JsonValidator validator() {
        var validator = new JsonValidator(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        validator.init();
        return validator;
    }

    static TaskEntity gateway() {
        return new TaskEntityGateway(List.of(new DailyDigestIngestionCoordinatorActor(validator())));
    }

    static EntityBatchResult run(String state, CollectCoordinatorDispatchCommandDTO... commands) throws Exception {
        return run(gateway(), state, commands);
    }

    static EntityBatchResult run(TaskEntity entity, String state, CollectCoordinatorDispatchCommandDTO... commands) throws Exception {
        var request = EntityBatchRequest.newBuilder()
                .setInstanceId(ENTITY_ID.toString()).setEntityState(StringValue.of(state));
        for (var command : commands) {
            request.addOperations(OperationRequest.newBuilder().setRequestId(UUID.randomUUID().toString())
                    .setOperation(DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName())
                    .setInput(StringValue.of(CONVERTER.serialize(command))));
        }
        return EntityBatchResult.parseFrom(EntityRunner.loadAndRun(request.build().toByteArray(), entity));
    }

    static String state(DailyDigestIngestionState lifecycle) {
        return CONVERTER.serialize(DailyDigestIngestionCoordinatorState.of(lifecycle));
    }

    static DailyDigestIngestionState lifecycle(EntityBatchResult result) {
        return CONVERTER.deserialize(result.getEntityState().getValue(), DailyDigestIngestionCoordinatorState.class).lifecycle();
    }

    static CollectCoordinatorRequestCollectCommandDTO request() {
        var request = new CollectCoordinatorRequestCollectCommandDTO();
        request.setSource("timer");
        return request;
    }

    static CollectCoordinatorRuntimeObservedCommandDTO observation(String instanceId, CollectRuntimeStatusDTO status) {
        var command = new CollectCoordinatorRuntimeObservedCommandDTO();
        command.setOrchestrationInstanceId(instanceId);
        command.setRuntimeStatus(status);
        return command;
    }
}
