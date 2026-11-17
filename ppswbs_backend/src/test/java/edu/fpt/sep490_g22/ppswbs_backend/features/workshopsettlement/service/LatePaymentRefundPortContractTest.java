package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.LatePaymentRefundOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.OpenLatePaymentRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentReviewSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Contract of the 003.1 → 003.3 late-payment refund port (FR-040, decision 2026-11-13). */
class LatePaymentRefundPortContractTest {

    private final WorkshopBookingFacade bookings = mock(WorkshopBookingFacade.class);
    private final BankRefundService bankRefunds = mock(BankRefundService.class);
    private final LatePaymentRefundPortAdapter port = new LatePaymentRefundPortAdapter(bookings, bankRefunds);

    private static final LatePaymentReviewSnapshot REVIEW =
            new LatePaymentReviewSnapshot("rev-1", "GRP-1", "MERCH-1", 150_000, "PENDING_MANAGER");

    private static OpenLatePaymentRefundCommand command(long amount) {
        return new OpenLatePaymentRefundCommand("GRP-1", "rev-1", "MERCH-1", amount, "op-1", "corr-1");
    }

    private static SettlementRefundRequest request(long amount) {
        return SettlementRefundRequest.builder().requestReference("REFUND-1").amountVnd(amount)
                .status(RefundRequestStatus.PENDING_ACCOUNT).build();
    }

    @Test
    void newRefund_isAccepted_inPendingAccount() {
        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.of(REVIEW));
        when(bankRefunds.openLatePaymentRefund("GRP-1", "rev-1", 150_000, 150_000, "corr-1"))
                .thenReturn(new BankRefundService.OpenedRefund(request(150_000), "raw-link"));

        LatePaymentRefundOutcome outcome = port.openLatePaymentRefund(command(150_000));

        assertThat(outcome.status()).isEqualTo(LatePaymentRefundOutcome.ACCEPTED);
        assertThat(outcome.refundRequestReference()).isEqualTo("REFUND-1");
        assertThat(outcome.refundStatus()).isEqualTo("PENDING_ACCOUNT");
        assertThat(outcome.opened()).isTrue();
    }

    @Test
    void repeatedRefund_isPriorEquivalent() {
        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.of(REVIEW));
        when(bankRefunds.openLatePaymentRefund("GRP-1", "rev-1", 150_000, 150_000, "corr-1"))
                .thenReturn(new BankRefundService.OpenedRefund(request(150_000), null));

        assertThat(port.openLatePaymentRefund(command(150_000)).status())
                .isEqualTo(LatePaymentRefundOutcome.PRIOR_EQUIVALENT);
    }

    @Test
    void amountOtherThanTheVerifiedLatePayment_isRejectedWithoutOpening() {
        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.of(REVIEW));

        LatePaymentRefundOutcome outcome = port.openLatePaymentRefund(command(149_000));

        assertThat(outcome.status()).isEqualTo(LatePaymentRefundOutcome.REJECTED);
        assertThat(outcome.reason()).isEqualTo("AMOUNT_MISMATCH");
        verify(bankRefunds, never()).openLatePaymentRefund(anyString(), anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    void unknownOrForeignReview_isRejected() {
        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.empty());
        assertThat(port.openLatePaymentRefund(command(150_000)).reason()).isEqualTo("EVIDENCE_NOT_FOUND");

        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.of(
                new LatePaymentReviewSnapshot("rev-1", "GRP-OTHER", "MERCH-1", 150_000, "PENDING_MANAGER")));
        assertThat(port.openLatePaymentRefund(command(150_000)).reason()).isEqualTo("EVIDENCE_NOT_FOUND");
    }

    @Test
    void existingRequestWithAnotherAmount_isASourceConflict() {
        when(bookings.findLatePaymentReview("rev-1")).thenReturn(Optional.of(REVIEW));
        when(bankRefunds.openLatePaymentRefund("GRP-1", "rev-1", 150_000, 150_000, "corr-1"))
                .thenReturn(new BankRefundService.OpenedRefund(request(100_000), null));

        assertThat(port.openLatePaymentRefund(command(150_000)).reason()).isEqualTo("SOURCE_CONFLICT");
    }
}
