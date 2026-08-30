package onlexnet.infra.adapters.out.facebook;

import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

import com.restfb.FacebookClient;
import com.restfb.Parameter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.FacebookPublisher;

@Component
@Slf4j
@RequiredArgsConstructor
class DefaultFacebookPublisher implements FacebookPublisher {

    private final FacebookClient client;

    @Override
    public @Nullable String publish(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        var result = this.client.publish("me/feed",
                com.restfb.types.FacebookType.class,
                Parameter.with("message", message));
        log.info("Published Facebook message: {}", message);
        return result != null ? result.getId() : null;
    }

    @Override
    public void publishComment(String postId, String comment) {
        if (postId == null || postId.isBlank()) {
            return;
        }
        if (comment == null || comment.isBlank()) {
            return;
        }

        this.client.publish(postId + "/comments",
                com.restfb.types.FacebookType.class,
                Parameter.with("message", comment));
        log.info("Published Facebook comment on post {}: {}", postId, comment);
    }

}