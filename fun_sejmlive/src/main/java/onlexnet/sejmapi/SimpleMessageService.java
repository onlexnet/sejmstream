package onlexnet.sejmapi;

/**
 * Tiny service used to demonstrate constructor injection in Azure Functions.
 */
public final class SimpleMessageService {

    private final String source;

    public SimpleMessageService(final String source) {
        this.source = source;
    }

    public String buildMessage() {
        return "Service says hello (" + this.source + ")";
    }
}
