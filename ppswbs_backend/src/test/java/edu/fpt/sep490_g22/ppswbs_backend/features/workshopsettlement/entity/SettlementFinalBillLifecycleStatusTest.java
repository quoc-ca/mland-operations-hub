package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementFinalBillLifecycleStatusTest {

    @Test
    void vocabularyContainsExactlyOpenFinalizedClosed() {
        assertThat(SettlementFinalBillLifecycleStatus.values()).containsExactly(
                SettlementFinalBillLifecycleStatus.OPEN,
                SettlementFinalBillLifecycleStatus.FINALIZED,
                SettlementFinalBillLifecycleStatus.CLOSED
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "FINALIZED", "CLOSED"})
    void recognizesCanonicalDirectFolioStates(String status) {
        assertThat(SettlementFinalBillLifecycleStatus.isKnown(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PAID", "CANCELLED", "EXPIRED", "UNKNOWN", "", "open"})
    void rejectsUnsupportedStatusValues(String status) {
        assertThat(SettlementFinalBillLifecycleStatus.isKnown(status)).isFalse();
    }

    @Test
    void nullStatusIsNotKnown() {
        assertThat(SettlementFinalBillLifecycleStatus.isKnown(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "FINALIZED", "CLOSED"})
    void entityAllowsKnownStatusOnPersistAndUpdate(String status) {
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-TEST-1")
                .bookingReference("BK-TEST-1")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus(status)
                .build();

        assertThatCode(bill::onCreate).doesNotThrowAnyException();
        assertThatCode(bill::onUpdate).doesNotThrowAnyException();
    }

    @Test
    void entityRejectsUnsupportedStatusOnPersist() {
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-TEST-1")
                .bookingReference("BK-TEST-1")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus("INVALID_STATUS")
                .build();

        assertThatThrownBy(bill::onCreate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SETTLEMENT_FINAL_BILL_STATUS_UNSUPPORTED");
    }

    @Test
    void entityRejectsUnsupportedStatusOnUpdate() {
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-TEST-1")
                .bookingReference("BK-TEST-1")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus("OPEN")
                .build();

        bill.onCreate();
        bill.setLifecycleStatus("EXPIRED");

        assertThatThrownBy(bill::onUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SETTLEMENT_FINAL_BILL_STATUS_UNSUPPORTED");
    }
}

