package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class OtaConnectivityModuleTest {

    @Test
    void otaConnectivityIsRecognizedAsModule() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");

        assertThat(modules.getModuleByName("otaconnectivity")).isPresent();
    }

    @Test
    void crossModuleDependenciesUseOnlyNamedFacadeTypes() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");
        ApplicationModule otaModule = modules.getModuleByName("otaconnectivity").orElseThrow();

        assertThat(otaModule.getDependencies(modules).stream())
                .allSatisfy(dependency -> {
                    ApplicationModule targetModule = dependency.getTargetModule();
                    assertThat(targetModule.getNamedInterfaces().getByName("facade"))
                            .as("OTA's dependency on %s must be through its facade", targetModule.getName())
                            .hasValueSatisfying(namedInterface ->
                                    assertThat(namedInterface.contains(dependency.getTargetType())).isTrue());
                });

        assertThat(modules.stream()
                .flatMap(sourceModule -> sourceModule.getDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(otaModule)))
                .allSatisfy(dependency -> {
                    assertThat(otaModule.getNamedInterfaces().getByName("facade"))
                            .as("Consumers may access OTA only through its facade")
                            .hasValueSatisfying(namedInterface ->
                                    assertThat(namedInterface.contains(dependency.getTargetType())).isTrue());
                });
    }
}
