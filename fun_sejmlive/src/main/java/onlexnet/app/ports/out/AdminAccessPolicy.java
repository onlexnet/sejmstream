package onlexnet.app.ports.out;

import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminActor;

/**
 * Application output port for admin authorization decisions.
 */
public interface AdminAccessPolicy {

    /**
     * Returns whether the given actor is allowed to invoke the requested action.
     */
    boolean isAllowed(AdminActor actor, AdminAction action);
}
