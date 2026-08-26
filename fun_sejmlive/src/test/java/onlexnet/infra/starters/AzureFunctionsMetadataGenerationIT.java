package onlexnet.infra.starters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Ensures every {@code @FunctionName}-annotated method has a matching generated {@code function.json}
 * with the correct entry point, and that no orphaned function folders are generated.
 */
class AzureFunctionsMetadataGenerationIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldMatchGeneratedFunctionMetadataWithAnnotationEntryPoints() throws IOException {
        var expectedEntryPointsByFunctionName = findExpectedEntryPointsByFunctionName();
        assertThat(expectedEntryPointsByFunctionName)
                .as("Expected at least one @FunctionName-annotated method on the classpath")
                .isNotEmpty();

        var appDirectory = AzureFunctionsStagingSupport.findSingleFunctionAppDirectory();
        var actualFunctionNames = findGeneratedFunctionNames(appDirectory);

        assertThat(actualFunctionNames)
                .as("Generated function folders under %s must match exactly the @FunctionName-annotated methods",
                        appDirectory)
                .containsExactlyInAnyOrderElementsOf(expectedEntryPointsByFunctionName.keySet());

        for (var entry : expectedEntryPointsByFunctionName.entrySet()) {
            var functionName = entry.getKey();
            var expectedEntryPoint = entry.getValue();
            var functionJson = appDirectory.resolve(functionName).resolve("function.json");

            var actualEntryPoint = OBJECT_MAPPER.readTree(functionJson.toFile()).get("entryPoint").asText();

            assertThat(actualEntryPoint)
                    .as("entryPoint in %s must match the @FunctionName-annotated method", functionJson)
                    .isEqualTo(expectedEntryPoint);
        }
    }

    private static Map<String, String> findExpectedEntryPointsByFunctionName() {
        JavaClasses classes = new ClassFileImporter().importPackages("onlexnet");
        Map<String, String> expectedEntryPointsByFunctionName = new HashMap<>();

        for (var javaClass : classes) {
            for (var javaMethod : javaClass.getMethods()) {
                if (!javaMethod.isAnnotatedWith(FunctionName.class)) {
                    continue;
                }

                var functionName = javaMethod.reflect().getAnnotation(FunctionName.class).value();
                var entryPoint = javaClass.reflect().getName() + "." + javaMethod.getName();
                expectedEntryPointsByFunctionName.put(functionName, entryPoint);
            }
        }

        return expectedEntryPointsByFunctionName;
    }

    private static List<String> findGeneratedFunctionNames(final Path appDirectory) throws IOException {
        try (var paths = Files.list(appDirectory)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("function.json")))
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }
}
