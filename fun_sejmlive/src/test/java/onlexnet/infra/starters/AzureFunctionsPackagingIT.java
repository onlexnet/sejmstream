package onlexnet.infra.starters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

/**
 * Ensures release packaging contains runtime classes required by Spring during startup in Azure Functions.
 */
class AzureFunctionsPackagingIT {

    private static final String JSPECIFY_CLASS_ENTRY = "org/jspecify/annotations/Nullable.class";

    @Test
    void givenAzureFunctionPackage_whenInspectingLibDirectory_thenContainsJSpecifyRuntimeClass() {
        var appDirectory = AzureFunctionsStagingSupport.findSingleFunctionAppDirectory();
        var libDirectory = appDirectory.resolve("lib");

        assertThat(libDirectory)
                .as("Azure Functions package must contain the lib directory")
                .isDirectory();

        var jarFiles = findJarFiles(libDirectory);
        assertThat(jarFiles)
                .as("Expected at least one dependency jar in Azure Functions lib directory")
                .isNotEmpty();

        var hasJSpecifyJarByName = jarFiles.stream()
                .map(path -> path.getFileName().toString())
                .anyMatch(name -> name.startsWith("jspecify-") && name.endsWith(".jar"));

        assertThat(hasJSpecifyJarByName)
                .as("Expected jspecify jar in Azure Functions package to prevent startup NoClassDefFoundError")
                .isTrue();

        var hasJSpecifyClass = jarFiles.stream()
                .anyMatch(path -> jarContainsClass(path, JSPECIFY_CLASS_ENTRY));

        assertThat(hasJSpecifyClass)
                .as("Expected %s to be packaged in at least one runtime jar", JSPECIFY_CLASS_ENTRY)
                .isTrue();
    }

    private static List<Path> findJarFiles(final Path libDirectory) {
        try (var paths = Files.list(libDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to inspect Azure Functions lib directory", ex);
        }
    }

    private static boolean jarContainsClass(final Path jarPath, final String classEntry) {
        try (var zipFile = new ZipFile(jarPath.toFile())) {
            return zipFile.getEntry(classEntry) != null;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to inspect jar file " + jarPath, ex);
        }
    }
}
