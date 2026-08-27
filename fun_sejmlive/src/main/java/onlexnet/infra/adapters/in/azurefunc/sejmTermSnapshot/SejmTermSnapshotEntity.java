package onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;

@Component
@Slf4j
@RequiredArgsConstructor
public class SejmTermSnapshotEntity implements TaskEntity, SejmTermSnapshotContractV1 {

    private SejmTermSnapshotEntityState state = UninitializedSejmTermSnapshotState.INSTANCE;
    private SejmTermSnapshotEntityContext context = UninitializedSejmTermSnapshotEntityContext.INSTANCE;

    protected Class<SejmTermSnapshotState> getStateType() {
        return SejmTermSnapshotState.class;
    }

    protected SejmTermSnapshotState initializeState(TaskEntityOperation operation) {
        return new SejmTermSnapshotState();
    }

    @Override
    public @Nullable Object run(TaskEntityOperation operation) {
        this.context = new InitializedSejmTermSnapshotEntityContext(operation.getContext());

        var stateType = getStateType();
        var persistedState = operation.getState().getState(stateType);
        this.state = persistedState == null ? initializeState(operation) : persistedState;

        var dispatchOperation = resolveContractOperation(operation.getName());
        dispatchOperation.invoke(this, operation);
        operation.getState().setState(requireState());
        return null;
    }

    public static DurableEntityOperationBinding<SejmTermSnapshotContractV1, ?> resolveContractOperation(String requestedMethod) {
        return SejmTermSnapshotContractOperations.resolveOperation(SejmTermSnapshotEntity.class, requestedMethod);
    }

    @Override
    public void termSnapshotCollected(TermSnapshotCollectedEvent event) {
        var outcome = requireState().handleTermSnapshotCollected(requireContextTermNum(), event);
        dispatchRecognizedEvents(outcome.diff());
    }

    protected void dispatchRecognizedEvents(TermSnapshotDiff diff) {
        if (!diff.newInterpellations().isEmpty()) {
            onNewInterpellationsDetected(toNewInterpellationsDetectedEvent(diff));
        }
        if (!diff.updatedInterpellations().isEmpty()) {
            onInterpellationsUpdated(toInterpellationsUpdatedEvent(diff));
        }
        if (!diff.newWrittenQuestions().isEmpty()) {
            onNewWrittenQuestionsDetected(toNewWrittenQuestionsDetectedEvent(diff));
        }
        if (!diff.newPrints().isEmpty()) {
            onNewPrintsDetected(toNewPrintsDetectedEvent(diff));
        }
        if (!diff.newBills().isEmpty()) {
            onNewBillsDetected(toNewBillsDetectedEvent(diff));
        }
    }

    protected void onNewInterpellationsDetected(NewInterpellationsDetectedEvent event) {
        log.info("Detected new interpellations for term {}: {}", event.termNum(), event.interpellationNums().size());
        log.debug("New interpellation keys for term {}: {}", event.termNum(), event.interpellationNums());
    }

    protected void onInterpellationsUpdated(InterpellationsUpdatedEvent event) {
        log.info("Detected updated interpellations for term {}: {}", event.termNum(), event.interpellationNums().size());
        log.debug("Updated interpellation keys for term {}: {}", event.termNum(), event.interpellationNums());
    }

    protected void onNewWrittenQuestionsDetected(NewWrittenQuestionsDetectedEvent event) {
        log.info("Detected new written questions for term {}: {}", event.termNum(), event.questionNums().size());
        log.debug("New written question keys for term {}: {}", event.termNum(), event.questionNums());
    }

    protected void onNewPrintsDetected(NewPrintsDetectedEvent event) {
        log.info("Detected new prints for term {}: {}", event.termNum(), event.printNums().size());
        log.debug("New print keys for term {}: {}", event.termNum(), event.printNums());
    }

    protected void onNewBillsDetected(NewBillsDetectedEvent event) {
        log.info("Detected new bills for term {}: {}", event.termNum(), event.billNums().size());
        log.debug("New bill keys for term {}: {}", event.termNum(), event.billNums());
    }

    protected NewInterpellationsDetectedEvent toNewInterpellationsDetectedEvent(TermSnapshotDiff diff) {
        return new NewInterpellationsDetectedEvent(diff.termNum(), diff.newInterpellations());
    }

    protected InterpellationsUpdatedEvent toInterpellationsUpdatedEvent(TermSnapshotDiff diff) {
        return new InterpellationsUpdatedEvent(diff.termNum(), diff.updatedInterpellations());
    }

    protected NewWrittenQuestionsDetectedEvent toNewWrittenQuestionsDetectedEvent(TermSnapshotDiff diff) {
        return new NewWrittenQuestionsDetectedEvent(diff.termNum(), diff.newWrittenQuestions());
    }

    protected NewPrintsDetectedEvent toNewPrintsDetectedEvent(TermSnapshotDiff diff) {
        return new NewPrintsDetectedEvent(diff.termNum(), diff.newPrints());
    }

