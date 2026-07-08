package onlexnet.app.ports.in.interpellation;

/**
 * Input port that processes one queue message and publishes one INTERPELLATION to Facebook.
 */
public interface ProcessInterpellationPublishUseCase {

    ProcessInterpellationPublishOutcome process(ProcessInterpellationPublishCommand command);
}
