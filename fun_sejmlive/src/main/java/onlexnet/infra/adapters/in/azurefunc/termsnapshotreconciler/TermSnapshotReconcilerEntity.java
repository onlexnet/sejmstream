package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.ProjectOwnerNotifier;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.base.EntityComponent;
import onlexnet.infra.adapters.in.azurefunc.base.TaskEntityLifecycleContext;

@Component
@Slf4j
@RequiredArgsConstructor
public class TermSnapshotReconcilerEntity implements TermSnapshotReconcilerContractV1, EntityComponent {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;

    private final ProjectOwnerNotifier projectOwnerNotifier;

    private TermSnapshotReconcilerEntityState state = UninitializedTermSnapshotReconcilerState.INSTANCE;
    private TaskEntityLifecycleContext context = TaskEntityLifecycleContext.uninitialized();

    protected Class<TermSnapshotReconcilerState> getStateType() {
        return TermSnapshotReconcilerState.class;
    }

    protected TermSnapshotReconcilerState initializeState(TaskEntityOperation operation) {
        return new TermSnapshotReconcilerState();
    }

    @Override
    public String entityName() {
        return SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.toLowerCase(Locale.ROOT);
    }

    @Override
    public DurableEntityOperationBinding<TermSnapshotReconcilerContractV1, ?> resolveOperation(String requestedMethod) {
        return resolveContractOperation(requestedMethod);
    }

    @Override
    public @Nullable Object runOperation(TaskEntityOperation operation) {
        this.context = TaskEntityLifecycleContext.initialized(operation.getContext());

        var stateType = getStateType();
        var persistedState = operation.getState().getState(stateType);
        this.state = persistedState == null ? initializeState(operation) : persistedState;

        var dispatchOperation = resolveContractOperation(operation.getName());
        dispatchOperation.invoke(this, operation);
        operation.getState().setState(requireState());
        return null;
    }

    public static DurableEntityOperationBinding<TermSnapshotReconcilerContractV1, ?> resolveContractOperation(
            String requestedMethod) {
        return TermSnapshotReconcilerContractOperations.resolveOperation(TermSnapshotReconcilerEntity.class,
                requestedMethod);
    }

    @Override
    public void termSnapshotCollected(TermSnapshotCollectedEvent event) {
        var outcome = requireState().handleTermSnapshotCollected(requireContextTermNum(), event);
        dispatchRecognizedEvents(outcome.diff(), event);
    }

