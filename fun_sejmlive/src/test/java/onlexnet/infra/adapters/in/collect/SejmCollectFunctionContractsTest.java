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
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import com.microsoft.durabletask.EntityInstanceId;
import com.restfb.FacebookClient;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectHttpStarterFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectBillsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectCommitteesActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectInterpellationsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectPublishCollectEventActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectPrintsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectQuestionsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectactivity.SejmCollectVotingsActivityFunction;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.SejmCollectOrchestratorFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectTimerFunction;
import onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator.DailyDigestIngestionCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator.DailyDigestIngestionCoordinatorContractV1;
import onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator.DailyDigestIngestionCoordinatorActor;
import onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator.DailyDigestIngestionCoordinatorEntityFunction;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerCollectEventFunction;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerEntityFunction;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerActor;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectEventPublishRequestDTO;
import onlexnet.infra.adapters.out.SejmCollectService;
import onlexnet.testsupport.AppTest;
import onlexnet.testsupport.PostgresIntegrationTestSupport;

@AppTest
class SejmCollectFunctionContractsTest extends PostgresIntegrationTestSupport {

    private static final String COORDINATOR_ENTITY_NAME = "collectcoordinator";
    private static final String TERM_SNAPSHOT_ENTITY_NAME = "termsnapshotreconciler";

    private static final List<String> COLLECT_COORDINATOR_OPERATIONS = DailyDigestIngestionCoordinatorContractOperations.BUSINESS_OPERATIONS
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

        @Autowired
        private DailyDigestIngestionCoordinatorActor dailyDigestIngestionCoordinatorActor;

    @Test
    void shouldMatchActorRegistrationsAndSdkIdentitiesWithTriggerTypes() {
        var coordinator = applicationContext.getBean(DailyDigestIngestionCoordinatorActor.class);
        var reconciler = applicationContext.getBean(TermSnapshotReconcilerActor.class);
        var coordinatorId = new EntityInstanceId(SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY);
        var snapshotId = new EntityInstanceId(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME, "10");

        assertThat(coordinator.entityName()).isEqualTo(COORDINATOR_ENTITY_NAME).isEqualTo(coordinatorId.getName());
        assertThat(reconciler.entityName()).isEqualTo(TERM_SNAPSHOT_ENTITY_NAME).isEqualTo(snapshotId.getName());
        assertThat(coordinatorId).isEqualTo(new EntityInstanceId("collectcoordinator", "singleton"));
        assertThat(coordinatorId.getKey()).isEqualTo("singleton");
        assertThat(snapshotId.getKey()).isEqualTo("10");
        assertThat(snapshotId).isNotEqualTo(new EntityInstanceId("SejmTermSnapshot", "10"));
    }

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
        var method = DailyDigestIngestionCoordinatorEntityFunction.class.getDeclaredMethod(
                "runDailyDigestIngestionCoordinatorEntity",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableEntityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_CollectCoordinatorEntity");
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("entityRequest");
        assertThat(trigger.entityName()).isEqualTo(COORDINATOR_ENTITY_NAME);
    }

        @Test
        void givenTermSnapshotEntityFunction_whenCheckingTriggerContract_thenFunctionAndEntityTriggerAreConfigured()
                        throws NoSuchMethodException {
                var method = TermSnapshotReconcilerEntityFunction.class.getDeclaredMethod(
                                "runTermSnapshotReconcilerEntity",
                                String.class,
                                ExecutionContext.class);

                var functionName = method.getAnnotation(FunctionName.class);
                var trigger = method.getParameters()[0].getAnnotation(DurableEntityTrigger.class);

                assertThat(functionName).isNotNull();
                assertThat(functionName.value()).isEqualTo("Fun_TermSnapshotReconcilerEntity");
                assertThat(trigger).isNotNull();
                assertThat(trigger.name()).isEqualTo("entityRequest");
                assertThat(trigger.entityName()).isEqualTo(TERM_SNAPSHOT_ENTITY_NAME);
        }

