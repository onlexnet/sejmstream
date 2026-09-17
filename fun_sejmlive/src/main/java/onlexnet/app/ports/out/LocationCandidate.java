package onlexnet.app.ports.out;

import org.jspecify.annotations.Nullable;

/**
 * A single Polish locality mention extracted from interpellation text and normalized to its
 * canonical (official) name, together with the geographic metadata the extractor could resolve.
 *
 * @param rawMention    the exact text fragment from the source referring to the place
 * @param canonicalName the normalized official Polish name of the location
 * @param province      the voivodeship (województwo), when known
 * @param county        the county/powiat, when known
 * @param latitude      geographic latitude, when confidently known
 * @param longitude     geographic longitude, when confidently known
 * @param confidence    extractor confidence in the range {@code [0.0, 1.0]}
 */
public record LocationCandidate(
        String rawMention,
        String canonicalName,
        @Nullable String province,
        @Nullable String county,
        @Nullable Double latitude,
        @Nullable Double longitude,
        double confidence) {
}
