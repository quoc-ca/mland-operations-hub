package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class OtaConnectivityModuleTest {

    @Test
    void otaConnectivityIsRecognizedAsModule() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");

        assertThat(modules.getModuleByName("otaconnectivity")).isPresent();
    }
}
