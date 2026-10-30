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
}
