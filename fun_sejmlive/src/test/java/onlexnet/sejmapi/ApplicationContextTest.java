package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies that the Spring application context starts the same way Azure Functions starts it:
 * via {@link CustomFunctionInstanceInjector} calling {@code SpringApplication.run(Program.class)}.
 *
 * <p>All primary function beans (those created by Azure Functions through the injector) must
 * be resolvable from the context when all required infrastructure is provided.
 */
@SpringBootTest(classes = Program.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {

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

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        ensurePostgresStarted();
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> DB_USERNAME);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("DB_URL", () -> jdbcUrl);
        registry.add("DB_USERNAME", () -> DB_USERNAME);
        registry.add("DB_PASSWORD", () -> DB_PASSWORD);
        registry.add("FB_TOKEN", () -> "test-placeholder-token-for-context-startup-check");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void givenAllRequiredPropertiesConfigured_whenContextStartsLikeAzureFunctions_thenAllPrimaryFunctionBeansAreResolvable() {
        assertThat(this.applicationContext.getBean(SejmCollectFunctions.class))
                .as("SejmCollectFunctions must be available for Azure Functions invocation")
                .isNotNull();
        assertThat(this.applicationContext.getBean(FacebookPublishingFunctions.class))
                .as("FacebookPublishingFunctions must be available when FB_TOKEN is configured")
                .isNotNull();
        assertThat(this.applicationContext.getBean(EmptyTimerFunctions.class))
                .as("EmptyTimerFunctions must be available for scheduled Liquibase migration trigger")
                .isNotNull();
        assertThat(this.applicationContext.getBean(SejmApiHttpFunctions.class))
                .as("SejmApiHttpFunctions must be available for HTTP API access")
                .isNotNull();
        assertThat(this.applicationContext.getBean(ApiDocumentationFunctions.class))
                .as("ApiDocumentationFunctions must be available for OpenAPI docs endpoint")
                .isNotNull();
        assertThat(this.applicationContext.getBean(DemoDurableFunctions.class))
                .as("DemoDurableFunctions must be available for Durable Function orchestration")
                .isNotNull();
    }

    private static synchronized void ensurePostgresStarted() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return;
        }

        postgresContainerName = "application-context-integration-test-" + UUID.randomUUID();
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
        } catch (IOException ex) {
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
