package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import onlexnet.app.ports.out.LocationCandidate;
import onlexnet.app.ports.out.RecognizedEntities;
import onlexnet.app.ports.out.RecognizedEntity;

/**
 * Renders recognized entities (persons, organizations, locations) into a Telegram-friendly summary,
 * unifying mentions of the same entity via its Entity-Linking canonical name, falling back to
 * case/diacritics-insensitive normalization for mentions that could not be linked.
 */
@Component
public final class InterpellationEntitySummaryPresenter {

    /**
     * Formats recognized entities into labeled lines, one per non-empty category. Returns a blank
     * string when no entities were recognized.
     */
    public String present(RecognizedEntities entities) {
        return present(entities, List.of());
    }

    /**
     * Formats recognized entities into labeled lines as {@link #present(RecognizedEntities)}, but
     * renders the "Miejscowości" line from dedicated {@code locations} (each annotated with its
     * province when known) when available, falling back to the general NER {@code Location}
     * mentions in {@code entities} when {@code locations} is empty.
     */
    public String present(RecognizedEntities entities, List<LocationCandidate> locations) {
        var lines = new StringJoiner("\n");
        appendCategory(lines, "Osoby", entities.persons());
        appendCategory(lines, "Firmy", entities.organizations());
        appendLocationsCategory(lines, locations, entities.locations());
        return lines.toString();
    }

    private void appendLocationsCategory(
            StringJoiner lines,
            List<LocationCandidate> locations,
            List<RecognizedEntity> fallbackMentions) {
        var unifiedLocations = unifyLocations(locations);
        if (!unifiedLocations.isEmpty()) {
            lines.add("  Miejscowości: " + String.join(", ", unifiedLocations));
            return;
        }
        appendCategory(lines, "Miejscowości", fallbackMentions);
    }

    private List<String> unifyLocations(List<LocationCandidate> locations) {
        var displayNameByKey = new LinkedHashMap<String, String>();
        for (var location : locations) {
            if (location.canonicalName().isBlank()) {
                continue;
            }
            var displayName = location.province() == null || location.province().isBlank()
                    ? location.canonicalName()
                    : location.canonicalName() + " (" + location.province() + ")";
            displayNameByKey.putIfAbsent(normalize(location.canonicalName()), displayName);
        }
        return List.copyOf(displayNameByKey.values());
    }

    private void appendCategory(StringJoiner lines, String label, List<RecognizedEntity> mentions) {
        var unified = unify(mentions);
        if (!unified.isEmpty()) {
            lines.add("  " + label + ": " + String.join(", ", unified));
        }
    }

    private List<String> unify(List<RecognizedEntity> mentions) {
        var displayNameByKey = new LinkedHashMap<String, String>();
        for (var mention : mentions) {
            var displayName = displayNameOf(mention);
            if (displayName.isBlank()) {
                continue;
            }
            displayNameByKey.putIfAbsent(normalize(displayName), displayName);
        }
        return List.copyOf(displayNameByKey.values());
    }

    private String displayNameOf(RecognizedEntity mention) {
        var canonicalName = mention.canonicalName();
        if (canonicalName != null && !canonicalName.isBlank()) {
            return canonicalName;
        }
        return mention.mentionText() == null ? "" : mention.mentionText();
    }

    private String normalize(String value) {
        var withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutDiacritics.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
