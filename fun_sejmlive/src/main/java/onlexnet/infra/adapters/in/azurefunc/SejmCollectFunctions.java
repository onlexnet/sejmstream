package onlexnet.infra.adapters.in.azurefunc;

/**
 * Function name constants for collect-related Azure Functions.
 *
 * <p>Runtime entry points are intentionally split into dedicated classes:
 * timer, http starter, orchestrator, and one class per activity function.
 */
public final class SejmCollectFunctions {

    /** Timer trigger function name. */
    public static final String TIMER_FUNCTION_NAME = "Fun_SejmCollectTimer";
    /** HTTP starter function name for manual trigger. */
    public static final String HTTP_STARTER_FUNCTION_NAME = "Fun_CollectStart";
    /** Durable orchestrator function name. */
    public static final String ORCHESTRATOR_FUNCTION_NAME = "Fun_CollectOrchestrator";
    /** Durable entity function name coordinating collect runs. */
    public static final String COORDINATOR_ENTITY_FUNCTION_NAME = "Fun_CollectCoordinatorEntity";
    /** Durable entity logical name used by the runtime. */
    static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    /** Durable entity singleton key for collect coordination. */
    static final String COORDINATOR_ENTITY_KEY = "singleton";
    /** Durable entity function name for term snapshot state and diffing. */
    public static final String TERM_SNAPSHOT_ENTITY_FUNCTION_NAME = "Fun_SejmTermSnapshotEntity";
    /** Durable entity logical name used by the runtime for term snapshots. */
    public static final String TERM_SNAPSHOT_ENTITY_NAME = "SejmTermSnapshot";
    /** Durable entity key for term snapshot state is dynamic: String.valueOf(termNum). */
    /** Activity function name for collecting votings. */
    public static final String ACTIVITY_VOTINGS = "Intern_CollectVotings";
    /** Activity function name for collecting committee sittings. */
    public static final String ACTIVITY_COMMITTEES = "Intern_CollectCommittees";
    /** Activity function name for collecting prints. */
    public static final String ACTIVITY_PRINTS = "Intern_CollectPrints";
    /** Activity function name for collecting interpellations. */
    public static final String ACTIVITY_INTERPELLATIONS = "Intern_CollectInterpellations";
    /** Activity function name for collecting written questions. */
    public static final String ACTIVITY_QUESTIONS = "Intern_CollectQuestions";
    /** Activity function name for collecting bills. */
    public static final String ACTIVITY_BILLS = "Intern_CollectBills";

}
