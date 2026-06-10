package com.example.funsejmapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.restfb.DefaultFacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.Page;

/**
 * Uses RestFB to publish the daily Facebook message.
 */
final class DefaultFacebookPublisher implements FacebookPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultFacebookPublisher.class);

    private final DefaultFacebookClient client;

    DefaultFacebookPublisher() {
        this(new FacebookTokenProvider().resolveToken());
    }

    DefaultFacebookPublisher(final String token) {
        this.client = createClient(token);
    }

    @Override
    public void publish(final String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        this.client.publish("me/feed",
                com.restfb.types.FacebookType.class,
                Parameter.with("message", message),
                Parameter.with("is_published", false));
        log.info("Published Facebook message: {}", message);
    }

    private DefaultFacebookClient createClient(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Facebook token is not configured");
        }

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
