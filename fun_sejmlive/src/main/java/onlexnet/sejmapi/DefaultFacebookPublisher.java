package onlexnet.sejmapi;

import lombok.extern.slf4j.Slf4j;

import com.restfb.DefaultFacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.Page;

@Slf4j
final class DefaultFacebookPublisher implements FacebookPublisher {

    private final String token;
    /** Lazily initialised on the first {@link #publish} call; guarded by {@link #ensureClient()}. */
    private DefaultFacebookClient client = null;

    DefaultFacebookPublisher(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Facebook token is not configured");
        }
        this.token = token;
    }

    @Override
    public void publish(final String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        ensureClient();
        this.client.publish("me/feed",
                com.restfb.types.FacebookType.class,
                Parameter.with("message", message));
        log.info("Published Facebook message: {}", message);
    }

    private synchronized void ensureClient() {
        if (this.client == null) {
            this.client = createClient(this.token);
        }
    }

    private DefaultFacebookClient createClient(final String token) {
        var fbClient = new DefaultFacebookClient(token, Version.LATEST);
        try {
            var pages = fbClient.fetchConnection("me/accounts", Page.class);
            var pageAccessToken = pages.getData().stream()
                    .filter(page -> System.getenv().getOrDefault("FB_PAGE_NAME", "SejmStream2")
                            .equals(page.getName()))
                    .map(Page::getAccessToken)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst();

            if (pageAccessToken.isPresent()) {
                return new DefaultFacebookClient(pageAccessToken.get(), Version.LATEST);
            }

            log.warn("Could not resolve a page access token; using the configured token directly.");
            return fbClient;
        } catch (FacebookOAuthException exception) {
            if (exception.getErrorCode() == 100) {
                return fbClient;
            }
            throw exception;
        }
    }
}
