package onlexnet.infra.adapters.in.azurefunc.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.microsoft.durabletask.DataConverter;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.JacksonDataConverter;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.SignalEntityOptions;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;
import com.microsoft.durabletask.TaskEntityState;
import com.restfb.FacebookClient;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.starters.Program;
import onlexnet.testsupport.AppTest;
import onlexnet.testsupport.PostgresIntegrationTestSupport;

@AppTest(classes = {Program.class, TaskEntityGatewayTest.LocalTestConfiguration.class})
class TaskEntityGatewayTest extends PostgresIntegrationTestSupport {

    private static final String LOCAL_ENTITY_NAME = "local-test-entity";
    private static final String BROKEN_ENTITY_NAME = "broken-test-entity";

    @MockitoBean
    FacebookClient facebookClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TaskEntityGateway taskEntityGateway;

    @Autowired
    @Qualifier("localDurableEntityComponent")
    private DurableEntityComponent localDurableEntityComponent;

    @Autowired
    @Qualifier("brokenDurableEntityComponent")
    private DurableEntityComponent brokenDurableEntityComponent;

    @Test
    void shouldDetectLocalDurableEntityComponentBean() {
        assertThat(applicationContext.getBeansOfType(DurableEntityComponent.class).values())
                .contains(localDurableEntityComponent, brokenDurableEntityComponent);
    }

    @Test
    void shouldRouteInvocationToLocalDurableEntityComponent() {
        var operation = createOperation(LOCAL_ENTITY_NAME, "ping");

        var result = taskEntityGateway.run(operation);

        assertThat(result).isEqualTo("local-result");
    }

    @Test
    void shouldPropagateBrokenBeanFailure() {
        var operation = createOperation(BROKEN_ENTITY_NAME, "break");

        assertThatThrownBy(() -> taskEntityGateway.run(operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken bean triggered");
    }

    private static TaskEntityOperation createOperation(String entityName, String operationName) {
        try {
            DataConverter dataConverter = new JacksonDataConverter();
            Constructor<TaskEntityState> stateConstructor = TaskEntityState.class
                    .getDeclaredConstructor(DataConverter.class, String.class);
            stateConstructor.setAccessible(true);
            TaskEntityState state = stateConstructor.newInstance(dataConverter, null);

            Constructor<TaskEntityOperation> operationConstructor = TaskEntityOperation.class
                    .getDeclaredConstructor(String.class, String.class, TaskEntityContext.class, TaskEntityState.class,
                            DataConverter.class);
            operationConstructor.setAccessible(true);
            return operationConstructor.newInstance(
                    operationName,
                    null,
                    new FixedTaskEntityContext(entityName),
                    state,
                    dataConverter);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create TaskEntityOperation test fixture", exception);
        }
    }

    private static final class FixedTaskEntityContext extends TaskEntityContext {

        private final EntityInstanceId entityInstanceId;

        private FixedTaskEntityContext(String entityName) {
            entityInstanceId = new EntityInstanceId(entityName, "singleton");
        }

        @Override
        public EntityInstanceId getId() {
            return entityInstanceId;
        }

        @Override
        public void signalEntity(EntityInstanceId entityId, String operationName, Object input,
                SignalEntityOptions options) {
            throw new UnsupportedOperationException("signalEntity is not used in this test context");
        }

        @Override
        public String startNewOrchestration(String orchestratorName, Object input,
                NewOrchestrationInstanceOptions options) {
            throw new UnsupportedOperationException("startNewOrchestration is not used in this test context");
        }
    }

    @TestConfiguration
    static class LocalTestConfiguration {

        @Bean("localDurableEntityComponent")
        DurableEntityComponent localDurableEntityComponent() {
            return new LocalDurableEntityComponent();
        }

        @Bean("brokenDurableEntityComponent")
        DurableEntityComponent brokenDurableEntityComponent() {
            return new BrokenDurableEntityComponent();
        }
    }

    private static final class LocalDurableEntityComponent implements DurableEntityComponent, LocalEntityContract {

        private static final DurableEntityOperationBinding<LocalEntityContract, String> PING_OPERATION =
                DurableEntityOperationBinding.of("ping", String.class, LocalEntityContract::ping);

        @Override
        public String entityName() {
            return LOCAL_ENTITY_NAME;
        }

        @Override
        public DurableEntityOperationBinding<LocalEntityContract, ?> resolveOperation(String requestedMethod) {
            if (PING_OPERATION.matches(requestedMethod)) {
                return PING_OPERATION;
            }
            throw new UnsupportedOperationException(
                    "Entity 'LocalDurableEntityComponent' does not support operation '" + requestedMethod + "'.");
        }

        @Override
        public Object runOperation(TaskEntityOperation operation) {
            return "local-result";
        }

        @Override
        public void ping(String ignoredPayload) {
            // No-op; operation binding is validated in gateway before execution.
        }
    }

    private static final class BrokenDurableEntityComponent implements DurableEntityComponent, BrokenEntityContract {

        private static final DurableEntityOperationBinding<BrokenEntityContract, String> BREAK_OPERATION =
                DurableEntityOperationBinding.of("break", String.class, BrokenEntityContract::breakNow);

        @Override
        public String entityName() {
            return BROKEN_ENTITY_NAME;
        }

        @Override
        public DurableEntityOperationBinding<BrokenEntityContract, ?> resolveOperation(String requestedMethod) {
            if (BREAK_OPERATION.matches(requestedMethod)) {
                return BREAK_OPERATION;
            }
            throw new UnsupportedOperationException(
                    "Entity 'BrokenDurableEntityComponent' does not support operation '" + requestedMethod + "'.");
        }

        @Override
        public Object runOperation(TaskEntityOperation operation) {
            throw new IllegalStateException("broken bean triggered");
        }

        @Override
        public void breakNow(String ignoredPayload) {
            throw new IllegalStateException("broken bean triggered");
        }
    }

    private interface LocalEntityContract extends DurableEntityContract {
        void ping(String payload);
    }

    private interface BrokenEntityContract extends DurableEntityContract {
        void breakNow(String payload);
    }
}
