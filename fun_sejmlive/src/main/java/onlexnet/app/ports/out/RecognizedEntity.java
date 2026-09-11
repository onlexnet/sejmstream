package onlexnet.app.ports.out;

import org.jspecify.annotations.Nullable;

/**
 * A single named-entity mention recognized in text, optionally resolved to a canonical
 * (entity-linked) name shared by other mentions of the same real-world entity.
 */
public record RecognizedEntity(String mentionText, @Nullable String canonicalName) {
}
