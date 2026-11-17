package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplyVerifiedSettlementPaymentEvidenceCommandTest {
    @Test void acceptsRedactedFinalPaymentEvidence() {
        new ApplyVerifiedSettlementPaymentEvidenceCommand("BK-1", "E-1", "M-1",
                "FINAL_PAYMENT", 100_000L, "VND", "12345678", "20261030181359", "OP-1", "COR-1");
    }

    @Test void rejectsUnsupportedKindOrCurrency() {
        assertThatThrownBy(() -> new ApplyVerifiedSettlementPaymentEvidenceCommand("BK-1", "E-1", "M-1",
                "DEPOSIT", 100_000L, "VND", "OP-1", "COR-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplyVerifiedSettlementPaymentEvidenceCommand("BK-1", "E-1", "M-1",
                "REFUND", 100_000L, "USD", "OP-1", "COR-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplyVerifiedSettlementPaymentEvidenceCommand("BK-1", "E-1", "M-1",
                "FINAL_PAYMENT", 100_000L, "VND", null, null, "OP-1", "COR-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