    protected void dispatchRecognizedEvents(TermSnapshotDiff diff, TermSnapshotCollectedEvent event) {
        if (hasRecognizedChanges(diff)) {
            notifyOwnerAboutRecognizedChanges(diff);
        }
        if (!diff.newInterpellations().isEmpty()) {
            onNewInterpellationsDetected(toNewInterpellationsDetectedEvent(diff, event));
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

    private void notifyOwnerAboutRecognizedChanges(TermSnapshotDiff diff) {
        try {
            projectOwnerNotifier.notifyOwner(toOwnerSummaryMessage(diff));
        } catch (RuntimeException exception) {
            log.warn("Failed to send term snapshot change summary for term {}", diff.termNum(), exception);
        }
    }

    private static boolean hasRecognizedChanges(TermSnapshotDiff diff) {
        return !diff.newInterpellations().isEmpty()
                || !diff.updatedInterpellations().isEmpty()
                || !diff.newWrittenQuestions().isEmpty()
                || !diff.newPrints().isEmpty()
                || !diff.newBills().isEmpty();
    }

    private static String toOwnerSummaryMessage(TermSnapshotDiff diff) {
        return "Term snapshot updates detected"
                + "\nTerm: " + diff.termNum()
                + "\nNew interpellations: " + diff.newInterpellations().size()
                + "\nUpdated interpellations: " + diff.updatedInterpellations().size()
                + "\nNew written questions: " + diff.newWrittenQuestions().size()
                + "\nNew prints: " + diff.newPrints().size()
                + "\nNew bills: " + diff.newBills().size();
    }

    protected void onNewInterpellationsDetected(NewInterpellationsDetectedEvent event) {
        log.info("Detected new interpellations for term {}: {}", event.termNum(), event.interpellationNums().size());
        log.debug("New interpellation keys for term {}: {}", event.termNum(), event.interpellationNums());
        notifyOwnerAboutNewInterpellations(event);
    }

    private void notifyOwnerAboutNewInterpellations(NewInterpellationsDetectedEvent event) {
        try {
            for (var chunk : chunkMessage(toOwnerNewInterpellationsMessage(event))) {
                projectOwnerNotifier.notifyOwner(chunk);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to send new interpellations notification for term {}", event.termNum(), exception);
        }
    }

    private static String toOwnerNewInterpellationsMessage(NewInterpellationsDetectedEvent event) {
        var message = new StringBuilder();
        message.append("Nowe interpelacje wykryte")
                .append("\nKadencja: ").append(event.termNum())
                .append("\nLiczba: ").append(event.interpellationNums().size());

        for (var interpellationNum : event.interpellationNums()) {
            var presentation = event.interpellationPresentation().get(interpellationNum);
            var title = presentation == null || presentation.title() == null || presentation.title().isBlank()
                    ? "brak tytułu"
                    : presentation.title();
            var webDescriptionUrl = presentation == null
                    || presentation.webDescriptionUrl() == null
                    || presentation.webDescriptionUrl().isBlank()
                            ? "(brak linku)"
                            : presentation.webDescriptionUrl();

            message.append("\n- ").append(interpellationNum).append(": ").append(title)
                    .append("\n  ").append(webDescriptionUrl);
        }
        return message.toString();
    }

    private static List<String> chunkMessage(String message) {
        if (message.length() <= TELEGRAM_MESSAGE_LIMIT) {
            return List.of(message);
        }

        var chunks = new ArrayList<String>();
        var start = 0;
        while (start < message.length()) {
            var end = Math.min(start + TELEGRAM_MESSAGE_LIMIT, message.length());
            if (end < message.length()) {
                var lastBreak = message.lastIndexOf('\n', end);
                if (lastBreak > start + 32) {
                    end = lastBreak;
                }
            }
            chunks.add(message.substring(start, end).trim());
            start = end;
            while (start < message.length() && message.charAt(start) == '\n') {
                start++;
            }
        }
        return List.copyOf(chunks);
    }

    protected void onInterpellationsUpdated(InterpellationsUpdatedEvent event) {
        log.info("Detected updated interpellations for term {}: {}", event.termNum(),
                event.interpellationNums().size());
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

    protected NewInterpellationsDetectedEvent toNewInterpellationsDetectedEvent(
            TermSnapshotDiff diff,
            TermSnapshotCollectedEvent event) {
        var presentationByNum = new LinkedHashMap<String, TermSnapshotCollectedEvent.InterpellationPresentation>();
        for (var interpellationNum : diff.newInterpellations()) {
            presentationByNum.put(interpellationNum, event.interpellationPresentation().get(interpellationNum));
        }
        return new NewInterpellationsDetectedEvent(diff.termNum(), diff.newInterpellations(), Map.copyOf(presentationByNum));
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

    private TermSnapshotReconcilerState requireState() {
        if (this.state instanceof TermSnapshotReconcilerState initializedState) {
            return initializedState;
        }
        throw new IllegalStateException("state must be initialized in run() before contract dispatch");
    }

    private TaskEntityContext requireContext() {
        return this.context.requireInitialized("context must be initialized in run() before contract dispatch");
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

        record NewInterpellationsDetectedEvent(
            int termNum,
            List<String> interpellationNums,
            Map<String, TermSnapshotCollectedEvent.InterpellationPresentation> interpellationPresentation) {
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
            TermSnapshotReconcilerState state,
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
                addedKeys(new TreeSet<>(previousInterpellations.keySet()),
                        new TreeSet<>(currentInterpellations.keySet())),
                updatedInterpellationKeys(previousInterpellations, currentInterpellations),
                addedKeys(previousQuestions, currentQuestions),
                addedKeys(previousPrints, currentPrints),
                addedKeys(previousBills, currentBills));

        // Persist previous + current merged so items untouched on a given day aren't forgotten and later misdetected as "new".
        var mergedInterpellations = new HashMap<>(previousInterpellations);
        mergedInterpellations.putAll(currentInterpellations);
        var mergedQuestions = new TreeSet<>(previousQuestions);
        mergedQuestions.addAll(currentQuestions);
        var mergedPrints = new TreeSet<>(previousPrints);
        mergedPrints.addAll(currentPrints);
        var mergedBills = new TreeSet<>(previousBills);
        mergedBills.addAll(currentBills);

        var snapshot = new TermSnapshotPayload(
                termNum,
                event.collectionDate(),
                Map.copyOf(mergedInterpellations),
                List.copyOf(mergedQuestions),
                List.copyOf(mergedPrints),
                List.copyOf(mergedBills));

        state.setLatestSnapshot(snapshot);

        return new ReconciliationOutcome(diff);
    }
}

sealed interface TermSnapshotReconcilerEntityState
        permits TermSnapshotReconcilerState, UninitializedTermSnapshotReconcilerState {
}

enum UninitializedTermSnapshotReconcilerState implements TermSnapshotReconcilerEntityState {
    INSTANCE
}