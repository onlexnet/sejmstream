package onlexnet.infra.adapters.in.azurefunc.collectcoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;
import com.microsoft.durabletask.TaskEntityState;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletionDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailureDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;

class CollectCoordinatorEntityTest {

    @Test
        void shouldResolveDispatchOperationName() {
                assertThat(CollectCoordinatorEntity.resolveContractOperation(
                                CollectCoordinatorContractOperations.DISPATCH.methodName()))
                                .isEqualTo(CollectCoordinatorContractOperations.DISPATCH);
    }

    @Test
        void shouldResolveDispatchOperationNameCaseInsensitively() {
                var operation = CollectCoordinatorEntity.resolveContractOperation("DiSpAtCh");

                assertThat(operation).isEqualTo(CollectCoordinatorContractOperations.DISPATCH);
    }

    @Test
        void shouldThrowForUnknownOperationName() {
                assertThatThrownBy(() -> CollectCoordinatorEntity.resolveContractOperation("unknownMethod"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CollectCoordinatorEntity")
                .hasMessageContaining("unknownMethod");
    }

        @Test
        void shouldExposeOnlyDispatchAsBusinessOperationBinding() {
                var contractMethods = Set.of(CollectCoordinatorContractV1.class.getMethods()).stream()
                                .map(Method::getName)
                                .collect(Collectors.toSet());

                var boundOperationMethods = CollectCoordinatorContractOperations.BUSINESS_OPERATIONS.stream()
                                .map(DurableEntityOperationBinding::methodName)
                                .collect(Collectors.toSet());

                assertThat(boundOperationMethods)
                                .containsExactly(CollectCoordinatorContractOperations.DISPATCH.methodName());
                assertThat(boundOperationMethods).containsExactlyInAnyOrderElementsOf(contractMethods);
        }

        @Test
        void shouldInvokeDispatchWithRequestCollectCommandPayload() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var command = new CollectCoordinatorRequestCollectCommandDTO();
                command.setSource("timer");
                when(operation.getInput(CollectCoordinatorDispatchCommandDTO.class)).thenReturn(command);

                CollectCoordinatorContractOperations.DISPATCH.invoke(target, operation);

                verify(target).dispatch(command);
        }

        @Test
        void shouldInvokeDispatchWithCollectCompletedCommandPayload() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var completion = new CollectCompletionDTO();
                completion.setOrchestrationInstanceId("instance-1");
                var command = new CollectCoordinatorCollectCompletedCommandDTO();
                command.setCompletion(completion);
                when(operation.getInput(CollectCoordinatorDispatchCommandDTO.class)).thenReturn(command);

                CollectCoordinatorContractOperations.DISPATCH.invoke(target, operation);

                verify(target).dispatch(command);
        }

        @Test
        void shouldInvokeDispatchWithCollectFailedCommandPayload() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var failure = new CollectFailureDTO();
                failure.setOrchestrationInstanceId("instance-1");
                failure.setMessage("boom");
                var command = new CollectCoordinatorCollectFailedCommandDTO();
                command.setFailure(failure);
                when(operation.getInput(CollectCoordinatorDispatchCommandDTO.class)).thenReturn(command);

                CollectCoordinatorContractOperations.DISPATCH.invoke(target, operation);

                verify(target).dispatch(command);
        }

        @Test
        void shouldInvokeDispatchWithForceStartNextCommandPayload() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var command = new CollectCoordinatorForceStartNextCommandDTO();
                command.setSource("manual-recovery");
                when(operation.getInput(CollectCoordinatorDispatchCommandDTO.class)).thenReturn(command);

                CollectCoordinatorContractOperations.DISPATCH.invoke(target, operation);

                verify(target).dispatch(command);
        }

    @Test
    void givenRunningStateAndTimeoutFailure_whenCollectFailed_thenSchedulesNewRunOneHourLater() {
        var jsonValidator = mock(JsonValidator.class);
        when(jsonValidator.validateReceived(eq(JsonValidator.COLLECT_FAILURE), any(CollectFailureDTO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(jsonValidator.validateToSend(eq(JsonValidator.COLLECT_ORCHESTRATION_INPUT), any(CollectOrchestrationInputDTO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var entity = new CollectCoordinatorEntity(jsonValidator);
        var operation = mock(TaskEntityOperation.class);
        var state = mock(TaskEntityState.class);
        var context = mock(TaskEntityContext.class);

        var persistedState = new Some();
        persistedState.setRunning(true);
        var failure = new CollectFailureDTO();
        failure.setOrchestrationInstanceId("collect-instance-1");
        failure.setMessage("io.netty.handler.timeout.ReadTimeoutException");
        var command = new CollectCoordinatorCollectFailedCommandDTO();
        command.setFailure(failure);

        when(operation.getName()).thenReturn(CollectCoordinatorContractOperations.DISPATCH.methodName());
        when(operation.getContext()).thenReturn(context);
        when(operation.getState()).thenReturn(state);
        when(state.getState(Some.class)).thenReturn(persistedState);
        when(operation.getInput(CollectCoordinatorDispatchCommandDTO.class)).thenReturn(command);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));

        var inputCaptor = ArgumentCaptor.forClass(CollectOrchestrationInputDTO.class);
        var optionsCaptor = ArgumentCaptor.forClass(NewOrchestrationInstanceOptions.class);
        var before = Instant.now();

        entity.run(operation);

        var after = Instant.now();
        verify(context).startNewOrchestration(
                eq(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME),
                inputCaptor.capture(),
                optionsCaptor.capture());

        var capturedInput = inputCaptor.getValue();
        assertThat(capturedInput.getCoordinatorEntityId())
                .isEqualTo(new EntityInstanceId(
                        SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                        SejmCollectFunctions.COORDINATOR_ENTITY_KEY).toString());
        assertThat(capturedInput.getSource()).isEqualTo("timeout-retry");

        var startTime = optionsCaptor.getValue().getStartTime();
        assertThat(startTime).isNotNull();
        assertThat(startTime)
                .isAfterOrEqualTo(before.plus(Duration.ofHours(1)).minusSeconds(5))
                .isBeforeOrEqualTo(after.plus(Duration.ofHours(1)).plusSeconds(5));
    }
}
