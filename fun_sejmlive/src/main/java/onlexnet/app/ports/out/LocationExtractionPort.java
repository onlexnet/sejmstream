package onlexnet.app.ports.out;

import java.util.List;

/**
 * Extracts Polish locality mentions from free text and normalizes them to unambiguous canonical
 * place names, distinct from general-purpose entity recognition (see {@link EntityRecognitionPort}).
 */
public interface LocationExtractionPort {

    /**
     * Extracts locality mentions from the given text.
     *
     * @param text free text to analyze
     * @return extracted, deduplicated location candidates, or an empty list when the text is
     *         blank, no localities are found, or extraction is unavailable
     */
    List<LocationCandidate> extractLocations(String text);
}
