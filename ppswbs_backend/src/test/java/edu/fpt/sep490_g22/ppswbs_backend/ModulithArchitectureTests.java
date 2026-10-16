package edu.fpt.sep490_g22.ppswbs_backend;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(
            PpswbsBackendApplication.class,
            JavaClass.Predicates.resideInAnyPackage(
                    "..configuration..",
                    "..common.."
            )
    );

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }
}
