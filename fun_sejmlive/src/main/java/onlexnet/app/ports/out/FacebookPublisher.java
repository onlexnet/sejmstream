package onlexnet.app.ports.out;

/**
 * Publishes a Facebook post and optional comments under it.
 */
public interface FacebookPublisher {

    String publish(String message);

    void publishComment(String postId, String comment);
}