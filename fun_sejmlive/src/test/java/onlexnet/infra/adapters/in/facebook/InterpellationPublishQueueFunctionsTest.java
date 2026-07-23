package onlexnet.infra.adapters.in.facebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;

import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishOutcome;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishUseCase;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishStatePort;

class InterpellationPublishQueueFunctionsTest {

    @Test
    void givenQueueFunction_whenInspectingTrigger_thenQueueAndConnectionAreConfigured() throws NoSuchMethodException {
        var method = InterpellationPublishQueueFunctions.class.getDeclaredMethod(
                "process",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(QueueTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_InterpellationPublishFromQueue");
        assertThat(trigger).isNotNull();
        assertThat(trigger.queueName()).isEqualTo("%INTERPELLATION_PUBLISH_QUEUE_NAME%");
        assertThat(trigger.connection()).isEqualTo("DomainStorage");
    }

    @Test
    void givenValidQueueMessage_whenProcessing_thenDelegatesToUseCase() throws Exception {
        var useCase = mock(ProcessInterpellationPublishUseCase.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        when(useCase.process(any())).thenReturn(
                new ProcessInterpellationPublishOutcome.Published("term-10-interpellation-42", 10, 42));
        var functions = new InterpellationPublishQueueFunctions(
            useCase,
            new ObjectMapper().findAndRegisterModules(),
            queuePort,
            statePort);

        var message = """
                {
                  "domainMessageId": "term-10-interpellation-42",
                  "termNum": 10,
                  "interpellationNum": 42,
                  "title": "Interpelacja testowa",
                  "recipients": ["Minister Finansow"],
                  "sentDate": "2026-07-01",
                  "attempt": 1,
                  "firstQueuedAt": "2026-07-01T10:15:30Z",
                  "lastError": null
                }
                """;

        functions.process(message, new FakeExecutionContext());

        verify(useCase).process(any());
    verify(queuePort, never()).enqueueDeadLetter(any());
    verify(statePort, never()).markDeadLetter(any(), any());
    }

    @Test
    void givenMalformedJson_whenProcessing_thenDeadLettersAndPersistsFailureState() {
    var useCase = mock(ProcessInterpellationPublishUseCase.class);
    var queuePort = mock(InterpellationPublishQueuePort.class);
    var statePort = mock(InterpellationPublishStatePort.class);
    var functions = new InterpellationPublishQueueFunctions(
        useCase,
        new ObjectMapper().findAndRegisterModules(),
        queuePort,
        statePort);

    functions.process("{not-json", new FakeExecutionContext());

    verify(useCase, never()).process(any());
    verify(queuePort).enqueueDeadLetter(any());
    verify(statePort).markDeadLetter(any(), any());
    }

    @Test
    void givenInvalidAttemptInPayload_whenProcessing_thenDeadLettersAndPersistsFailureState() {
    var useCase = mock(ProcessInterpellationPublishUseCase.class);
    var queuePort = mock(InterpellationPublishQueuePort.class);
    var statePort = mock(InterpellationPublishStatePort.class);
    var functions = new InterpellationPublishQueueFunctions(
        useCase,
        new ObjectMapper().findAndRegisterModules(),
        queuePort,
        statePort);
    var message = """
        {
          "domainMessageId": "term-10-interpellation-42",
          "termNum": 10,
          "interpellationNum": 42,
          "title": "Interpelacja testowa",
          "recipients": ["Minister Finansow"],
          "sentDate": "2026-07-01",
          "attempt": 0,
          "firstQueuedAt": "2026-07-01T10:15:30Z",
          "lastError": null
        }
        """;

    functions.process(message, new FakeExecutionContext());

    verify(useCase, never()).process(any());
    verify(queuePort).enqueueDeadLetter(any());
    verify(statePort).markDeadLetter(any(), any());
    }

    @Test
    void givenMissingRequiredFieldInPayload_whenProcessing_thenDeadLettersAndPersistsFailureState() {
        var useCase = mock(ProcessInterpellationPublishUseCase.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var functions = new InterpellationPublishQueueFunctions(
                useCase,
                new ObjectMapper().findAndRegisterModules(),
                queuePort,
                statePort);
        var message = """
                {
                  "domainMessageId": "term-10-interpellation-42",
                  "termNum": 10,
                  "interpellationNum": 42,
                  "recipients": ["Minister Finansow"],
                  "sentDate": "2026-07-01",
                  "attempt": 1,
                  "firstQueuedAt": "2026-07-01T10:15:30Z",
                  "lastError": null
                }
                """;

        functions.process(message, new FakeExecutionContext());

        verify(useCase, never()).process(any());
        verify(queuePort).enqueueDeadLetter(any());
        verify(statePort).markDeadLetter(any(), any());
    }

        @Test
        void givenOperationalProcessingFailure_whenProcessing_thenBubblesForQueueRetryWithoutMalformedDeadLetter() {
        var useCase = mock(ProcessInterpellationPublishUseCase.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        when(useCase.process(any())).thenThrow(new IllegalStateException("db outage"));
        var functions = new InterpellationPublishQueueFunctions(
            useCase,
            new ObjectMapper().findAndRegisterModules(),
            queuePort,
            statePort);
        var message = """
            {
              "domainMessageId": "term-10-interpellation-42",
              "termNum": 10,
              "interpellationNum": 42,
              "title": "Interpelacja testowa",
              "recipients": ["Minister Finansow"],
              "sentDate": "2026-07-01",
              "attempt": 1,
              "firstQueuedAt": "2026-07-01T10:15:30Z",
              "lastError": null
            }
            """;

        assertThatThrownBy(() -> functions.process(message, new FakeExecutionContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db outage");
        verify(queuePort, never()).enqueueDeadLetter(any());
        verify(statePort, never()).markDeadLetter(any(), any());
        }

    private static final class FakeExecutionContext implements ExecutionContext {

        @Override
        public Logger getLogger() {
            return Logger.getLogger(FakeExecutionContext.class.getName());
        }

        @Override
        public String getInvocationId() {
            return "invocation-id";
        }

        @Override
        public String getFunctionName() {
            return "Fun_InterpellationPublishFromQueue";
        }
    }
}
