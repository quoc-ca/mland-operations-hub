package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationModuleTest {

    @Test
    @DisplayName("T030: Verify Spring Modulith module definition and public named facade for Notification Management")
    void verifyNotificationModule() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");

        assertThat(modules.getModuleByName("notificationmanagement"))
                .as("notificationmanagement should be detected as a distinct named module under features")
                .isPresent();

        ApplicationModule notificationModule = modules.getModuleByName("notificationmanagement").get();

        assertThat(notificationModule.getNamedInterfaces().getByName("facade"))
                .as("notificationmanagement must expose a public named interface 'facade'")
                .isPresent();

        // Ensure no boundary violations originate from notificationmanagement
        modules.detectViolations()
                .filter(violation -> violation.getMessage().contains("notificationmanagement"))
                .throwIfPresent();
    }
}

