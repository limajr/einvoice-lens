package io.github.einvoicelens.core;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CoreArchitectureTest {
    @Test
    void coreHasNoFrameworkOrPresentationDependencies() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.einvoicelens.core");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "com.vaadin..",
                "io.github.einvoicelens.app..", "io.github.einvoicelens.explain..")
                .check(classes);
    }
}
