package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class OtaAuditCorrelationServiceTest {

    private final OtaAuditCorrelationService service = new OtaAuditCorrelationService();

    @Test
    void generatesOpaqueUuidCorrelationReference() {
        String correlationReference = service.newCorrelationReference();

        assertThat(UUID.fromString(correlationReference).toString()).isEqualTo(correlationReference);
    }

    @Test
    void preservesOnlyCanonicalUuidCorrelationValues() {
        UUID value = UUID.randomUUID();

        assertThat(service.safeCorrelationReference(value.toString().toUpperCase()))
                .isEqualTo(value.toString());
        assertThat(UUID.fromString(service.safeCorrelationReference(" ")).toString())
                .isNotBlank();
        assertThat(service.safeCorrelationReference("Bearer secret-token-value"))
                .isNotEqualTo("Bearer secret-token-value");
    }

    @Test
    void auditLogContainsOnlyAllowlistedMetadata(CapturedOutput output) {
        String sensitiveCandidate = "raw-provider-payload contact@example.test card=4111111111111111";

        service.record(
                OtaAuditCorrelationService.IntegrationAction.INBOUND_EVENT_RECEIVED,
                OtaProvider.KLOOK,
                42L,
                sensitiveCandidate,
                OtaAuditCorrelationService.IntegrationOutcome.REJECTED
        );

        assertThat(output.getOut())
                .contains("INBOUND_EVENT_RECEIVED", "KLOOK", "mappingId=42", "outcome=REJECTED")
                .doesNotContain(sensitiveCandidate, "contact@example.test", "4111111111111111", "secret-token-value");
    }
}
