package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.OpenCancellationHandoffCommand;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkshopSettlementModuleTest {
    @Test
    void workshopSettlementIsRecognizedAsModule() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");
        assertThat(modules.getModuleByName("workshopsettlement")).isPresent();
    }

    @Test
    void boundaryTypesAreSafeRecords() {
        OpenCancellationHandoffCommand command = new OpenCancellationHandoffCommand(
                "SES-1", List.of("BK-1"), "FACILITY_ISSUE", "OP-1", "CORR-1");
        CancellationHandoffOutcome outcome = CancellationHandoffOutcome.accepted("CASE-1", "CORR-1");
        assertThat(command.affectedBookingReferences()).containsExactly("BK-1");
        assertThat(outcome.status()).isEqualTo(CancellationHandoffOutcome.Status.ACCEPTED);
    }
}
