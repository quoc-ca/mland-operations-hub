package edu.fpt.sep490_g22.ppswbs_backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithArchitectureTests {

    @Test
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");
        modules.forEach(System.out::println);
        modules.verify();

        assertThat(modules.getModuleByName("accountmanagement"))
            .as("accountmanagement should be detected as a distinct named module under features")
            .isPresent();
        assertThat(modules.getModuleByName("workshopoperations"))
            .as("workshopoperations should be detected as a distinct named module under features")
            .isPresent();
    }

    @Test
    void operationsHasNoDependencyOnBookingInternals() {
        com.tngtech.archunit.core.domain.JavaClasses classes = new com.tngtech.archunit.core.importer.ClassFileImporter()
            .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.fpt.sep490_g22.ppswbs_backend");

        com.tngtech.archunit.lang.ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage("..features.workshopoperations..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..workshopbooking.controller..",
                "..workshopbooking.dto..",
                "..workshopbooking.dao..",
                "..workshopbooking.entity..",
                "..workshopbooking.service..",
                "..workshopbooking.domain..",
                "..workshopbooking.infrastructure..",
                "..workshopbooking.application..",
                "..workshopbooking.web.."
            )
            .as("Workshop Operations must have no dependency on Booking internals");

        rule.check(classes);
    }
}
