package onlexnet.app.ports.out;

/**
 * Publishes a Facebook post.
 */
public interface FacebookPublisher {

    void publish(String message);
}