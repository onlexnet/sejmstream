package onlexnet.app.ports.in.admin;

/**
 * Actor identity known to the application layer.
 */
public sealed interface AdminActor permits AdminActor.ExternalActor {

    /**
     * Actor represented by an external identifier.
     */
    record ExternalActor(String externalId) implements AdminActor {
    }
}
