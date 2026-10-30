package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SettlementDtoValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void guestProofRequiresExactlyOneContact() {
        assertThat(validator.validate(new GuestContactProofDto("a@b.test", "0123"))).isNotEmpty();
        assertThat(validator.validate(new GuestContactProofDto("a@b.test", null))).isEmpty();
        assertThat(validator.validate(new GuestContactProofDto(null, "0123"))).isEmpty();
    }

    @Test void refundPercentAndVariableChargeAreBounded() {
        assertThat(validator.validate(new RefundPolicyRevisionCommandDto(Instant.now(), 101))).isNotEmpty();
        assertThat(validator.validate(new RefundPolicyRevisionCommandDto(Instant.now(), 50))).isEmpty();
        assertThat(validator.validate(new VariableChargeCommandDto("extra", 0L, "reason"))).isNotEmpty();
    }

    @Test void finalBillCommandsEnforceCustomerAndManagerShapes() {
        assertThat(validator.validate(new ChargeCommandDto("addon-ref", null, null, null, null, null))).isEmpty();
        assertThat(validator.validate(new ChargeCommandDto("addon-ref", "guest@example.test", null, null, null, null))).isEmpty();
        assertThat(validator.validate(new ChargeCommandDto("addon-ref", "guest@example.test", "0123", null, null, null))).isNotEmpty();
        assertThat(validator.validate(new ChargeCommandDto(null, null, null, "Extra", 100L, "Reason"))).isEmpty();
        assertThat(validator.validate(new ChargeCommandDto(null, null, null, "", 100L, "Reason"))).isNotEmpty();
        assertThat(validator.validate(new FinalizationCommandDto(false, null))).isEmpty();
        assertThat(validator.validate(new FinalizationCommandDto(true, "approved exception"))).isEmpty();
        assertThat(validator.validate(new FinalizationCommandDto(true, " "))).isNotEmpty();
        assertThat(validator.validate(new FinalizationCommandDto(false, "unexpected reason"))).isNotEmpty();
    }
}
