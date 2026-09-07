package onlexnet.app.ports.out;

/**
 * Output port for publishing collect orchestration events.
 */
public interface CollectOrchestratorEventPublisher {

    void publish(CollectOrchestratorEvent event);
}
