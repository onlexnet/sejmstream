package onlexnet.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.usecases.SejmDigestService;
import onlexnet.infra.adapters.out.DefaultSejmDailyDigestPersistence;
import liquibase.integration.spring.SpringLiquibase;
import onlexnet.testsupport.AppTest;

@AppTest(classes = DatabaseConfiguration.class)
class LiquibaseSchemaIntegrationTest {

    private static final String DB_NAME = "sejmstream";
    private static final String DB_USERNAME = "sejmstream";
    private static final String DB_PASSWORD = "sejmstream";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private static String postgresContainerName;
    private static String jdbcUrl;

    @AfterAll
    static void stopPostgresContainer() {
        if (postgresContainerName != null && !postgresContainerName.isBlank()) {
            try {
                runCommand("docker", "stop", postgresContainerName);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        @Autowired
        @SuppressWarnings("unused")
        private SpringLiquibase springLiquibase;

    @BeforeEach
    void clearDigestTables() {
        this.jdbcTemplate.update("TRUNCATE TABLE sejm_daily_digest_item RESTART IDENTITY CASCADE");
        this.jdbcTemplate.update("TRUNCATE TABLE sejm_publish_log RESTART IDENTITY CASCADE");
    }

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
                ensurePostgresStarted();
                registry.add("DB_URL", () -> jdbcUrl);
                registry.add("DB_USERNAME", () -> DB_USERNAME);
                registry.add("DB_PASSWORD", () -> DB_PASSWORD);
                registry.add("spring.datasource.url", () -> jdbcUrl);
                registry.add("spring.datasource.username", () -> DB_USERNAME);
                registry.add("spring.datasource.password", () -> DB_PASSWORD);
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenPhaseTwoTablesExist() {
        var tableNames = this.jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                                    AND table_name IN (
                                            'sejm_daily_digest_item',
                                            'sejm_publish_log',
                                            'sejm_interpellation_publish_state')
                ORDER BY table_name
                """, String.class);

        assertThat(tableNames).containsExactly(
                "sejm_daily_digest_item",
                                "sejm_interpellation_publish_state",
                "sejm_publish_log");
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenPhaseTwoUniqueConstraintExists() {
        var constraintName = this.jdbcTemplate.queryForObject("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = con.connamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = 'sejm_daily_digest_item'
                  AND con.contype = 'u'
                  AND pg_get_constraintdef(con.oid) =
                      'UNIQUE (collection_date, data_type, item_key)'
                """, String.class);

        assertThat(constraintName)
                .isEqualTo("uk_sejm_daily_digest_item_collection_type_key");
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenCollectionDateIndexExists() {
        var indexDefinition = this.jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'sejm_daily_digest_item'
                  AND indexname = 'idx_sejm_daily_digest_item_collection_date'
                """, String.class);

        assertThat(indexDefinition)
                .contains("CREATE INDEX idx_sejm_daily_digest_item_collection_date")
                .contains("(collection_date)");
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenDailyDigestItemColumnsMatchPhaseTwoSchema() {
        var columns = findColumnsFor("sejm_daily_digest_item");

        assertThat(columns)
                .extracting(ColumnDefinition::name,
                        ColumnDefinition::dataType,
                        ColumnDefinition::nullable,
                        ColumnDefinition::maxLength,
                        ColumnDefinition::defaultValue)
                .containsExactly(
                        tuple("id", "bigint", false, null, null),
                        tuple("collection_date", "date", false, null, null),
                        tuple("data_type", "character varying", false, 50, null),
                        tuple("item_key", "character varying", false, 255, null),
                    tuple("item_title", "text", true, null, null),
                    tuple("item_json", "jsonb", true, null, null),
                        tuple("collected_at", "timestamp without time zone", false, null, "now()"));
    }

            @Test
            void givenLongTitleAndJsonPayload_whenUpsertAndRead_thenBothValuesAreStored() throws Exception {
            var persistence = createPersistence();
            var collectionDate = LocalDate.of(2026, 7, 16);
            var dataType = "PRINT";
            var itemKey = "print-9001";
            var longTitle = "Tytul ".repeat(400);
            var itemJson = """
                {"number":"9001","title":"Projekt testowy","metadata":{"source":"it","version":1}}
                """;

            var affectedRows = persistence.upsertItem(collectionDate, dataType, itemKey, longTitle, itemJson);

            assertThat(affectedRows).isEqualTo(1);
            var rows = persistence.findByDateAndType(collectionDate, dataType);
            assertThat(rows).hasSize(1);

            var storedRow = rows.getFirst();
            assertThat(storedRow.get("item_title")).isEqualTo(longTitle);
            assertThat(storedRow.get("item_json").getClass().getName()).isEqualTo("org.postgresql.util.PGobject");
            assertThat(this.objectMapper.readTree(extractJsonValue(storedRow.get("item_json"))))
                .isEqualTo(this.objectMapper.readTree(itemJson));
            }

            @Test
            void givenConflictOnNaturalKey_whenUpsertCalledTwice_thenLatestTitleAndJsonAreSaved() throws Exception {
            var persistence = createPersistence();
            var collectionDate = LocalDate.of(2026, 7, 16);
            var firstJson = "{" + "\"topic\":\"Pierwsza wersja\",\"yes\":100}";
            var secondJson = "{" + "\"topic\":\"Druga wersja\",\"yes\":101}";

            persistence.upsertItem(collectionDate, "VOTING", "1/10", "Tytul 1", firstJson);
            persistence.upsertItem(collectionDate, "VOTING", "1/10", "Tytul 2", secondJson);

            var rows = persistence.findByDateAndType(collectionDate, "VOTING");
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().get("item_title")).isEqualTo("Tytul 2");
            assertThat(this.objectMapper.readTree(extractJsonValue(rows.getFirst().get("item_json"))))
                .isEqualTo(this.objectMapper.readTree(secondJson));

            var count = this.jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sejm_daily_digest_item
                WHERE collection_date = ?
                  AND data_type = ?
                  AND item_key = ?
                """, Integer.class, Date.valueOf(collectionDate), "VOTING", "1/10");
            assertThat(count).isEqualTo(1);
            }

            @Test
            void givenJsonbStoredItem_whenDigestIsBuilt_thenServiceReadsDriverSpecificJsonValue() throws Exception {
            var persistence = createPersistence();
            var collectionDate = LocalDate.of(2026, 7, 16);
            var votingJson = this.objectMapper.writeValueAsString(new VotingItem(
                LocalDateTime.of(2026, 7, 16, 10, 0),
                7,
                42,
                "Temat jsonb",
                200,
                100,
                10,
                310,
                0));
            persistence.upsertItem(collectionDate, "VOTING", "7/42", "Temat jsonb", votingJson);

            var digestService = new SejmDigestService(persistence, this.objectMapper);
            var digest = digestService.buildDigest(collectionDate);

            assertThat(digest).isPresent();
            assertThat(digest.orElseThrow())
                .contains("📊 GŁOSOWANIA (1):")
                .contains("Temat jsonb");
            }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenPublishLogColumnsMatchPhaseTwoSchema() {
        var columns = findColumnsFor("sejm_publish_log");

        assertThat(columns)
                .extracting(ColumnDefinition::name,
                        ColumnDefinition::dataType,
                        ColumnDefinition::nullable,
                        ColumnDefinition::maxLength,
                        ColumnDefinition::defaultValue)
                .containsExactly(
                        tuple("id", "bigint", false, null, null),
                        tuple("publish_date", "date", false, null, null),
                        tuple("published_at", "timestamp without time zone", false, null, null),
                        tuple("post_message", "text", true, null, null),
                        tuple("success", "boolean", false, null, "false"),
                        tuple("error_message", "character varying", true, 1000, null));
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenDailyDigestItemDataTypeCheckConstraintExists() {
        var constraintDefinition = this.jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(con.oid)
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = con.connamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = 'sejm_daily_digest_item'
                  AND con.conname = 'chk_sejm_daily_digest_item_data_type'
                """, String.class);

        assertThat(constraintDefinition)
                .contains("CHECK")
                .contains("data_type")
                .contains("VOTING")
                .contains("COMMITTEE_SITTING")
                .contains("PRINT")
                .contains("INTERPELLATION")
                .contains("WRITTEN_QUESTION")
                .contains("BILL");
    }

            @Test
            void givenLiquibaseConfiguration_whenContextStarts_thenInterpellationPublishStateColumnsMatchSchema() {
            var columns = findColumnsFor("sejm_interpellation_publish_state");

            assertThat(columns)
                .extracting(ColumnDefinition::name,
                    ColumnDefinition::dataType,
                    ColumnDefinition::nullable,
                    ColumnDefinition::maxLength,
                    ColumnDefinition::defaultValue)
                .containsExactly(
                    tuple("id", "bigint", false, null, null),
                    tuple("term_num", "integer", false, null, null),
                    tuple("interpellation_num", "integer", false, null, null),
                    tuple("domain_message_id", "character varying", false, 140, null),
                    tuple("collection_date", "date", false, null, null),
                    tuple("interpellation_title", "character varying", true, 1000, null),
                    tuple("status", "character varying", false, 30, null),
                    tuple("attempt", "integer", false, null, null),
                    tuple("first_queued_at", "timestamp without time zone", false, null, null),
                    tuple("last_attempt_at", "timestamp without time zone", true, null, null),
                    tuple("published_at", "timestamp without time zone", true, null, null),
                    tuple("facebook_post_message", "text", true, null, null),
                    tuple("last_error", "character varying", true, 1000, null),
                    tuple("created_at", "timestamp without time zone", false, null, "now()"),
                    tuple("updated_at", "timestamp without time zone", false, null, "now()"),
                    tuple("last_known_reply_count", "integer", false, null, "0"),
                    tuple("reply_notification_published_at", "timestamp without time zone", true, null, null));
            }

            @Test
            void givenLiquibaseConfiguration_whenContextStarts_thenInterpellationStateUniqueConstraintsExist() {
            var constraints = this.jdbcTemplate.queryForList("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = con.connamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = 'sejm_interpellation_publish_state'
                  AND con.contype = 'u'
                ORDER BY con.conname
                """, String.class);

            assertThat(constraints).containsExactly(
                "uk_interpellation_publish_state_message_id",
                "uk_interpellation_publish_state_term_interpellation");
            }

            @Test
            void givenLiquibaseConfiguration_whenContextStarts_thenInterpellationStateStatusConstraintExists() {
            var constraintDefinition = this.jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(con.oid)
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = con.connamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = 'sejm_interpellation_publish_state'
                  AND con.conname = 'chk_interpellation_publish_state_status'
                """, String.class);

            assertThat(constraintDefinition)
                .contains("CHECK")
                .contains("QUEUED")
                .contains("PROCESSING")
                .contains("RETRY_SCHEDULED")
                .contains("PUBLISHED")
                .contains("DEAD_LETTER")
                .contains("PUBLISH_CONFIRMATION_PENDING")
                .contains("QUEUE_ENQUEUE_FAILED");
            }

    private java.util.List<ColumnDefinition> findColumnsFor(final String tableName) {
        return this.jdbcTemplate.query("""
                SELECT column_name,
                       data_type,
                       is_nullable,
                       character_maximum_length,
                       pg_get_expr(ad.adbin, ad.adrelid) AS column_default
                FROM information_schema.columns cols
                LEFT JOIN pg_attribute attr
                    ON attr.attname = cols.column_name
                   AND attr.attrelid = (quote_ident(cols.table_schema) || '.' || quote_ident(cols.table_name))::regclass
                LEFT JOIN pg_attrdef ad
                    ON ad.adrelid = attr.attrelid
                   AND ad.adnum = attr.attnum
                WHERE cols.table_schema = 'public'
                  AND cols.table_name = ?
                ORDER BY cols.ordinal_position
                """, (rs, rowNum) -> new ColumnDefinition(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        "YES".equals(rs.getString("is_nullable")),
                        (Integer) rs.getObject("character_maximum_length"),
                        rs.getString("column_default")), tableName);
    }

    private record ColumnDefinition(String name, String dataType, boolean nullable, Integer maxLength,
            String defaultValue) {
    }

    private DefaultSejmDailyDigestPersistence createPersistence() {
        return new DefaultSejmDailyDigestPersistence(this.jdbcTemplate);
    }

    private static String extractJsonValue(final Object dbValue) {
        if (dbValue instanceof CharSequence sequence) {
            return sequence.toString();
        }
        try {
            var getValueMethod = dbValue.getClass().getMethod("getValue");
            var extracted = getValueMethod.invoke(dbValue);
            return extracted == null ? "" : String.valueOf(extracted);
        } catch (ReflectiveOperationException exception) {
            return String.valueOf(dbValue);
        }
    }

    private static synchronized void ensurePostgresStarted() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return;
        }

        postgresContainerName = "liquibase-schema-it-" + UUID.randomUUID();
        runCommand("docker", "run", "-d", "--rm",
                "--name", postgresContainerName,
                "-e", "POSTGRES_DB=" + DB_NAME,
                "-e", "POSTGRES_USER=" + DB_USERNAME,
                "-e", "POSTGRES_PASSWORD=" + DB_PASSWORD,
                "-P",
                "postgres:17-alpine");

        String mappedPort = waitForMappedPort();
        jdbcUrl = "jdbc:postgresql://localhost:" + mappedPort + "/" + DB_NAME;
        waitForDatabaseReady();
    }

    private static String waitForMappedPort() {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                String port = runCommand("docker", "inspect", "-f",
                        "{{(index (index .NetworkSettings.Ports \"5432/tcp\") 0).HostPort}}",
                        postgresContainerName);
                if (!port.isBlank()) {
                    return port;
                }
            } catch (RuntimeException ignored) {
                // Container may still be initializing.
            }
            sleepMillis(1000);
        }

        throw new IllegalStateException("Timed out waiting for mapped PostgreSQL port");
    }

    private static void waitForDatabaseReady() {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try (var connection = DriverManager.getConnection(jdbcUrl, DB_USERNAME, DB_PASSWORD)) {
                connection.isValid(1);
                return;
            } catch (Exception ignored) {
                sleepMillis(1000);
            }
        }

        throw new IllegalStateException("Timed out waiting for PostgreSQL to accept JDBC connections");
    }

    private static String runCommand(final String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Command failed (" + exitCode + "): "
                        + String.join(" ", command) + "\n" + output);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running command: " + String.join(" ", command), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to run command: " + String.join(" ", command), ex);
        }
    }

    private static void sleepMillis(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for PostgreSQL startup", ex);
        }
    }
}