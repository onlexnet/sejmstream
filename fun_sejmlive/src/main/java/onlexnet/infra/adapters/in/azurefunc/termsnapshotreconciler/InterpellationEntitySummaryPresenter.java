package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

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
        var lines = new StringJoiner("\n");
        appendCategory(lines, "Osoby", entities.persons());
        appendCategory(lines, "Firmy", entities.organizations());
        appendCategory(lines, "Miejscowości", entities.locations());
        return lines.toString();
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
