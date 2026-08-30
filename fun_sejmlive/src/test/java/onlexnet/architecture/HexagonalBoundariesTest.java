package onlexnet.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NullMarked;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class HexagonalBoundariesTest {

    @Test
    void appLayerMustNotDependOnAdapters() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("onlexnet");

        noClasses()
                .that()
                .resideInAPackage("onlexnet.app..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("onlexnet.infra.adapters..")
                .check(classes);
    }

            @Test
            void appPackagesMustBeNullMarked() {
                JavaClasses classes = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("onlexnet");

            var packageInfoByPackage = classes.stream()
                .filter(javaClass -> javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.toMap(
                    javaClass -> javaClass.getPackageName(),
                    Function.identity(),
                    (first, second) -> first));

            var appPackages = classes.stream()
                .map(javaClass -> javaClass.getPackageName())
                .filter(packageName -> packageName.startsWith("onlexnet"))
                .filter(packageName -> !packageName.contains(".generated."))
                .collect(Collectors.toCollection(java.util.TreeSet::new));

            assertThat(appPackages)
                .as("Expected at least one application package under onlexnet")
                .isNotEmpty();

            for (var packageName : appPackages) {
                var packageInfoClass = packageInfoByPackage.get(packageName);

                assertThat(packageInfoClass)
                    .as("Package %s must declare package-info.java", packageName)
                    .isNotNull();

                assertThat(packageInfoClass.isAnnotatedWith(NullMarked.class))
                    .as("Package %s must be annotated with @NullMarked", packageName)
                    .isTrue();
            }
            }
}
