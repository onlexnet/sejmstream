package onlexnet.app.usecases;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;
import onlexnet.shared.Guards;

/**
 * Builds a social-media digest from collected Sejm daily data.
 */
@Component
public class SejmDigestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SejmDigestService.class);

    private static final int MAX_ITEMS_PER_SECTION = 5;
    private static final int AGENDA_EXCERPT_MAX_LENGTH = 160;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final String DATA_TYPE_COLUMN = "data_type";
    private static final String ITEM_JSON_COLUMN = "item_json";
    private static final String DATA_TYPE_VOTING = "VOTING";
    private static final String DATA_TYPE_COMMITTEE_SITTING = "COMMITTEE_SITTING";
    private static final String DATA_TYPE_PRINT = "PRINT";
    private static final String DATA_TYPE_INTERPELLATION = "INTERPELLATION";
    private static final String DATA_TYPE_WRITTEN_QUESTION = "WRITTEN_QUESTION";
    private static final String DATA_TYPE_BILL = "BILL";
    private static final String DIGEST_FOOTER = "#SejmStream #Sejm #ParlamentPolski";

    private final SejmDailyDigestPersistence repository;
    private final ObjectMapper objectMapper;

    public SejmDigestService(SejmDailyDigestPersistence repository,
            ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Builds a Polish digest post for the given date.
     *
     * @param date collection date
     * @return empty when no sections contain items
     */
    public Optional<String> buildDigest(LocalDate date) {
        var rows = this.repository.findByDate(date);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        var groupedRows = rows.stream()
            .map(row -> Map.entry(tryGetStringValue(row, DATA_TYPE_COLUMN), row))
            .flatMap(entry -> entry.getKey().stream()
                .map(key -> Map.entry(key, entry.getValue())))
                .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                        LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        var sections = new ArrayList<String>();
        addVotingSection(groupedRows, sections);
        addCommitteeSection(groupedRows, sections);
        addPrintSection(groupedRows, sections);
        addInterpellationSection(groupedRows, sections);
        addQuestionSection(groupedRows, sections);
        addBillSection(groupedRows, sections);

        if (sections.isEmpty()) {
            return Optional.empty();
        }

        var postBuilder = new StringBuilder();
        postBuilder.append("🏛️ Dzisiaj w Sejmie (")
                .append(date)
                .append("):\n\n");
        postBuilder.append(String.join("\n\n", sections));
        postBuilder.append("\n\n").append(DIGEST_FOOTER);
        return Optional.of(postBuilder.toString());
    }

    private void addVotingSection(Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_VOTING, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
            .map(this::toVotingItem)
            .flatMap(Optional::stream)
                .map(item -> "• " + safeText(item.topic())
                        + " — ZA: " + item.yes()
                        + ", PRZECIW: " + item.no()
                        + ", WSTRZYM: " + item.abstain())
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("📊 GŁOSOWANIA", lines));
    }

    private void addCommitteeSection(Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_COMMITTEE_SITTING, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
                .map(this::toCommitteeSittingItem)
            .flatMap(Optional::stream)
                .map(item -> "• " + safeText(item.code())
                        + " (" + safeText(item.status()) + ")"
                        + " — " + excerptAgenda(item.agenda()))
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("📋 KOMISJE", lines));
    }

    private void addPrintSection(Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_PRINT, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
                .map(this::toPrintItem)
            .flatMap(Optional::stream)
                .map(item -> "• Nr " + safeText(item.number()) + ": " + safeText(item.title()))
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("📄 DRUKI", lines));
    }

    private void addInterpellationSection(
            Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_INTERPELLATION, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
                .map(this::toInterpellationItem)
            .flatMap(Optional::stream)
                .map(item -> "• " + safeText(item.title())
                        + " → " + joinRecipients(item.to()))
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("🗣️ INTERPELACJE", lines));
    }

    private void addQuestionSection(Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_WRITTEN_QUESTION, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
                .map(this::toWrittenQuestionItem)
            .flatMap(Optional::stream)
                .map(item -> "• " + safeText(item.title())
                        + " → " + joinRecipients(item.to()))
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("❓ ZAPYTANIA", lines));
    }

    private void addBillSection(Map<String, List<Map<String, Object>>> groupedRows,
            List<String> sections) {
        var rows = groupedRows.getOrDefault(DATA_TYPE_BILL, List.of());
        if (rows.isEmpty()) {
            return;
        }

        var lines = rows.stream()
                .map(this::toBillItem)
            .flatMap(Optional::stream)
                .map(item -> "• " + safeText(item.title()))
                .toList();

        if (lines.isEmpty()) {
            return;
        }

        sections.add(formatSection("📝 PROJEKTY", lines));
    }

    private String formatSection(String header, List<String> lines) {
        var total = lines.size();
        var displayedLines = lines.stream().limit(MAX_ITEMS_PER_SECTION).toList();

        var section = new StringBuilder();
        section.append(header)
                .append(" (")
                .append(total)
                .append("):\n")
                .append(String.join("\n", displayedLines));

        if (total > MAX_ITEMS_PER_SECTION) {
            section.append("\n... i ")
                    .append(total - MAX_ITEMS_PER_SECTION)
                    .append(" więcej");
        }
        return section.toString();
    }

    private Optional<VotingItem> toVotingItem(Map<String, Object> row) {
        return readJson(row, VotingItem.class);
    }

    private Optional<CommitteeSittingItem> toCommitteeSittingItem(Map<String, Object> row) {
        return readJson(row, CommitteeSittingItem.class);
    }

    private Optional<PrintItem> toPrintItem(Map<String, Object> row) {
        return readJson(row, PrintItem.class);
    }

    private Optional<InterpellationItem> toInterpellationItem(Map<String, Object> row) {
        return readJson(row, InterpellationItem.class);
    }

    private Optional<WrittenQuestionItem> toWrittenQuestionItem(Map<String, Object> row) {
        return readJson(row, WrittenQuestionItem.class);
    }

    private Optional<BillItem> toBillItem(Map<String, Object> row) {
        return readJson(row, BillItem.class);
    }

    private <T> Optional<T> readJson(Map<String, Object> row, Class<T> type) {
        var json = tryGetJsonValue(row, ITEM_JSON_COLUMN).orElse(null);
        if (json == null || json.isBlank()) {
            LOGGER.warn("Skipping digest row due to missing item_json for type {}", type.getSimpleName());
            return Optional.empty();
        }
        try {
            return Optional.of(this.objectMapper.readValue(json, type));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Skipping malformed digest row for type {}: {}",
                    type.getSimpleName(),
                    exception.getOriginalMessage());
            return Optional.empty();
        }
    }

    private String joinRecipients(List<String> recipients) {
        var safeRecipients = Guards.orDefaultIfNullOrEmpty(
                recipients,
                java.util.Collections.<String>emptyList());
        if (safeRecipients.isEmpty()) {
            return "brak adresata";
        }
        return safeRecipients.stream().map(this::safeText).collect(Collectors.joining(", "));
    }

    private String excerptAgenda(String rawAgenda) {
        var withoutHtml = HTML_TAG_PATTERN.matcher(safeText(rawAgenda)).replaceAll(" ");
        var normalized = WHITESPACE_PATTERN.matcher(withoutHtml).replaceAll(" ").trim();
        if (normalized.length() <= AGENDA_EXCERPT_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, AGENDA_EXCERPT_MAX_LENGTH - 1) + "…";
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "brak danych";
        }
        return value.trim();
    }

    private Optional<String> tryGetJsonValue(Map<String, Object> row, String key) {
        return tryGetColumnValue(row, key).map(this::extractJsonValue);
    }

    private Optional<String> tryGetStringValue(Map<String, Object> row, String key) {
        return tryGetColumnValue(row, key).map(String::valueOf);
    }

    private Optional<Object> tryGetColumnValue(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            LOGGER.warn("Skipping digest row due to missing column {}", key);
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private String extractJsonValue(Object value) {
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (!"org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return String.valueOf(value);
        }
        try {
            var getValueMethod = value.getClass().getMethod("getValue");
            var extracted = getValueMethod.invoke(value);
            return extracted == null ? "" : String.valueOf(extracted);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to extract JSON value from {}: {}",
                    value.getClass().getName(),
                    exception.getMessage());
            return String.valueOf(value);
        }
    }
}