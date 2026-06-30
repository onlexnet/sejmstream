package onlexnet.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class HexagonalBoundariesTest {

    @Test
    void appLayerMustNotDependOnAdaptersOrLegacyTelegramPackage() {
        JavaClasses classes = new ClassFileImporter().importPackages("onlexnet");

        noClasses()
                .that()
                .resideInAPackage("onlexnet.app..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("onlexnet.infra.adapters..", "onlexnet.sejmapi.telegram..")
                .check(classes);
    }
}
