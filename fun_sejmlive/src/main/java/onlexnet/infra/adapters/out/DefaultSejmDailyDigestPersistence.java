package onlexnet.infra.adapters.out;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.out.SejmDailyDigestPersistence;

/**
 * JDBC adapter for daily digest collection and publish log persistence.
 */
@Component
public class DefaultSejmDailyDigestPersistence implements SejmDailyDigestPersistence {

    private final JdbcTemplate jdbcTemplate;

    public DefaultSejmDailyDigestPersistence(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int upsertItem(final LocalDate date, final String dataType,
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

    @Override
    public List<Map<String, Object>> findByDate(final LocalDate date) {
        var sql = """
                SELECT collection_date, data_type, item_key, item_title, item_json, collected_at
                FROM sejm_daily_digest_item
                WHERE collection_date = ?
                ORDER BY data_type, item_key
                """;
        return this.jdbcTemplate.queryForList(sql, Date.valueOf(date));
    }

    @Override
    public List<Map<String, Object>> findByDateAndType(final LocalDate date,
            final String dataType) {
        var sql = """
                SELECT collection_date, data_type, item_key, item_title, item_json, collected_at
                FROM sejm_daily_digest_item
                WHERE collection_date = ? AND data_type = ?
                ORDER BY item_key
                """;
        return this.jdbcTemplate.queryForList(sql, Date.valueOf(date), dataType);
    }

    @Override
        public int insertPublishLog(final LocalDate date, final @Nullable String message,
            final boolean success, final @Nullable String errorMsg) {
        var sql = """
                INSERT INTO sejm_publish_log (publish_date, published_at, post_message, success, error_message)
                VALUES (?, NOW(), ?, ?, ?)
                """;
        return this.jdbcTemplate.update(sql, Date.valueOf(date), message, success, errorMsg);
    }

    @Override
    public boolean alreadyPublishedToday(final LocalDate date) {
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