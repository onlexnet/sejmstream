package onlexnet.app.ports.out;

import org.jspecify.annotations.Nullable;

/**
 * Minimal attachment metadata carried through the interpellation publishing pipeline.
 */
public record AttachmentMetadata(
        String replyKey,
        String name,
        String url,
        @Nullable String lastModified,
        @Nullable String fileName) {
}
