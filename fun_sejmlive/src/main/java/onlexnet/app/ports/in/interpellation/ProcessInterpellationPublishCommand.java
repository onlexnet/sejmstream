package onlexnet.app.ports.in.interpellation;

import onlexnet.app.ports.out.InterpellationPublishQueueMessage;

/**
 * Command for processing a single queue message that publishes one interpellation.
 */
public record ProcessInterpellationPublishCommand(InterpellationPublishQueueMessage message) {
}
