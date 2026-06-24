package onlexnet.sejmapi;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseBootstrap {

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public DatabaseBootstrap(final JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url:}") final String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void init() throws SQLException {
        if (this.datasourceUrl.isBlank()) {
            log.info("Database not configured (spring.datasource.url is empty), skipping connectivity check.");
            return;
        }
        final var result = this.jdbcTemplate.queryForObject("select 1", Integer.class);
        log.info("Database connection check passed: {}", result);
    }
}
