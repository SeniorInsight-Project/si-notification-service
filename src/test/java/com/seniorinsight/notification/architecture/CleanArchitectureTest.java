package com.seniorinsight.notification.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class CleanArchitectureTest {

    @Test
    void domainDoesNotDependOnOuterLayers() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.seniorinsight.notification");
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapter..", "..config..");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrConfiguration() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.seniorinsight.notification");
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..config..");
        rule.check(classes);
    }
}
