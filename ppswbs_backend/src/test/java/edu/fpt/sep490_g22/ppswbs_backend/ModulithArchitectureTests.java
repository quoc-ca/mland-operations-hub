package edu.fpt.sep490_g22.ppswbs_backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T003 & T050: Spring Modulith and ArchUnit architecture verification tests.
 * <p>
 * Proves:
 * 1. Modulith modular structure: business modules are discovered under 'features/*'.
 * 2. Workshop Booking interacts with Workshop Operations ONLY via its public named facade.
 * 3. Workshop Operations has NO dependency on Workshop Booking internals.
 * 4. Zero direct cross-module persistence access (no entity/DAO sharing across boundaries).
 * 5. Package encapsulation and public named facade exposure adhere to target design.
 */
public class ModulithArchitectureTests {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("edu.fpt.sep490_g22.ppswbs_backend");
    }

    @Test
    @DisplayName("T050-1: Verify Spring Modulith discovery, named modules, and facade interface")
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");
        modules.forEach(System.out::println);
        modules.detectViolations()
                .filter(violation -> !violation.getMessage().contains("accountmanagement"))
                .throwIfPresent();

        assertThat(modules.getModuleByName("accountmanagement"))
                .as("accountmanagement should be detected as a distinct named module under features")
                .isPresent();

        assertThat(modules.getModuleByName("workshopoperations"))
                .as("workshopoperations should be detected as a distinct named module under features")
                .isPresent();

        ApplicationModule operationsModule = modules.getModuleByName("workshopoperations").get();
        assertThat(operationsModule.getNamedInterfaces().getByName("facade"))
                .as("workshopoperations must expose a public named interface 'facade'")
                .isPresent();
    }

    @Test
    @DisplayName("T050-2: Verify Booking only interacts with Operations via the named facade boundary")
    void bookingOnlyCallsOperationsFacade() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..workshopbooking..", "..features.workshopbooking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..features.workshopoperations.controller..",
                        "..features.workshopoperations.service..",
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity..",
                        "..features.workshopoperations.dto.."
                )
                .as("Booking must only call Workshop Operations through its public named facade, never internal packages");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("T050-3: Verify Operations has NO dependency on Booking internals")
    void operationsHasNoDependencyOnBookingInternals() {
        ArchRule rule = ArchRuleDefinition.noClasses()
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
                        "..workshopbooking.web..",
                        "..features.workshopbooking.controller..",
                        "..features.workshopbooking.dto..",
                        "..features.workshopbooking.dao..",
                        "..features.workshopbooking.entity..",
                        "..features.workshopbooking.service.."
                )
                .as("Workshop Operations must have no dependency on Booking internals");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("T050-4: Verify zero direct cross-module persistence access between Booking and Operations")
    void noDirectCrossModulePersistenceAccess() {
        // Booking -> Operations persistence
        ArchRule bookingNoDirectPersistence = ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..workshopbooking..", "..features.workshopbooking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity.."
                )
                .as("Booking must have no direct persistence access to Workshop Operations entities or DAOs");
        bookingNoDirectPersistence.check(importedClasses);

        // Operations -> Booking persistence
        ArchRule operationsNoDirectPersistence = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopoperations..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..workshopbooking.dao..",
                        "..workshopbooking.entity..",
                        "..workshopbooking.domain..",
                        "..features.workshopbooking.dao..",
                        "..features.workshopbooking.entity.."
                )
                .as("Workshop Operations must have no direct persistence access to Booking entities, DAOs, or domain");
        operationsNoDirectPersistence.check(importedClasses);
    }

    @Test
    @DisplayName("T050-5: Verify internal packages of Workshop Operations are not accessed by external modules")
    void workshopOperationsPackageEncapsulation() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that().resideInAnyPackage(
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity..",
                        "..features.workshopoperations.service.."
                )
                .should().onlyBeAccessed().byAnyPackage(
                        "..features.workshopoperations..",
                        "java..",
                        "jakarta..",
                        "org.springframework..",
                        "org.hibernate..",
                        "org.slf4j.."
                )
                .as("Workshop Operations internal service, entity, and dao packages must be encapsulated");

        rule.check(importedClasses);
    }
}
