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
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;

class CollectCoordinatorEntityTest {

    @Test
        void givenKnownOperationNames_whenResolving_thenReturnsExpectedContractBinding() {
                assertThat(CollectCoordinatorEntity.resolveContractOperation(
                                CollectCoordinatorContractOperations.REQUEST_COLLECT.methodName()))
                                .isEqualTo(CollectCoordinatorContractOperations.REQUEST_COLLECT);
                assertThat(CollectCoordinatorEntity.resolveContractOperation(
                                CollectCoordinatorContractOperations.COLLECT_COMPLETED.methodName()))
                                .isEqualTo(CollectCoordinatorContractOperations.COLLECT_COMPLETED);
                assertThat(CollectCoordinatorEntity.resolveContractOperation(
                                CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()))
                                .isEqualTo(CollectCoordinatorContractOperations.COLLECT_FAILED);
                assertThat(CollectCoordinatorEntity.resolveContractOperation(
                                CollectCoordinatorContractOperations.FORCE_START_NEXT.methodName()))
                                .isEqualTo(CollectCoordinatorContractOperations.FORCE_START_NEXT);
    }

    @Test
    void givenMixedCaseOperationName_whenResolving_thenMatchesCaseInsensitively() {
                var operation = CollectCoordinatorEntity.resolveContractOperation("CoLlEcTcOmPlEtEd");

                assertThat(operation).isEqualTo(CollectCoordinatorContractOperations.COLLECT_COMPLETED);
    }

    @Test
    void givenUnknownOperationName_whenResolving_thenThrowsWithEntityNameAndOperation() {
                assertThatThrownBy(() -> CollectCoordinatorEntity.resolveContractOperation("unknownMethod"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CollectCoordinatorEntity")
                .hasMessageContaining("unknownMethod");
    }

        @Test
        void givenCoordinatorContractMethods_whenChecked_thenAllAreInvokableByOperationBindings() {
                var contractMethods = Set.of(CollectCoordinatorContractV1.class.getMethods()).stream()
                                .map(Method::getName)
                                .collect(Collectors.toSet());

                var boundOperationMethods = CollectCoordinatorContractOperations.BUSINESS_OPERATIONS.stream()
                                .map(DurableEntityOperationBinding::methodName)
                                .collect(Collectors.toSet());

                assertThat(boundOperationMethods).containsExactlyInAnyOrderElementsOf(contractMethods);
        }

        @Test
        void givenIncomingDurableRequestForRequestCollect_whenInvoked_thenCallsContractMethod() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                when(operation.getInput(String.class)).thenReturn("timer");

                CollectCoordinatorContractOperations.REQUEST_COLLECT.invoke(target, operation);

                verify(target).requestCollect("timer");
        }

        @Test
        void givenIncomingDurableRequestForCollectCompleted_whenInvoked_thenCallsContractMethod() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var completion = new CollectCompletion();
                completion.setOrchestrationInstanceId("instance-1");
                when(operation.getInput(CollectCompletion.class)).thenReturn(completion);

                CollectCoordinatorContractOperations.COLLECT_COMPLETED.invoke(target, operation);

                verify(target).collectCompleted(completion);
        }

        @Test
        void givenIncomingDurableRequestForCollectFailed_whenInvoked_thenCallsContractMethod() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                var failure = new CollectFailure();
                failure.setOrchestrationInstanceId("instance-1");
                failure.setMessage("boom");
                when(operation.getInput(CollectFailure.class)).thenReturn(failure);

                CollectCoordinatorContractOperations.COLLECT_FAILED.invoke(target, operation);

                verify(target).collectFailed(failure);
        }

        @Test
        void givenIncomingDurableRequestForForceStartNext_whenInvoked_thenCallsContractMethod() {
                var target = mock(CollectCoordinatorContractV1.class);
                var operation = mock(TaskEntityOperation.class);
                when(operation.getInput(String.class)).thenReturn("manual-recovery");

                CollectCoordinatorContractOperations.FORCE_START_NEXT.invoke(target, operation);

                verify(target).forceStartNext("manual-recovery");
        }

    @Test
    void givenRunningStateAndTimeoutFailure_whenCollectFailed_thenSchedulesNewRunOneHourLater() {
        var jsonValidator = mock(JsonValidator.class);
        when(jsonValidator.validateReceived(eq(JsonValidator.COLLECT_FAILURE), any(CollectFailure.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(jsonValidator.validateToSend(eq(JsonValidator.COLLECT_ORCHESTRATION_INPUT), any(CollectOrchestrationInput.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var entity = new CollectCoordinatorEntity(jsonValidator);
        var operation = mock(TaskEntityOperation.class);
        var state = mock(TaskEntityState.class);
        var context = mock(TaskEntityContext.class);

        var persistedState = new Some();
        persistedState.setRunning(true);
        var failure = new CollectFailure();
        failure.setOrchestrationInstanceId("collect-instance-1");
        failure.setMessage("io.netty.handler.timeout.ReadTimeoutException");

        when(operation.getName()).thenReturn(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName());
        when(operation.getContext()).thenReturn(context);
        when(operation.getState()).thenReturn(state);
        when(state.getState(Some.class)).thenReturn(persistedState);
        when(operation.getInput(CollectFailure.class)).thenReturn(failure);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));

        var inputCaptor = ArgumentCaptor.forClass(CollectOrchestrationInput.class);
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
