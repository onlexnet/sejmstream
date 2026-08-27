package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import com.restfb.FacebookClient;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractV1;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectBillsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectCommitteesActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectCoordinatorEntityFunctions;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectHttpStarterFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectInterpellationsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectOrchestratorFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectPrintsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectQuestionsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectTimerFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectVotingsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot.SejmTermSnapshotEntityFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.out.SejmCollectService;
import onlexnet.testsupport.AppTest;

@AppTest
class SejmCollectFunctionContractsTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
        private static final String TERM_SNAPSHOT_ENTITY_NAME = "SejmTermSnapshot";

    private static final List<String> COLLECT_COORDINATOR_OPERATIONS = CollectCoordinatorContractOperations.BUSINESS_OPERATIONS
            .stream()
            .map(onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding::methodName)
            .toList();

    @MockitoBean
    FacebookClient facebookClient;
    
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SejmCollectService sejmCollectService;

    @Autowired
    private SejmApiClient sejmApiClient;

    @Test
    void givenSpringBootContext_whenResolvingCollectFunctions_thenRequiredDependenciesAreInjected() {
        assertThat(this.applicationContext).isNotNull();
        assertThat(this.sejmCollectService).isNotNull();
        assertThat(this.sejmApiClient).isNotNull();
        assertThat(this.applicationContext.getBean(SejmCollectService.class)).isSameAs(this.sejmCollectService);
        assertThat(this.applicationContext.getBean(SejmApiClient.class)).isSameAs(this.sejmApiClient);
    }

    @Test
    void givenTimerFunction_whenCheckingTriggerContract_thenRunsDailyAt1630() throws NoSuchMethodException {
        var method = SejmCollectTimerFunction.class.getDeclaredMethod(
                "runTimer",
                String.class,
                DurableClientContext.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.TIMER_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 0 * * * *");
    }

    @Test
    void givenHttpStarterFunction_whenCheckingTriggerContract_thenPostAndFunctionAuthAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectHttpStarterFunction.class.getDeclaredMethod(
                "httpStart",
                HttpRequestMessage.class,
                DurableClientContext.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.HTTP_STARTER_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.POST);
        assertThat(trigger.authLevel()).isEqualTo(AuthorizationLevel.FUNCTION);
    }

    @Test
    void givenOrchestratorFunction_whenCheckingTriggerContract_thenFunctionAndOrchestrationTriggerAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectOrchestratorFunction.class.getDeclaredMethod(
                "runOrchestrator",
                com.microsoft.durabletask.TaskOrchestrationContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableOrchestrationTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("orchestrationContext");
    }

    @Test
    void givenCoordinatorEntityFunction_whenCheckingTriggerContract_thenFunctionAndEntityTriggerAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectCoordinatorEntityFunctions.class.getDeclaredMethod(
                "runCollectCoordinatorEntity",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableEntityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("entityRequest");
        assertThat(trigger.entityName()).isEqualTo(COORDINATOR_ENTITY_NAME);
    }

        @Test
        void givenTermSnapshotEntityFunction_whenCheckingTriggerContract_thenFunctionAndEntityTriggerAreConfigured()
                        throws NoSuchMethodException {
                var method = SejmTermSnapshotEntityFunctions.class.getDeclaredMethod(
                                "runSejmTermSnapshotEntity",
                                String.class,
                                ExecutionContext.class);

                var functionName = method.getAnnotation(FunctionName.class);
                var trigger = method.getParameters()[0].getAnnotation(DurableEntityTrigger.class);

                assertThat(functionName).isNotNull();
                assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_FUNCTION_NAME);
                assertThat(trigger).isNotNull();
                assertThat(trigger.name()).isEqualTo("entityRequest");
                assertThat(trigger.entityName()).isEqualTo(TERM_SNAPSHOT_ENTITY_NAME);
        }

    @Test
    void givenActivities_whenCheckingTriggerContract_thenFunctionAndActivityTriggersAreConfigured()
            throws NoSuchMethodException {
        assertActivityContract(SejmCollectVotingsActivityFunction.class, "collectVotings", SejmCollectFunctions.ACTIVITY_VOTINGS);
        assertActivityContract(
                SejmCollectCommitteesActivityFunction.class,
                "collectCommittees",
                SejmCollectFunctions.ACTIVITY_COMMITTEES);
        assertActivityContract(SejmCollectPrintsActivityFunction.class, "collectPrints", SejmCollectFunctions.ACTIVITY_PRINTS);
        assertActivityContract(
                SejmCollectInterpellationsActivityFunction.class,
                "collectInterpellations",
                SejmCollectFunctions.ACTIVITY_INTERPELLATIONS);
        assertActivityContract(
                SejmCollectQuestionsActivityFunction.class,
                "collectQuestions",
                SejmCollectFunctions.ACTIVITY_QUESTIONS);
        assertActivityContract(SejmCollectBillsActivityFunction.class, "collectBills", SejmCollectFunctions.ACTIVITY_BILLS);
    }

    @Test
    void givenAllCollectCoordinatorOperations_whenChecked_thenEachMapsToCoordinatorContractMethod() {
        var publicMethods = Arrays.stream(CollectCoordinatorContractV1.class.getMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (var op : COLLECT_COORDINATOR_OPERATIONS) {
            assertThat(publicMethods)
                    .as("enum %s must match a public method on CollectCoordinatorEntity", op)
                    .contains(op.toLowerCase());
        }
    }

    @Test
    void givenCoordinatorState_whenSerializedWithJackson_thenRoundTripsSuccessfully() throws Exception {
        var objectMapper = new ObjectMapper();
        var stateClass = Class.forName("onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorState");
        var constructor = stateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var state = constructor.newInstance();
        var setRunning = stateClass.getDeclaredMethod("setRunning", boolean.class);
        setRunning.setAccessible(true);
        setRunning.invoke(state, true);
        var setPendingRequests = stateClass.getDeclaredMethod("setPendingRequests", int.class);
        setPendingRequests.setAccessible(true);
        setPendingRequests.invoke(state, 2);

        var json = objectMapper.writeValueAsString(state);
        var restored = objectMapper.readValue(json, stateClass);
        var isRunning = stateClass.getDeclaredMethod("isRunning");
        isRunning.setAccessible(true);
        var getPendingRequests = stateClass.getDeclaredMethod("getPendingRequests");
        getPendingRequests.setAccessible(true);

        assertThat(isRunning.invoke(restored)).isEqualTo(true);
        assertThat(getPendingRequests.invoke(restored)).isEqualTo(2);
    }

    private static void assertActivityContract(
            Class<?> ownerType,
            String methodName,
            String expectedFunctionName) throws NoSuchMethodException {
        var method = ownerType.getDeclaredMethod(
                methodName,
                CollectActivityRequest.class,
                ExecutionContext.class);
        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableActivityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(expectedFunctionName);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("request");
    }
}
