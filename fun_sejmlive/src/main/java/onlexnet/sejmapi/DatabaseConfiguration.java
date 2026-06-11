package onlexnet.sejmapi;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class DatabaseConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DB_URL")
    public DataSource dataSource(@Value("${spring.datasource.url:}") final String url,
            @Value("${spring.datasource.username:}") final String username,
            @Value("${spring.datasource.password:}") final String password) {
        final var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    @ConditionalOnProperty(name = "DB_URL")
    public JdbcTemplate jdbcTemplate(final DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "DB_URL")
    public SpringLiquibase springLiquibase(final DataSource dataSource) {
        final var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setShouldRun(true);
        return liquibase;
    }
}