    protected NewBillsDetectedEvent toNewBillsDetectedEvent(TermSnapshotDiff diff) {
        return new NewBillsDetectedEvent(diff.termNum(), diff.newBills());
    }

    private SejmTermSnapshotState requireState() {
        if (this.state instanceof SejmTermSnapshotState initializedState) {
            return initializedState;
        }
        throw new IllegalStateException("state must be initialized in run() before contract dispatch");
    }

    private TaskEntityContext requireContext() {
        if (this.context instanceof InitializedSejmTermSnapshotEntityContext initializedContext) {
            return initializedContext.value();
        }
        throw new IllegalStateException("context must be initialized in run() before contract dispatch");
    }

    private int requireContextTermNum() {
        var key = requireContext().getId().getKey();
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("term snapshot entity key must be an integer, but was: " + key, ex);
        }
    }

    private static List<String> addedKeys(TreeSet<String> previous, TreeSet<String> current) {
        var added = new ArrayList<String>();
        for (var key : current) {
            if (!previous.contains(key)) {
                added.add(key);
            }
        }
        return List.copyOf(added);
    }

    private static List<String> updatedInterpellationKeys(
            Map<String, String> previous,
            Map<String, String> current) {
        var updated = new ArrayList<String>();
        for (var entry : current.entrySet()) {
            var previousFingerprint = previous.get(entry.getKey());
            if (previousFingerprint != null && !Objects.equals(previousFingerprint, entry.getValue())) {
                updated.add(entry.getKey());
            }
        }
        return List.copyOf(updated);
    }

    record TermSnapshotDiff(
            int termNum,
            List<String> newInterpellations,
            List<String> updatedInterpellations,
            List<String> newWrittenQuestions,
            List<String> newPrints,
            List<String> newBills) {
    }

    record ReconciliationOutcome(TermSnapshotDiff diff) {
    }

    record NewInterpellationsDetectedEvent(int termNum, List<String> interpellationNums) {
    }

    record InterpellationsUpdatedEvent(int termNum, List<String> interpellationNums) {
    }

    record NewWrittenQuestionsDetectedEvent(int termNum, List<String> questionNums) {
    }

    record NewPrintsDetectedEvent(int termNum, List<String> printNums) {
    }

    record NewBillsDetectedEvent(int termNum, List<String> billNums) {
    }

    static ReconciliationOutcome reconcile(
            SejmTermSnapshotState state,
            int termNum,
            TermSnapshotCollectedEvent event) {
        var previousSnapshot = state.getLatestSnapshot();
        var previousInterpellations = new HashMap<String, String>();
        var previousQuestions = new TreeSet<String>();
        var previousPrints = new TreeSet<String>();
        var previousBills = new TreeSet<String>();

        if (previousSnapshot != null) {
            previousInterpellations.putAll(previousSnapshot.interpellationFingerprints());
            previousQuestions.addAll(previousSnapshot.writtenQuestionKeys());
            previousPrints.addAll(previousSnapshot.printKeys());
            previousBills.addAll(previousSnapshot.billKeys());
        }

        var currentInterpellations = new HashMap<>(event.interpellationFingerprints());
        var currentQuestions = new TreeSet<>(event.writtenQuestionKeys());
        var currentPrints = new TreeSet<>(event.printKeys());
        var currentBills = new TreeSet<>(event.billKeys());

        var diff = new TermSnapshotDiff(
            termNum,
                addedKeys(new TreeSet<>(previousInterpellations.keySet()), new TreeSet<>(currentInterpellations.keySet())),
                updatedInterpellationKeys(previousInterpellations, currentInterpellations),
                addedKeys(previousQuestions, currentQuestions),
                addedKeys(previousPrints, currentPrints),
                addedKeys(previousBills, currentBills));

        var snapshot = new TermSnapshotPayload(
            termNum,
            event.collectionDate(),
                Map.copyOf(currentInterpellations),
                List.copyOf(currentQuestions),
                List.copyOf(currentPrints),
                List.copyOf(currentBills));

        state.setLatestSnapshot(snapshot);

        return new ReconciliationOutcome(diff);
    }
}

sealed interface SejmTermSnapshotEntityState permits SejmTermSnapshotState, UninitializedSejmTermSnapshotState {
}

enum UninitializedSejmTermSnapshotState implements SejmTermSnapshotEntityState {
    INSTANCE
}

sealed interface SejmTermSnapshotEntityContext
        permits InitializedSejmTermSnapshotEntityContext, UninitializedSejmTermSnapshotEntityContext {
}

record InitializedSejmTermSnapshotEntityContext(TaskEntityContext value) implements SejmTermSnapshotEntityContext {
    InitializedSejmTermSnapshotEntityContext {
        Objects.requireNonNull(value, "value");
    }
}

enum UninitializedSejmTermSnapshotEntityContext implements SejmTermSnapshotEntityContext {
    INSTANCE
}