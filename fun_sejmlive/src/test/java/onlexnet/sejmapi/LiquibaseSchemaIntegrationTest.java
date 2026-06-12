package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = DatabaseConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LiquibaseSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:17-alpine")
            .withDatabaseName("sejmstream")
            .withUsername("sejmstream")
            .withPassword("sejmstream");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void givenLiquibaseConfiguration_whenContextStarts_thenPhaseTwoTablesExist() {
        var tableNames = this.jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('sejm_daily_digest_item', 'sejm_publish_log')
                ORDER BY table_name
                """, String.class);

        assertThat(tableNames).containsExactly(
                "sejm_daily_digest_item",
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
                        tuple("item_title", "character varying", true, 1000, null),
                        tuple("item_json", "text", true, null, null),
                        tuple("collected_at", "timestamp without time zone", false, null, "now()"));
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
}