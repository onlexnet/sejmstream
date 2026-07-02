package onlexnet.sejmapi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;

/**
 * Service that collects Sejm activity data from the API and stores it in the daily digest table.
 * Provides methods to collect items from six different Sejm API categories and persist them
 * as JSON in the database. Each collect method is idempotent due to database upsert semantics.
 */
@Component
public class SejmCollectService implements SejmCollectOperations {

    private static final Logger LOGGER = Logger.getLogger(SejmCollectService.class.getName());

    private final SejmApiClient sejmApiClient;
        private final SejmDailyDigestPersistence repository;
    private final ObjectMapper objectMapper;

    public SejmCollectService(final SejmApiClient sejmApiClient,
            final SejmDailyDigestPersistence repository,
            final ObjectMapper objectMapper) {
        this.sejmApiClient = Objects.requireNonNull(sejmApiClient, "sejmApiClient must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Collects voting items for the given term and date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectVotings(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            return collectItems(termNum, date, null, "VOTING", "voting(s)", "votings",
                    () -> sejmApiClient.fetchVotingsForDate(termNum, date),
                    item -> item.sitting() + "/" + item.votingNumber(), VotingItem::topic);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting votings for term " + termNum, e);
            throw new IllegalStateException("Failed to collect votings", e);
        }
    }

    /**
     * Collects committee sitting items for the given term and date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectCommitteeSittings(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            return collectItems(termNum, date, null, "COMMITTEE_SITTING",
                    "committee sitting(s)", "committee sittings",
                    () -> sejmApiClient.fetchCommitteeSittingsForDate(termNum, date),
                    item -> item.code() + "/" + item.num(), CommitteeSittingItem::agenda);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting committee sittings for term " + termNum, e);
            throw new IllegalStateException("Failed to collect committee sittings", e);
        }
    }

    /**
     * Collects print items modified since the given date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectPrints(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            return collectItems(termNum, date, null, "PRINT", "print(s)", "prints",
                    () -> sejmApiClient.fetchPrintsModifiedSince(termNum, date), PrintItem::number,
                    PrintItem::title);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting prints for term " + termNum, e);
            throw new IllegalStateException("Failed to collect prints", e);
        }
    }

    /**
     * Collects interpellation items modified since the start of the given date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectInterpellations(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            var since = startOfDay(date);
            return collectItems(termNum, date, since, "INTERPELLATION", "interpellation(s)",
                    "interpellations",
                    () -> sejmApiClient.fetchInterpellationsModifiedSince(termNum, since),
                    item -> String.valueOf(item.num()), InterpellationItem::title);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting interpellations for term " + termNum, e);
            throw new IllegalStateException("Failed to collect interpellations", e);
        }
    }

    /**
     * Collects written question items modified since the start of the given date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectWrittenQuestions(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            var since = startOfDay(date);
            return collectItems(termNum, date, since, "WRITTEN_QUESTION", "written question(s)",
                    "written questions",
                    () -> sejmApiClient.fetchWrittenQuestionsModifiedSince(termNum, since),
                    item -> String.valueOf(item.num()), WrittenQuestionItem::title);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting written questions for term " + termNum, e);
            throw new IllegalStateException("Failed to collect written questions", e);
        }
    }

    /**
     * Collects bill items received since the given date.
     *
     * @param termNum Sejm term number
     * @param date    collection date
     * @return number of items upserted
     */
    public int collectBills(final int termNum, final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        try {
            return collectItems(termNum, date, null, "BILL", "bill(s)", "bills",
                    () -> sejmApiClient.fetchBillsReceivedSince(termNum, date), BillItem::number,
                    BillItem::title);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting bills for term " + termNum, e);
            throw new IllegalStateException("Failed to collect bills", e);
        }
    }

    private <T> int collectItems(final int termNum, final LocalDate date,
            final LocalDateTime since, final String dataType, final String collectedLabel,
            final String noItemsLabel, final Supplier<List<T>> fetcher,
            final Function<T, String> itemKeyExtractor, final Function<T, String> titleExtractor) {
        var items = fetcher.get();
        if (items == null) {
            LOGGER.fine(buildNoItemsMessage(termNum, noItemsLabel, since, date));
            return 0;
        }

        int count = 0;
        for (var item : items) {
            if (item != null) {
                count += repository.upsertItem(date, dataType, itemKeyExtractor.apply(item),
                        titleExtractor.apply(item), toJson(item));
            }
        }

        LOGGER.fine("Collected " + count + " " + collectedLabel + " for term " + termNum);
        return count;
    }

    private String buildNoItemsMessage(final int termNum, final String noItemsLabel,
            final LocalDateTime since, final LocalDate date) {
        if (since != null) {
            return "No " + noItemsLabel + " returned for term " + termNum + " since " + since;
        }
        return "No " + noItemsLabel + " returned for term " + termNum + " on " + date;
    }

    /**
     * Normalizes a collection date to the start of the day for APIs that filter by timestamp.
     */
    private LocalDateTime startOfDay(final LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIDNIGHT);
    }

    /**
     * Serializes an item to JSON string using the configured ObjectMapper.
     *
     * @param item the item to serialize
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    private String toJson(final Object item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize item to JSON: " + item, e);
            throw new IllegalStateException("Failed to serialize item to JSON", e);
        }
    }
}
