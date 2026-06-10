package onlexnet.sejmapi;

import java.util.List;

/**
 * Demo-only output payload returned by the durable workflow activity.
 *
 * @param correlationId correlation identifier propagated from the request
 * @param source static marker indicating demo data origin
 * @param demoRows generated demo rows returned to the caller
 */
public record DemoWorkflowResult(
        String correlationId,
        String source,
        List<String> demoRows) {
}
