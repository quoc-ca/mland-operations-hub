package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 003.3 T090 / FR-022b1: the settlement-case lifecycle vocabulary. */
class SettlementCaseLifecycleStatusTest {

    @Test
    void vocabularyIsExactlyTheSpecStates() {
        assertThat(SettlementCaseLifecycleStatus.values()).extracting(Enum::name)
                .containsExactly("OPEN", "PENDING_PROVIDER", "RESOLVED", "REJECTED", "FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "PENDING_PROVIDER", "RESOLVED", "REJECTED", "FAILED"})
    void caseAcceptsEverySpecStateOnInsertAndUpdate(String status) {
        SettlementCase settlementCase = caseWith(status);

        assertThatCode(settlementCase::onCreate).doesNotThrowAnyException();
        assertThatCode(settlementCase::onUpdate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"CANCELED", "EXPIRED", "PENDING", "open", ""})
    void caseRejectsNonSpecStateBeforeItReachesTheDatabase(String status) {
        assertThatThrownBy(caseWith(status)::onCreate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SETTLEMENT_CASE_STATUS_UNSUPPORTED");
        assertThatThrownBy(caseWith(status)::onUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SETTLEMENT_CASE_STATUS_UNSUPPORTED");
    }

    @Test
    void lifecycleWalksOpenThroughPendingProviderToResolvedOrFailed() {
        for (String terminal : new String[] {"RESOLVED", "FAILED"}) {
            SettlementCase settlementCase = caseWith("OPEN");
            settlementCase.onCreate();
            settlementCase.setLifecycleStatus("PENDING_PROVIDER");
            settlementCase.onUpdate();
            settlementCase.setLifecycleStatus(terminal);
            settlementCase.onUpdate();
            assertThat(settlementCase.getLifecycleStatus()).isEqualTo(terminal);
        }
    }

    private static SettlementCase caseWith(String status) {
        return SettlementCase.builder().caseReference("CASE-1").bookingReference("BK-1")
                .caseType("MLAND_CANCELLATION_REMEDY").lifecycleStatus(status)
                .correlationReference("CORR-1").build();
    }
}