    @Test
        void givenTermSnapshotCollectEventFunction_whenCheckingTriggerContract_thenQueueAndDurableClientBindingsAreConfigured()
            throws NoSuchMethodException {
        var method = TermSnapshotReconcilerCollectEventFunction.class.getDeclaredMethod(
                "runFromCollectEvent",
                String.class,
                DurableClientContext.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
                var queueTrigger = method.getParameters()[0].getAnnotation(QueueTrigger.class);
        var durableClientInput = method.getParameters()[1].getAnnotation(DurableClientInput.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.TERM_SNAPSHOT_COLLECT_EVENT_FUNCTION_NAME);
                assertThat(queueTrigger).isNotNull();
                assertThat(queueTrigger.name()).isEqualTo("eventPayload");
                assertThat(queueTrigger.queueName()).isEqualTo("%COLLECT_ORCHESTRATOR_QUEUE_NAME%");
                assertThat(queueTrigger.connection()).isEqualTo("DomainStorage");
        assertThat(durableClientInput).isNotNull();
        assertThat(durableClientInput.name()).isEqualTo("durableContext");
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
        assertActivityContract(
                SejmCollectPublishCollectEventActivityFunction.class,
                "publishCollectEvent",
                SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT,
                CollectEventPublishRequestDTO.class,
                String.class);
    }

    @Test
        void shouldExposeOnlyDispatchAsDailyDigestIngestionCoordinatorBusinessOperation() {
        var publicMethods = Arrays.stream(DailyDigestIngestionCoordinatorContractV1.class.getMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

                assertThat(COLLECT_COORDINATOR_OPERATIONS)
                                .containsExactly(DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName());

        for (var op : COLLECT_COORDINATOR_OPERATIONS) {
            assertThat(publicMethods)
                    .as("enum %s must match a public method on DailyDigestIngestionCoordinatorActor", op)
                    .contains(op.toLowerCase());
        }
    }

    @Test
    void givenCoordinatorState_whenSerializedWithJackson_thenRoundTripsSuccessfully() throws Exception {
        var objectMapper = new ObjectMapper();
                var getStateType = DailyDigestIngestionCoordinatorActor.class.getDeclaredMethod("getStateType");
                getStateType.setAccessible(true);
                @SuppressWarnings("unchecked")
                var stateClass = (Class<Object>) getStateType.invoke(this.dailyDigestIngestionCoordinatorActor);
        var constructor = stateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var state = constructor.newInstance();
        var setRunning = stateClass.getDeclaredMethod("setRunning", boolean.class);
        setRunning.setAccessible(true);
        setRunning.invoke(state, true);

        var json = objectMapper.writeValueAsString(state);
        var restored = objectMapper.readValue(json, stateClass);
        var isRunning = stateClass.getDeclaredMethod("isRunning");
        isRunning.setAccessible(true);

        assertThat(isRunning.invoke(restored)).isEqualTo(true);

        var restoredFromLegacyPayload = objectMapper.readValue(
                "{\"running\":true,\"pendingRequests\":2}",
                stateClass);
        assertThat(isRunning.invoke(restoredFromLegacyPayload)).isEqualTo(true);
    }

    private static void assertActivityContract(
            Class<?> ownerType,
            String methodName,
            String expectedFunctionName) throws NoSuchMethodException {
        assertActivityContract(
                ownerType,
                methodName,
                expectedFunctionName,
                CollectActivityRequestDTO.class,
                CollectActivityResultWire.class);
    }

    private static void assertActivityContract(
            Class<?> ownerType,
            String methodName,
            String expectedFunctionName,
            Class<?> requestType,
            Class<?> responseType) throws NoSuchMethodException {
        var method = ownerType.getDeclaredMethod(
                methodName,
                requestType,
                ExecutionContext.class);
        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableActivityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(expectedFunctionName);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("request");
                assertThat(method.getReturnType()).isEqualTo(responseType);
    }
}
