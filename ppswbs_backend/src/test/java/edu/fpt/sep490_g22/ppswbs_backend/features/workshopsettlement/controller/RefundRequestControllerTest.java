package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 003.3 T097 HTTP routes, FR-039/FR-040: the refund queue and its transitions are Manager-only in V1, the
 * counter entry of an account is open to Staff, the full account number is read through one audited route, and
 * a group-level refund (a late payment never re-confirmed) reports no ticket code.
 */
class RefundRequestControllerTest {

    private final BankRefundService bankRefunds = mock(BankRefundService.class);
    private final WorkshopSettlementAuthorizationService authorization =
            mock(WorkshopSettlementAuthorizationService.class);
    private final RefundRequestController controller = new RefundRequestController(bankRefunds, authorization);

    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(22L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(1L));
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(11L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));

    @Test
    void listsOnlyForAManagerAndFiltersByStatus() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(bankRefunds.list(RefundRequestStatus.PENDING_TRANSFER))
                .thenReturn(List.of(ticketRefund(RefundRequestStatus.PENDING_TRANSFER)));

        var rows = controller.list(RefundRequestStatus.PENDING_TRANSFER, auth(MANAGER));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.ticketCode()).isEqualTo("GRP-TEST0001-L0-T1");
            assertThat(row.groupCode()).isEqualTo("GRP-TEST0001");
            assertThat(row.source()).isEqualTo("MLAND_CANCELLATION");
            assertThat(row.status()).isEqualTo("PENDING_TRANSFER");
            assertThat(row.accountNumberMasked()).isEqualTo("****1234");
        });
        verify(authorization).requireManager(MANAGER);
        verify(bankRefunds).list(RefundRequestStatus.PENDING_TRANSFER);
    }

    @Test
    void aLatePaymentRefundOfAWholeGroupReportsNoTicketCode() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(bankRefunds.list(null)).thenReturn(List.of(groupRefund()));

        var rows = controller.list(null, auth(MANAGER));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.ticketCode()).isNull();
            assertThat(row.groupCode()).isEqualTo("GRP-TEST0002");
            assertThat(row.source()).isEqualTo("LATE_PAYMENT_NOT_RECONFIRMED");
        });
    }

    @Test
    void staffCannotListOrTransitionTheQueue() {
        when(authorization.requireManager(STAFF))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));

        assertThatThrownBy(() -> controller.list(null, auth(STAFF)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> controller.applyTransition("REFUND-1", "corr",
                new RefundRequestController.TransitionRequest(
                        RefundRequestController.RefundTransition.APPROVE, null, null, "Approved"), auth(STAFF)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(bankRefunds);
    }

    @Test
    void approveAndConfirmGoThroughTheLifecycleWithTheManagerAsActor() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(bankRefunds.approve(eq("REFUND-1"), eq(22L), eq("Approved"), anyString()))
                .thenReturn(ticketRefund(RefundRequestStatus.PENDING_TRANSFER));
        when(bankRefunds.confirmRefunded(eq("REFUND-1"), eq(22L), anyString()))
                .thenReturn(ticketRefund(RefundRequestStatus.REFUNDED));

        assertThat(controller.applyTransition("REFUND-1", "corr-1",
                new RefundRequestController.TransitionRequest(
                        RefundRequestController.RefundTransition.APPROVE, null, null, "Approved"), auth(MANAGER))
                .status()).isEqualTo("PENDING_TRANSFER");
        assertThat(controller.applyTransition("REFUND-1", "corr-1",
                new RefundRequestController.TransitionRequest(
                        RefundRequestController.RefundTransition.CONFIRM_REFUNDED, null, null, null), auth(MANAGER))
                .status()).isEqualTo("REFUNDED");
    }

    @Test
    void recordingTheTransferNeedsBothTheBankReferenceAndTheProof() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);

        assertThatThrownBy(() -> controller.applyTransition("REFUND-1", "corr-1",
                new RefundRequestController.TransitionRequest(
                        RefundRequestController.RefundTransition.RECORD_TRANSFER, "BANK-9", null, null),
                auth(MANAGER)))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessage("TRANSFER_PROOF_REQUIRED");
        verify(bankRefunds, never()).recordTransfer(anyString(), any(), anyString(), anyString(), anyString());

        when(bankRefunds.recordTransfer(eq("REFUND-1"), eq(22L), eq("BANK-9"), eq("proof-9"), anyString()))
                .thenReturn(ticketRefund(RefundRequestStatus.PENDING_RECONCILIATION));
        assertThat(controller.applyTransition("REFUND-1", "corr-1",
                new RefundRequestController.TransitionRequest(
                        RefundRequestController.RefundTransition.RECORD_TRANSFER, "BANK-9", "proof-9", null),
                auth(MANAGER)).status()).isEqualTo("PENDING_RECONCILIATION");
    }

    @Test
    void staffMayEnterTheAccountAtTheCounterAndResendTheLink() {
        when(authorization.requireManagerOrAnyStaff(STAFF)).thenReturn(STAFF);
        when(bankRefunds.submitAccountAtCounter(eq("REFUND-1"), eq("VCB"), eq("0123456789"), eq("NGUYEN VAN A"),
                eq(11L), anyString())).thenReturn(ticketRefund(RefundRequestStatus.PENDING_APPROVAL));

        var response = controller.recordAccountAtCounter("REFUND-1", "corr-1",
                new RefundRequestController.CounterAccountRequest("VCB", "0123456789", "NGUYEN VAN A"), auth(STAFF));

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        // The response carries the mask only, never what was just entered.
        assertThat(response.accountNumberMasked()).isEqualTo("****1234");

        assertThat(controller.resendAccountLink("REFUND-1", "corr-1", auth(STAFF)).getStatusCode().value())
                .isEqualTo(204);
        verify(bankRefunds).resendAccountLink(eq("REFUND-1"), eq(11L), anyString());
    }

    @Test
    void theFullAccountNumberIsManagerOnlyAndNotCached() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(bankRefunds.revealAccountNumber(eq("REFUND-1"), eq(22L), anyString())).thenReturn("0123451234");
        when(bankRefunds.require("REFUND-1")).thenReturn(ticketRefund(RefundRequestStatus.PENDING_TRANSFER));

        var response = controller.revealAccountNumber("REFUND-1", "corr-1", auth(MANAGER));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accountNumber()).isEqualTo("0123451234");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        // The audited read runs with the Manager as the actor.
        verify(bankRefunds).revealAccountNumber(eq("REFUND-1"), eq(22L), anyString());
    }

    @Test
    void aRequestWithoutAResolvedActorIsRefusedBeforeAnyServiceCall() {
        assertThatThrownBy(() -> controller.list(null, null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(bankRefunds, authorization);
    }

    private static Authentication auth(CurrentAccountActor actor) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    private static SettlementRefundRequest ticketRefund(RefundRequestStatus status) {
        return SettlementRefundRequest.builder()
                .requestReference("REFUND-1")
                .bookingReference("GRP-TEST0001-L0-T1")
                .groupReference("GRP-TEST0001")
                .reason(RefundReason.MLAND_CANCELLATION)
                .sourceReference("case-1")
                .amountVnd(200_000)
                .currencyCode("VND")
                .status(status)
                .recipientAccountMasked("****1234")
                .recipientBankCode("VCB")
                .recipientHolderName("NGUYEN VAN A")
                .build();
    }

    /** A late payment that was never re-confirmed: the refund belongs to the group, no ticket was confirmed. */
    private static SettlementRefundRequest groupRefund() {
        return SettlementRefundRequest.builder()
                .requestReference("REFUND-2")
                .bookingReference("GRP-TEST0002")
                .groupReference("GRP-TEST0002")
                .reason(RefundReason.LATE_PAYMENT_NOT_RECONFIRMED)
                .sourceReference("REVIEW-1")
                .amountVnd(500_000)
                .currencyCode("VND")
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build();
    }
}
