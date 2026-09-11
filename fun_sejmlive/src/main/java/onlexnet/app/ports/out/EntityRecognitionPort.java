package onlexnet.app.ports.out;

/**
 * Extracts and links named entities (persons, organizations, locations) from free text.
 */
public interface EntityRecognitionPort {

    /**
     * Recognizes named entities in the given text.
     *
     * @param text free text to analyze
     * @return recognized entities grouped by category, or {@link RecognizedEntities#empty()} when
     *         the text is blank or recognition is unavailable
     */
    RecognizedEntities recognize(String text);
}
