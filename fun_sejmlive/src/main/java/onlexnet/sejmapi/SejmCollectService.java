package onlexnet.sejmapi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient;

/**
 * Service that collects Sejm activity data from the API and stores it in the daily digest table.
 * Provides methods to collect items from six different Sejm API categories and persist them
 * as JSON in the database. Each collect method is idempotent due to database upsert semantics.
 */
@Component
public class SejmCollectService {

    private static final Logger LOGGER = Logger.getLogger(SejmCollectService.class.getName());

    private final SejmApiClient sejmApiClient;
    private final SejmDailyDigestRepository repository;
    private final ObjectMapper objectMapper;

    public SejmCollectService(final SejmApiClient sejmApiClient,
            final SejmDailyDigestRepository repository,
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
            var items = sejmApiClient.fetchVotingsForDate(termNum, date);
            if (items == null) {
                LOGGER.fine("No votings returned for term " + termNum + " on " + date);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    var key = item.sitting() + "/" + item.votingNumber();
                    count += repository.upsertItem(date, "VOTING", key, item.topic(), toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " voting(s) for term " + termNum);
            return count;
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
            var items = sejmApiClient.fetchCommitteeSittingsForDate(termNum, date);
            if (items == null) {
                LOGGER.fine("No committee sittings returned for term " + termNum + " on " + date);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    var key = item.code() + "/" + item.num();
                    count += repository.upsertItem(date, "COMMITTEE_SITTING", key, item.agenda(),
                            toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " committee sitting(s) for term " + termNum);
            return count;
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
            var items = sejmApiClient.fetchPrintsModifiedSince(termNum, date);
            if (items == null) {
                LOGGER.fine("No prints returned for term " + termNum + " since " + date);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    count += repository.upsertItem(date, "PRINT", item.number(), item.title(),
                            toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " print(s) for term " + termNum);
            return count;
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
            var since = LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT);
            var items = sejmApiClient.fetchInterpellationsModifiedSince(termNum, since);
            if (items == null) {
                LOGGER.fine("No interpellations returned for term " + termNum + " since " + since);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    count += repository.upsertItem(date, "INTERPELLATION", String.valueOf(item.num()),
                            item.title(), toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " interpellation(s) for term " + termNum);
            return count;
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
            var since = LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT);
            var items = sejmApiClient.fetchWrittenQuestionsModifiedSince(termNum, since);
            if (items == null) {
                LOGGER.fine("No written questions returned for term " + termNum + " since " + since);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    count += repository.upsertItem(date, "WRITTEN_QUESTION",
                            String.valueOf(item.num()), item.title(), toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " written question(s) for term " + termNum);
            return count;
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
            var items = sejmApiClient.fetchBillsReceivedSince(termNum, date);
            if (items == null) {
                LOGGER.fine("No bills returned for term " + termNum + " since " + date);
                return 0;
            }
            int count = 0;
            for (var item : items) {
                if (item != null) {
                    count += repository.upsertItem(date, "BILL", item.number(), item.title(),
                            toJson(item));
                }
            }
            LOGGER.fine("Collected " + count + " bill(s) for term " + termNum);
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting bills for term " + termNum, e);
            throw new IllegalStateException("Failed to collect bills", e);
        }
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
