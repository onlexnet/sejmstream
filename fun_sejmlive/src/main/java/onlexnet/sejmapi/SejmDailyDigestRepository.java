package onlexnet.sejmapi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SejmDailyDigestRepository {

    private final JdbcTemplate jdbcTemplate;

    public SejmDailyDigestRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertItem(final LocalDate collectionDate,
            final String dataType,
            final String itemKey,
                        final String itemTitle,
            final String payloadJson) {

        return this.jdbcTemplate.update(
                """
                                                INSERT INTO sejm_daily_digest_item (
                                                        collection_date,
                                                        data_type,
                                                        item_key,
                                                        item_title,
                                                        item_json,
                                                        collected_at
                                                )
                                                VALUES (?, ?, ?, ?, ?, NOW())
                        ON CONFLICT (collection_date, data_type, item_key)
                        DO UPDATE SET
                                                        item_title = EXCLUDED.item_title,
                            item_json = EXCLUDED.item_json,
                            collected_at = NOW()
                        """,
                collectionDate,
                dataType,
                itemKey,
                                itemTitle,
                payloadJson);
    }

    public List<Map<String, Object>> findByDate(final LocalDate date) {
        return this.jdbcTemplate.queryForList(
                """
                        SELECT id, collection_date, data_type, item_key, item_title, item_json, collected_at
                        FROM sejm_daily_digest_item
                        WHERE collection_date = ?
                        ORDER BY data_type, item_key
                        """,
                date);
    }

    public List<Map<String, Object>> findByDateAndType(final LocalDate date, final String dataType) {
        return this.jdbcTemplate.queryForList(
                """
                        SELECT id, collection_date, data_type, item_key, item_title, item_json, collected_at
                        FROM sejm_daily_digest_item
                        WHERE collection_date = ? AND data_type = ?
                        ORDER BY item_key
                        """,
                date,
                dataType);
    }

    public int insertPublishLog(final LocalDate date,
            final String message,
            final boolean success,
            final String errorMsg) {

        return this.jdbcTemplate.update(
                """
                        INSERT INTO sejm_publish_log (publish_date, published_at, post_message, success, error_message)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                date,
                LocalDateTime.now(),
                message,
                success,
                errorMsg);
    }

    public boolean alreadyPublishedToday(final LocalDate date) {
        final Boolean exists = this.jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS(
                            SELECT 1
                            FROM sejm_publish_log
                            WHERE publish_date = ? AND success = TRUE
                        )
                        """,
                Boolean.class,
                date);
        return Boolean.TRUE.equals(exists);
    }
}
