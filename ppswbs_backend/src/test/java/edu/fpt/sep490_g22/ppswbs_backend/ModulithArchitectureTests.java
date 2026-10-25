package edu.fpt.sep490_g22.ppswbs_backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithArchitectureTests {

    @Test
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(PpswbsBackendApplication.class);
        modules.forEach(System.out::println);
        modules.verify();

        assertThat(modules.getModuleByName("features.accountmanagement"))
            .as("accountmanagement should be detected as a distinct named module under features")
            .isPresent();
    }
}
