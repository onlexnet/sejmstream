package onlexnet.app.ports.in.admin;

/**
 * Canonical admin action understood by the application core.
 */
public sealed interface AdminAction permits AdminAction.Noop, AdminAction.Help, AdminAction.Data,
        AdminAction.Collect, AdminAction.Publish, AdminAction.Version, AdminAction.Unknown {

    /**
     * No actionable command was supplied.
     */
    enum Noop implements AdminAction {
        INSTANCE
    }

    /**
     * User requested command overview.
     */
    enum Help implements AdminAction {
        INSTANCE
    }

    /**
     * User requested current Sejm term data.
     */
    enum Data implements AdminAction {
        INSTANCE
    }

    /**
     * User requested data collection side-effect.
     */
    enum Collect implements AdminAction {
        INSTANCE
    }

    /**
     * User requested publish side-effect.
     */
    enum Publish implements AdminAction {
        INSTANCE
    }

    /**
     * User requested the current build version.
     */
    enum Version implements AdminAction {
        INSTANCE
    }

    /**
     * Adapter parsed a command token unsupported by the core.
     */
    record Unknown(String command) implements AdminAction {
    }
}
