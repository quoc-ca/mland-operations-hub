package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioBalanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketChangeServiceTest {

    @Mock
    private WorkshopBookingFacade booking;
    @Mock
    private WorkshopOperationsSettlementAdapter operations;
    @Mock
    private SettlementFinalBillRepository finalBills;
    @Mock
    private SettlementTicketChangeRepository changeRepository;
    @Mock
    private FolioBalanceService folioBalanceService;
    @Mock
    private SettlementBusinessAuditRepository auditRepository;

    private TicketChangeService service;

    private static final String TICKET_CODE = "GRP-01-L1-T1";
    private static final String GROUP_CODE = "GRP-01";
    private static final UUID OPS_TICKET_REF = UUID.randomUUID();
    private static final Long BUYER_ACCOUNT_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final Long STAFF_ACCOUNT_ID = 200L;
    private static final Long MANAGER_ACCOUNT_ID = 300L;
    private static final String IDEMPOTENCY_KEY = "idem-key-ticket-change-001";
    private static final UUID TARGET_SESSION_1 = UUID.randomUUID();
    private static final UUID TARGET_SESSION_2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TicketChangeService(
                booking,
                operations,
                finalBills,
                changeRepository,
                folioBalanceService,
                auditRepository
        );
    }

    private TicketFolioContext createDefaultContext(long voucherDiscountVnd, Instant firstSessionStart) {
        return new TicketFolioContext(
                TICKET_CODE,
                GROUP_CODE,
                "L1",
                "PKG-01",
                10L,
                List.of("session-1", "session-2"),
                BUYER_ACCOUNT_ID,
                OPS_TICKET_REF.toString(),
                firstSessionStart != null ? firstSessionStart : Instant.now().plus(48, ChronoUnit.HOURS),
                0L,
                "L1-T1",
                voucherDiscountVnd
        );
    }

    private SettlementFinalBill createDefaultFolio(long remainingVnd) {
        return SettlementFinalBill.builder()
                .billReference("BILL-" + TICKET_CODE)
                .bookingReference(TICKET_CODE)
                .groupReference(GROUP_CODE)
                .postPromotionAmountVnd(200_000L)
                .acceptedDepositAmountVnd(100_000L)
                .remainingAmountVnd(remainingVnd)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .build();
    }

    @Test
    @DisplayName("changeTicketSessions: advance change auto-approved when conditions hold (FR-045a)")
    void advanceChange_autoApproved_success() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-01", memberActor);

        assertThat(result.applied()).isTrue();
        assertThat(result.confirmIncreaseRequired()).isFalse();
        assertThat(result.priceIncreaseAmount()).isEqualTo("0");
        assertThat(result.countedChange()).isTrue();
        assertThat(result.balanceDue()).isEqualTo("100000");

        ArgumentCaptor<ReplaceTicketSessionsCommand> cmdCaptor = ArgumentCaptor.forClass(ReplaceTicketSessionsCommand.class);
        verify(operations).replaceTicketSessions(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().type()).isEqualTo(TicketReplacementType.CUSTOMER_ADVANCE);
        assertThat(cmdCaptor.getValue().lines().getFirst().ticketReference()).isEqualTo(OPS_TICKET_REF);

        verify(changeRepository).save(any(SettlementTicketChange.class));
    }

    @Test
    @DisplayName("changeTicketSessions: moving session 1 exhausts normal allowance for later session 2 change; Manager grant unblocks it")
    void movingSession1ExhaustsAllowance_managerGrantUnblocksSession2() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        // 1 counted change already used, 0 extra granted
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(1L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_2),
                null,
                null,
                null
        );

        // Attempting a second change fails with CHANGE_ALLOWANCE_EXHAUSTED
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-02", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("CHANGE_ALLOWANCE_EXHAUSTED");

        // Now Manager grants extra allowance (FR-054)
        CurrentAccountActor managerActor = new CurrentAccountActor(MANAGER_ACCOUNT_ID, ActorRole.MANAGER, ActorStatus.ACTIVE, false, List.of());
        GrantChangeAllowanceRequestDto grantReq = new GrantChangeAllowanceRequestDto("Manager exception for session 2 reschedule");
        ChangeAllowanceResponseDto grantRes = service.grantExtraAllowance(TICKET_CODE, grantReq, "idem-key-grant-allowance-01", "CORR-03", managerActor);

        assertThat(grantRes.ticketCode()).isEqualTo(TICKET_CODE);
        assertThat(grantRes.reason()).isEqualTo("Manager exception for session 2 reschedule");
        verify(changeRepository).save(argThat(c -> "MANAGER_GRANTED".equals(c.getChangeType())));

        // With 1 manager grant present, remaining allowance is 1 + 1 - 1 = 1 > 0
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(1L);
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        ChangeResultDto secondResult = service.changeTicketSessions(TICKET_CODE, request, "idem-key-second-change-ok", "CORR-04", memberActor);
        assertThat(secondResult.applied()).isTrue();
        assertThat(secondResult.countedChange()).isTrue();
    }

    @Test
    @DisplayName("changeTicketSessions: advance change within 24 hours of first session start throws 409 ADVANCE_CHANGE_WINDOW_EXPIRED")
    void advanceChange_within24Hours_throws409() {
        // First session start is in 12 hours (less than 24h)
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-05", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("ADVANCE_CHANGE_WINDOW_EXPIRED");
    }

    @Test
    @DisplayName("changeTicketSessions: late arrival change delegates to CUSTOMER_LATE_ARRIVAL and reports 003.2 rejection")
    void lateArrival_delegatesToOperations_reportsRejection() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().minus(1, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        when(operations.replaceTicketSessions(any()))
                .thenReturn(TicketSessionReplacementOutcome.rejected(RejectionReason.LATE_ARRIVAL_WINDOW_EXPIRED, List.of(0)));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.LATE_ARRIVAL,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-06", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("LATE_ARRIVAL_WINDOW_EXPIRED");
    }

    @Test
    @DisplayName("changeTicketSessions: 003.2 reschedule quota exhausted throws 409 RESCHEDULE_QUOTA_EXHAUSTED")
    void quotaExhausted_throws409() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        when(operations.replaceTicketSessions(any()))
                .thenReturn(TicketSessionReplacementOutcome.rejected(RejectionReason.RESCHEDULE_QUOTA_EXHAUSTED, List.of(0)));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-07", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("RESCHEDULE_QUOTA_EXHAUSTED");
    }

    @Test
    @DisplayName("changeTicketSessions: moving outside voucher scope without confirmIncrease returns 200 with applied=false and confirmIncreaseRequired=true")
    void voucherScopeLoss_withoutConfirmation_returnsNotApplied() {
        // Ticket had 50,000 VND voucher discount at location 10L
        TicketFolioContext ctx = createDefaultContext(50_000L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        // Target session is at location 20L (different branch -> out of voucher scope)
        SaleEligibilitySnapshot targetSessionSnap = new SaleEligibilitySnapshot(
                TARGET_SESSION_1, true, "ACTIVE", 20L, Instant.now().plus(48, ChronoUnit.HOURS),
                Instant.now().plus(50, ChronoUnit.HOURS), 5, null
        );
        when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(targetSessionSnap);

        when(folioBalanceService.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                false, // NOT confirmed yet
                null,
                null
        );

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-08", memberActor);

        assertThat(result.applied()).isFalse();
        assertThat(result.confirmIncreaseRequired()).isTrue();
        assertThat(result.priceIncreaseAmount()).isEqualTo("50000");
        assertThat(result.balanceDue()).isEqualTo("100000");
        assertThat(result.countedChange()).isFalse();

        // 003.2 was NOT called and nothing was persisted
        verify(operations, never()).replaceTicketSessions(any());
        verify(changeRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeTicketSessions: moving outside voucher scope WITH confirmIncrease=true applies increase to folio and commits change")
    void voucherScopeLoss_withConfirmation_appliesIncreaseAndCommits() {
        TicketFolioContext ctx = createDefaultContext(50_000L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);

        SaleEligibilitySnapshot targetSessionSnap = new SaleEligibilitySnapshot(
                TARGET_SESSION_1, true, "ACTIVE", 20L, Instant.now().plus(48, ChronoUnit.HOURS),
                Instant.now().plus(50, ChronoUnit.HOURS), 5, null
        );
        when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(targetSessionSnap);

        when(folioBalanceService.balanceOf(any())).thenReturn(
                new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L), // before
                new FolioBalanceService.FolioBalance(150_000L, 0L, 150_000L)  // after price increase
        );
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                true, // CONFIRMED increase
                null,
                null
        );

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-09", memberActor);

        assertThat(result.applied()).isTrue();
        assertThat(result.confirmIncreaseRequired()).isFalse();
        assertThat(result.priceIncreaseAmount()).isEqualTo("50000");
        assertThat(result.balanceDue()).isEqualTo("150000");
        assertThat(result.countedChange()).isTrue();

        // 50,000 VND added to folio remaining and postPromotion amount
        assertThat(folio.getRemainingAmountVnd()).isEqualTo(150_000L);
        assertThat(folio.getPostPromotionAmountVnd()).isEqualTo(250_000L);
        verify(finalBills).save(folio);

        verify(operations).replaceTicketSessions(any());
        verify(changeRepository).save(argThat(c -> c.getPriceIncreaseAmountVnd() == 50_000L && c.isBuyerConfirmedIncrease()));
    }

    @Test
    @DisplayName("changeTicketSessions: recipient (different member account) refused with 403 NOT_BUYER")
    void recipientCaller_refused403() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        CurrentAccountActor recipientActor = new CurrentAccountActor(OTHER_MEMBER_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-10", recipientActor))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("NOT_BUYER");
    }

    @Test
    @DisplayName("changeTicketSessions: Guest without buyerVerificationReference throws 409 BUYER_VERIFICATION_REQUIRED")
    void guestWithoutProof_throws409() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null, // missing proof
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-11", null))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("BUYER_VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("changeTicketSessions: Staff at counter requires reason and buyerVerificationReference (FR-055)")
    void staffAtCounter_requiresReasonAndProof() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        CurrentAccountActor staffActor = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(10L));

        // Missing reason -> 422 REASON_REQUIRED
        TicketChangeRequestDto noReasonReq = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                "bvr-proof-1234567890",
                null
        );
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, noReasonReq, IDEMPOTENCY_KEY, "CORR-12", staffActor))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        // Missing proof -> 409 BUYER_VERIFICATION_REQUIRED
        TicketChangeRequestDto noProofReq = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                "Customer requested session change at counter"
        );
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, noProofReq, IDEMPOTENCY_KEY, "CORR-13", staffActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("BUYER_VERIFICATION_REQUIRED");

        // Valid reason and proof -> consumes proof and succeeds
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

        TicketChangeRequestDto validStaffReq = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                "bvr-proof-1234567890",
                "Customer requested session change at counter"
        );
        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, validStaffReq, IDEMPOTENCY_KEY, "CORR-14", staffActor);
        assertThat(result.applied()).isTrue();
        verify(booking).consumeBuyerProof(any());
    }

    @Test
    @DisplayName("changeTicketSessions: replaying with same Idempotency-Key returns stored result without re-executing")
    void idempotencyReplay_returnsStored() {
        SettlementTicketChange stored = SettlementTicketChange.builder()
                .changeReference("TCHG-PREV-01")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .ticketCode(TICKET_CODE)
                .groupCode(GROUP_CODE)
                .changeType("ADVANCE")
                .toSessionReferences(TARGET_SESSION_1.toString())
                .counted(true)
                .priceIncreaseAmountVnd(0L)
                .buyerConfirmedIncrease(false)
                .applied(true)
                .balanceDueSnapshot(100_000L)
                .createdAt(Instant.now())
                .build();
        when(changeRepository.findByTicketCodeAndIdempotencyKey(TICKET_CODE, IDEMPOTENCY_KEY)).thenReturn(Optional.of(stored));

        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-15", null);

        assertThat(result.applied()).isTrue();
        assertThat(result.ticketCode()).isEqualTo(TICKET_CODE);
        assertThat(result.priceIncreaseAmount()).isEqualTo("0");
        assertThat(result.balanceDue()).isEqualTo("100000");

        verifyNoInteractions(booking);
        verifyNoInteractions(operations);
    }

    @Test
    @DisplayName("grantExtraAllowance: non-manager refused with 403 MANAGER_ONLY")
    void grantExtraAllowance_nonManager_throws403() {
        CurrentAccountActor staffActor = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(10L));
        GrantChangeAllowanceRequestDto request = new GrantChangeAllowanceRequestDto("Staff trying to grant");

        assertThatThrownBy(() -> service.grantExtraAllowance(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-16", staffActor))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("MANAGER_ONLY");
    }

    @Test
    @DisplayName("changeTicketSessions: folio not OPEN throws 409 TICKET_FOLIO_NOT_OPEN")
    void folioNotOpen_throws409() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill finalizedFolio = createDefaultFolio(0L);
        finalizedFolio.setLifecycleStatus(SettlementFinalBillLifecycleStatus.FINALIZED.name());
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(finalizedFolio));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-17", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("TICKET_FOLIO_NOT_OPEN");
    }
}

