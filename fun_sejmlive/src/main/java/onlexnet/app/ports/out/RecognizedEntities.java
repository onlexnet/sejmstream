package onlexnet.app.ports.out;

import java.util.List;

/**
 * Named entities recognized in a text, grouped by the categories relevant to interpellation content.
 */
public record RecognizedEntities(
        List<RecognizedEntity> persons,
        List<RecognizedEntity> organizations,
        List<RecognizedEntity> locations) {

    public static RecognizedEntities empty() {
        return new RecognizedEntities(List.of(), List.of(), List.of());
    }
}
