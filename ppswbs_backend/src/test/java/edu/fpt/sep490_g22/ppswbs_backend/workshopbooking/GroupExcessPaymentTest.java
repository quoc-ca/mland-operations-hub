package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupExcessPaymentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ExcessPaymentRefundPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.LatePaymentRefundOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 T124 / FR-013a: a second verified payment on a CONFIRMED group is excess money, refunded 100%. */
class GroupExcessPaymentTest {

    private static final String GROUP = "GRP-EXCESS0001";
    private GroupPaymentAttemptRepository attempts;
    private GroupConfirmationService confirmation;
    private GroupExcessPaymentRepository excess;
    private ExcessPaymentRefundPort refunds;
    private ManagerAlertService alerts;
    private GroupPaymentEvidenceService evidence;
    private BookingGroup group;
    private GroupPaymentAttempt second;

    @BeforeEach
    void setUp() {
        attempts = mock(GroupPaymentAttemptRepository.class);
        confirmation = mock(GroupConfirmationService.class);
        excess = mock(GroupExcessPaymentRepository.class);
        refunds = mock(ExcessPaymentRefundPort.class);
        alerts = mock(ManagerAlertService.class);
        evidence = new GroupPaymentEvidenceService(attempts, confirmation, mock(BookingGroupRepository.class),
                mock(LatePaymentReviewService.class), excess, refunds, alerts,
                new GroupConfirmationAttemptRunner(confirmation,
                        mock(org.springframework.transaction.PlatformTransactionManager.class)),
                mock(GroupConfirmationRetryService.class));
        group = BookingGroup.builder().id(1L).groupCode(GROUP).groupStatus(BookingStatus.CONFIRMED)
                .paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(150_000).build();
        second = GroupPaymentAttempt.builder().group(group).attemptReference("GATT-2").merchantReference("GRP-M2")
                .amountVnd(150_000).attemptStatus(PaymentAttemptStatus.PENDING).build();
        when(attempts.findByMerchantReference("GRP-M2")).thenReturn(Optional.of(second));
        when(excess.findByVerifiedEvidenceReference("GRP-M2")).thenReturn(Optional.empty());
    }

    private DepositPaymentEvidenceOutcome apply() {
        return evidence.apply(GROUP, "GRP-M2", 150_000, "VND", "VNPAY-TX-2", "op-2", "corr-2");
    }

    @Test
    void aSecondVerifiedPaymentIsRecordedInFullRefundedAndAlertedWithoutTouchingTheGroup() {
        when(refunds.openExcessPaymentRefund(any())).thenReturn(new LatePaymentRefundOutcome(
                LatePaymentRefundOutcome.ACCEPTED, "REFUND-1", "PENDING_ACCOUNT", null));

        assertThat(apply()).isEqualTo(DepositPaymentEvidenceOutcome.accepted());

        ArgumentCaptor<ExcessPaymentRefundPort.OpenExcessPaymentRefundCommand> command =
                ArgumentCaptor.forClass(ExcessPaymentRefundPort.OpenExcessPaymentRefundCommand.class);
        verify(refunds).openExcessPaymentRefund(command.capture());
        assertThat(command.getValue().groupCode()).isEqualTo(GROUP);
        assertThat(command.getValue().evidenceReference()).isEqualTo("GRP-M2");
        assertThat(command.getValue().amountVnd()).isEqualTo(150_000);
        ArgumentCaptor<GroupExcessPayment> saved = ArgumentCaptor.forClass(GroupExcessPayment.class);
        verify(excess).save(saved.capture());
        assertThat(saved.getValue().getAmountVnd()).isEqualTo(150_000);
        assertThat(saved.getValue().getRefundRequestReference()).isEqualTo("REFUND-1");
        assertThat(saved.getValue().getRefundOutcome()).isEqualTo("ACCEPTED");
        verify(alerts).alert(eq(group), eq(GroupNotificationService.KIND_EXCESS_PAYMENT_MANAGER_ALERT), eq("GRP-M2"),
                anyString(), anyString(), eq("corr-2"));
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(confirmation);
    }

    @Test
    void aReplayOfTheSameExcessEvidenceSettlesNothingAgain() {
        when(excess.findByVerifiedEvidenceReference("GRP-M2")).thenReturn(Optional.of(GroupExcessPayment.builder().build()));

        assertThat(apply()).isEqualTo(DepositPaymentEvidenceOutcome.accepted());

        verifyNoInteractions(refunds, alerts, confirmation);
        verify(excess, never()).save(any());
    }

    @Test
    void aRefusedRefundRecordsNothingAndAlertsNobody() {
        when(refunds.openExcessPaymentRefund(any())).thenReturn(new LatePaymentRefundOutcome(
                LatePaymentRefundOutcome.REJECTED, null, null, "AMOUNT_INVALID"));

        DepositPaymentEvidenceOutcome outcome = apply();

        assertThat(outcome).isEqualTo(DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY));
        verify(excess, never()).save(any());
        verifyNoInteractions(alerts, confirmation);
        assertThat(second.getAttemptStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
    }
}
