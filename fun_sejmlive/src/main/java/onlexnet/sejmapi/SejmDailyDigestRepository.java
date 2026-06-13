package onlexnet.sejmapi;

import java.sql.Date;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repository for daily digest collection and publish logs.
 */
@Component
public class SejmDailyDigestRepository {

    private final JdbcTemplate jdbcTemplate;

    public SejmDailyDigestRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts or updates one collected digest item for a date and data type.
     *
     * @return number of affected rows
     */
    public int upsertItem(final java.time.LocalDate date, final String dataType,
            final String itemKey, final String title, final String itemJson) {
        var sql = """
                INSERT INTO sejm_daily_digest_item (
                    collection_date, data_type, item_key, item_title, item_json, collected_at
                ) VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (collection_date, data_type, item_key)
                DO UPDATE SET
                    item_title = EXCLUDED.item_title,
                    item_json = EXCLUDED.item_json,
                    collected_at = NOW()
                """;
        return this.jdbcTemplate.update(
                sql,
                Date.valueOf(date),
                dataType,
                itemKey,
                title,
                itemJson);
    }

    /**
     * Returns all collected digest rows for a specific date.
     */
    public List<Map<String, Object>> findByDate(final java.time.LocalDate date) {
        var sql = """
                SELECT collection_date, data_type, item_key, item_title, item_json, collected_at
                FROM sejm_daily_digest_item
                WHERE collection_date = ?
                ORDER BY data_type, item_key
                """;
        return this.jdbcTemplate.queryForList(sql, Date.valueOf(date));
    }

    /**
     * Returns collected digest rows for a date filtered by data type.
     */
    public List<Map<String, Object>> findByDateAndType(final java.time.LocalDate date,
            final String dataType) {
        var sql = """
                SELECT collection_date, data_type, item_key, item_title, item_json, collected_at
                FROM sejm_daily_digest_item
                WHERE collection_date = ? AND data_type = ?
                ORDER BY item_key
                """;
        return this.jdbcTemplate.queryForList(sql, Date.valueOf(date), dataType);
    }

    /**
     * Writes a publishing attempt log entry.
     *
     * @return number of affected rows
     */
    public int insertPublishLog(final java.time.LocalDate date, final String message,
            final boolean success, final String errorMsg) {
        var sql = """
                INSERT INTO sejm_publish_log (publish_date, published_at, post_message, success, error_message)
                VALUES (?, NOW(), ?, ?, ?)
                """;
        return this.jdbcTemplate.update(sql, Date.valueOf(date), message, success, errorMsg);
    }

    /**
     * Checks whether a successful publication already exists for the given date.
     */
    public boolean alreadyPublishedToday(final java.time.LocalDate date) {
        var sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM sejm_publish_log
                    WHERE publish_date = ? AND success = TRUE
                )
                """;
        var exists = this.jdbcTemplate.queryForObject(sql, Boolean.class, Date.valueOf(date));
        return Boolean.TRUE.equals(exists);
    }
}