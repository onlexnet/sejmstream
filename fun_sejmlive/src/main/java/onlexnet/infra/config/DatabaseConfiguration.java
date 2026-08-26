package onlexnet.infra.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.integration.spring.SpringLiquibase;

@Configuration
public class DatabaseConfiguration {

    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        if (url.startsWith("@Microsoft.KeyVault")) {
            throw new IllegalStateException(
                    "spring.datasource.url contains an unresolved Key Vault reference. " +
                    "Ensure the Function App has a system-assigned managed identity and the Key Vault " +
                    "grants it 'get' permission on secrets.");
        }
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public SpringLiquibase springLiquibase(DataSource dataSource,
            @Value("${spring.datasource.url:}") String url) {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setShouldRun(!url.isBlank());
        return liquibase;
    }
}