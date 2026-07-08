package onlexnet.infra.adapters.out;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishStatePort;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;

/**
 * JDBC adapter for daily digest collection and publish log persistence.
 */
@Component
public class DefaultSejmDailyDigestPersistence
    implements SejmDailyDigestPersistence, InterpellationPublishStatePort {

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

    @Override
    public boolean tryCreateQueuedRecord(
            final InterpellationPublishQueueMessage message,
            final LocalDate collectionDate) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, 'QUEUED', ?, ?, NOW(), NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'QUEUED',
                    domain_message_id = EXCLUDED.domain_message_id,
                    collection_date = EXCLUDED.collection_date,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    last_error = NULL,
                    updated_at = NOW()
                WHERE sejm_interpellation_publish_state.status = 'QUEUE_ENQUEUE_FAILED'
                """;
            var updated = this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                Date.valueOf(collectionDate),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()));
        return updated > 0;
    }

    @Override
    public boolean tryClaimForPublish(final InterpellationPublishQueueMessage message) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'PROCESSING', ?, ?, NOW(), NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'PROCESSING',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    last_error = NULL,
                    updated_at = NOW()
                WHERE sejm_interpellation_publish_state.status IN ('QUEUED', 'RETRY_SCHEDULED')
                """;
        var updated = this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()));
        return updated > 0;
    }

    @Override
    public boolean isPublished(final int termNum, final int interpellationNum) {
        var sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM sejm_interpellation_publish_state
                    WHERE term_num = ?
                      AND interpellation_num = ?
                      AND status = 'PUBLISHED'
                )
                """;
        var exists = this.jdbcTemplate.queryForObject(sql, Boolean.class, termNum, interpellationNum);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void markPublished(
            final InterpellationPublishQueueMessage message,
            final String facebookPostMessage) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    published_at,
                    facebook_post_message,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'PUBLISHED', ?, ?, NOW(), NOW(), ?, NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'PUBLISHED',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    published_at = NOW(),
                    facebook_post_message = EXCLUDED.facebook_post_message,
                    last_error = NULL,
                    updated_at = NOW()
                """;
        this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()),
                facebookPostMessage);
    }

    @Override
    public void markPublishConfirmationPending(
            final InterpellationPublishQueueMessage message,
            final String errorMessage,
            final String facebookPostMessage) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    facebook_post_message,
                    last_error,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'PUBLISH_CONFIRMATION_PENDING', ?, ?, NOW(), ?, ?, NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'PUBLISH_CONFIRMATION_PENDING',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    facebook_post_message = EXCLUDED.facebook_post_message,
                    last_error = EXCLUDED.last_error,
                    updated_at = NOW()
                """;
        this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()),
                facebookPostMessage,
                errorMessage);
    }

    @Override
    public void markRetryScheduled(final InterpellationPublishQueueMessage message, final String errorMessage) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    last_error,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'RETRY_SCHEDULED', ?, ?, NOW(), ?, NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'RETRY_SCHEDULED',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    last_error = EXCLUDED.last_error,
                    updated_at = NOW()
                """;
        this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()),
                errorMessage);
    }

    @Override
    public void markEnqueueFailed(final InterpellationPublishQueueMessage message, final String errorMessage) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    last_error,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'QUEUE_ENQUEUE_FAILED', ?, ?, NOW(), ?, NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'QUEUE_ENQUEUE_FAILED',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    last_error = EXCLUDED.last_error,
                    updated_at = NOW()
                """;
        this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()),
                errorMessage);
    }

    @Override
    public void markDeadLetter(final InterpellationPublishQueueMessage message, final String errorMessage) {
        var sql = """
                INSERT INTO sejm_interpellation_publish_state (
                    term_num,
                    interpellation_num,
                    domain_message_id,
                    collection_date,
                    interpellation_title,
                    status,
                    attempt,
                    first_queued_at,
                    last_attempt_at,
                    last_error,
                    updated_at
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, 'DEAD_LETTER', ?, ?, NOW(), ?, NOW())
                ON CONFLICT (term_num, interpellation_num)
                DO UPDATE SET
                    status = 'DEAD_LETTER',
                    domain_message_id = EXCLUDED.domain_message_id,
                    interpellation_title = EXCLUDED.interpellation_title,
                    attempt = EXCLUDED.attempt,
                    first_queued_at = LEAST(sejm_interpellation_publish_state.first_queued_at, EXCLUDED.first_queued_at),
                    last_attempt_at = NOW(),
                    last_error = EXCLUDED.last_error,
                    updated_at = NOW()
                """;
        this.jdbcTemplate.update(
                sql,
                message.termNum(),
                message.interpellationNum(),
                message.domainMessageId(),
                message.title(),
                message.attempt(),
                Timestamp.from(message.firstQueuedAt()),
                errorMessage);
    }
}