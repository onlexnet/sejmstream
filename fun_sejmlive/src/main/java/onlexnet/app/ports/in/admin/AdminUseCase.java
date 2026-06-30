package onlexnet.app.ports.in.admin;

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
