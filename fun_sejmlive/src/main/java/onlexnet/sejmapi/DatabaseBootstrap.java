package onlexnet.sejmapi;

import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseBootstrap {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseBootstrap(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() throws SQLException {
        final var result = this.jdbcTemplate.queryForObject("select 1", Integer.class);
        log.info("Database connection check passed: {}", result);
    }
}
