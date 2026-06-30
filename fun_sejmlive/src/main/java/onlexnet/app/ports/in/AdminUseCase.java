package onlexnet.app.ports.in;

import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;

/**
 * Application use case for administrative commands.
 */
public interface AdminUseCase {

	/**
	 * Handles one canonical admin action request.
	 *
	 * @param request admin request with actor, canonical action and metadata
	 * @return channel-agnostic handling outcome
	 */
	AdminOutcome handleAdminAction(AdminCommandRequest request);
}
