package onlexnet.app.ports.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Contract for persisting and retrieving daily Sejm digest data.
 */
public interface SejmDailyDigestPersistence {

    /**
     * Inserts or updates one collected digest item for a date and data type.
     *
     * @return number of affected rows
     */
    int upsertItem(LocalDate date, String dataType, String itemKey, String title, String itemJson);

    /**
     * Returns all collected digest rows for a specific date.
     */
    List<Map<String, Object>> findByDate(LocalDate date);

    /**
     * Returns collected digest rows for a date filtered by data type.
     */
    List<Map<String, Object>> findByDateAndType(LocalDate date, String dataType);

    /**
     * Writes a publishing attempt log entry.
     *
     * @return number of affected rows
     */
    int insertPublishLog(LocalDate date, @Nullable String message, boolean success, @Nullable String errorMsg);

    /**
     * Checks whether a successful publication already exists for the given date.
     */
    boolean alreadyPublishedToday(LocalDate date);
}