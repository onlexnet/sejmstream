package onlexnet.infra.starters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Locates the Azure Functions staging output produced by the Maven package phase. */
final class AzureFunctionsStagingSupport {

    static final Path STAGING_ROOT = Path.of("target", "azure-functions");

    private AzureFunctionsStagingSupport() {
    }

    static Path findSingleFunctionAppDirectory() {
        assertThat(STAGING_ROOT)
                .as("Azure Functions staging directory should exist after Maven package phase")
                .isDirectory();

        List<Path> appDirectories;
        try (var paths = Files.list(STAGING_ROOT)) {
            appDirectories = paths.filter(Files::isDirectory).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list Azure Functions staging directory", ex);
        }

        assertThat(appDirectories)
                .as("Expected at least one function app folder under target/azure-functions")
                .isNotEmpty();

        return appDirectories.stream()
                .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                .orElseThrow(() -> new IllegalStateException("No Azure Functions app directory found"));
    }
}
