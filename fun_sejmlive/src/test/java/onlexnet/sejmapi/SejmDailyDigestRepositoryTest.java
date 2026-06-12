package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import liquibase.integration.spring.SpringLiquibase;

@Testcontainers
@SpringBootTest(classes = { DatabaseConfiguration.class, SejmDailyDigestRepository.class },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SejmDailyDigestRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sejmstream")
            .withUsername("sejmstream")
            .withPassword("sejmstream");

    @Autowired
    private SejmDailyDigestRepository repository;

    @Autowired
    @SuppressWarnings("unused")
    private SpringLiquibase springLiquibase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM sejm_publish_log");
        jdbcTemplate.update("DELETE FROM sejm_daily_digest_item");
    }

    @Test
    void upsertItemInsertsRow() {
        LocalDate date = LocalDate.of(2026, 6, 12);

        int affected = repository.upsertItem(date, "VOTING", "3/17", "Vote topic", "{\"k\":\"v\"}");

        assertThat(affected).isEqualTo(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT item_title FROM sejm_daily_digest_item WHERE collection_date = ? AND item_key = ?",
                date, "3/17");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("item_title")).isEqualTo("Vote topic");
    }

    @Test
    void upsertItemUpdatesTitleOnConflict() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        repository.upsertItem(date, "VOTING", "3/17", "Original title", "{\"a\":\"1\"}");

        repository.upsertItem(date, "VOTING", "3/17", "Updated title", "{\"a\":\"2\"}");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT item_title, item_json FROM sejm_daily_digest_item WHERE collection_date = ? AND item_key = ?",
                date, "3/17");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("item_title")).isEqualTo("Updated title");
        assertThat(rows.get(0).get("item_json")).isEqualTo("{\"a\":\"2\"}");
    }

    @Test
    void insertPublishLogPersistsSuccessState() {
        LocalDate date = LocalDate.of(2026, 6, 12);

        int affected = repository.insertPublishLog(date, "summary text", true, null);

        assertThat(affected).isEqualTo(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT post_message, success, error_message FROM sejm_publish_log WHERE publish_date = ?",
                date);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("post_message")).isEqualTo("summary text");
        assertThat(rows.get(0).get("success")).isEqualTo(true);
        assertThat(rows.get(0).get("error_message")).isNull();
    }

    @Test
    void insertPublishLogPersistsFailureState() {
        LocalDate date = LocalDate.of(2026, 6, 12);

        repository.insertPublishLog(date, null, false, "network error");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT success, error_message FROM sejm_publish_log WHERE publish_date = ?",
                date);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("success")).isEqualTo(false);
        assertThat(rows.get(0).get("error_message")).isEqualTo("network error");
    }

    @Test
    void alreadyPublishedTodayReturnsTrueWhenSuccessLogExists() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        repository.insertPublishLog(date, "msg", true, null);

        assertThat(repository.alreadyPublishedToday(date)).isTrue();
    }

    @Test
    void alreadyPublishedTodayReturnsFalseWhenNoLog() {
        LocalDate date = LocalDate.of(2026, 6, 12);

        assertThat(repository.alreadyPublishedToday(date)).isFalse();
    }

    @Test
    void alreadyPublishedTodayReturnsFalseWhenOnlyFailureLogExists() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        repository.insertPublishLog(date, "msg", false, "some error");

        assertThat(repository.alreadyPublishedToday(date)).isFalse();
    }
}

