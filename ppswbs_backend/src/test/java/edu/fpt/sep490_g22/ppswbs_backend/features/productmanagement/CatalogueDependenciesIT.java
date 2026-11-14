package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * T082 (part): productmanagement reaches other modules only through their facades — the 001 identity facade
 * (current actor) and the FE-06 target port — and never through their services, entities, repositories or
 * controllers. Retail (FE-08) depends on productmanagement, never the reverse. The real-adapter part of T082
 * (FE-08 hold source) is still missing; its absence is a typed 503, covered by CatalogueContentEditIT.
 */
class CatalogueDependenciesIT {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.fpt.sep490_g22.ppswbs_backend");

    @Test
    void identityIsReachedOnlyThroughThe001Facade() {
        noClasses().that().resideInAPackage("..features.productmanagement..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.accountmanagement.service..", "..features.accountmanagement.entity..",
                        "..features.accountmanagement.dao..", "..features.accountmanagement.repository..",
                        "..features.accountmanagement.controller..", "..features.accountmanagement.dto..")
                .because("002 consumes only the actor of accountmanagement::facade (T007)")
                .check(PRODUCTION);
    }

    @Test
    void loyaltyIsReachedOnlyThroughItsFacade() {
        noClasses().that().resideInAPackage("..features.productmanagement..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.loyaltypromotion.service..", "..features.loyaltypromotion.entity..",
                        "..features.loyaltypromotion.dao..", "..features.loyaltypromotion.controller..",
                        "..features.loyaltypromotion.dto..")
                .because("the FE-06 target provider implements loyaltypromotion::facade only (T091)")
                .check(PRODUCTION);
    }

    @Test
    void noOtherModuleReachesIntoCatalogueInternals() {
        noClasses().that().resideOutsideOfPackage("..features.productmanagement..")
                .and().resideOutsideOfPackage("..configuration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.productmanagement.service..", "..features.productmanagement.entity..",
                        "..features.productmanagement.dao..", "..features.productmanagement.controller..",
                        "..features.productmanagement.dto..")
                .because("other features may only use productmanagement::facade")
                .check(PRODUCTION);
    }
}
