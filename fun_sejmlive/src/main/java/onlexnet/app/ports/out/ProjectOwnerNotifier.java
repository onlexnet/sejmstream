package onlexnet.app.ports.out;

/**
 * Sends operational alerts to the project owner.
 */
public interface ProjectOwnerNotifier {

    /**
     * Delivers one alert message.
     */
    void notifyOwner(String message);
}
