package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
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
import java.math.BigInteger;
import java.time.LocalDate;
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
    @Mock
    private LoyaltyFacade loyalty;
    @Mock
    private SettlementTicketChangeRequestRepository requestRepository;
    @Mock
    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeNotifier notifier;

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
    private static final UUID FROM_SESSION_1 = UUID.randomUUID();
    private static final UUID FROM_SESSION_2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TicketChangeService(
                booking,
                operations,
                finalBills,
                changeRepository,
                folioBalanceService,
                auditRepository,
                loyalty,
                new WorkshopSettlementAuthorizationService(
                        mock(edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade.class),
                        auditRepository, operations),
                requestRepository,
                notifier
        );
        lenient().when(requestRepository.save(any(SettlementTicketChangeRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("T116: Staff acting for a buyer outside the ticket's location, or inactive, is refused before anything")
    void staffOnBehalf_outOfScopeOrInactive_isRefused() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        TicketChangeRequestDto req = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null, "bvr-proof-1234567890", "Counter request");

        CurrentAccountActor otherBranch = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(99L));
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, req, IDEMPOTENCY_KEY, "CORR-T116a", otherBranch))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        CurrentAccountActor inactive = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.SUSPENDED, false, List.of(10L));
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, req, IDEMPOTENCY_KEY, "CORR-T116b", inactive))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);

        verify(booking, never()).consumeBuyerProof(any());
        verify(operations, never()).replaceTicketSessions(any());
    }

    @Test
    void adminWithABuyerProofIsRefusedAndNeverTreatedAsGuest() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        TicketChangeRequestDto req = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null, "bvr-proof-1234567890", "Admin request");
        CurrentAccountActor admin = new CurrentAccountActor(77L, ActorRole.ADMIN, ActorStatus.ACTIVE, false, List.of());

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, req, IDEMPOTENCY_KEY, "CORR-ADMIN", admin))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("STAFF_OR_MANAGER_REQUIRED");

        verify(booking, never()).consumeBuyerProof(any());
        verify(operations, never()).replaceTicketSessions(any());
    }

    private TicketFolioContext createDefaultContext(long voucherDiscountVnd, Instant firstSessionStart) {
        Instant fromStart = firstSessionStart != null ? firstSessionStart : Instant.now().plus(48, ChronoUnit.HOURS);
        lenient().when(operations.saleEligibility(FROM_SESSION_1)).thenReturn(new SaleEligibilitySnapshot(FROM_SESSION_1,
                true, "ACTIVE", 10L, fromStart, fromStart.plus(2, ChronoUnit.HOURS), 5, null));
        return new TicketFolioContext(
                TICKET_CODE,
                GROUP_CODE,
                "L1",
                "7",
                10L,
                List.of(FROM_SESSION_1.toString(), FROM_SESSION_2.toString()),
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
        // Owner decision 2026-11-18: the customer is told by email, once, with the change reference and no price rise.
        verify(notifier).applied(eq(TICKET_CODE), eq(ctx), eq(List.of(TARGET_SESSION_1)), eq(0L), anyString(), eq("CORR-01"));
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
                List.of(TARGET_SESSION_2), List.of(FROM_SESSION_1),
                null,
                null,
                null
        );

        // Attempting a second change no longer fails: only a Manager can allow it, so it waits in the queue (T117)
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        ChangeResultDto queued = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-02", memberActor);
        assertThat(queued.applied()).isFalse();
        assertThat(queued.changeRequestReference()).startsWith("CHG-REQ-");
        verify(requestRepository).save(argThat(r -> "CHANGE_ALLOWANCE_EXHAUSTED".equals(r.getBlockedRules())
                && SettlementTicketChangeRequest.PENDING.equals(r.getStatus())));
        verify(operations, never()).replaceTicketSessions(any());

        // Now Manager grants extra allowance (FR-054)
        CurrentAccountActor managerActor = new CurrentAccountActor(MANAGER_ACCOUNT_ID, ActorRole.MANAGER, ActorStatus.ACTIVE, false, List.of());
        GrantChangeAllowanceRequestDto grantReq = new GrantChangeAllowanceRequestDto("Manager exception for session 2 reschedule");
        ChangeAllowanceResponseDto grantRes = service.grantExtraAllowance(TICKET_CODE, grantReq, "idem-key-grant-allowance-01", "CORR-03", managerActor);

        assertThat(grantRes.ticketCode()).isEqualTo(TICKET_CODE);
        assertThat(grantRes.reason()).isEqualTo("Manager exception for session 2 reschedule");
        verify(changeRepository).save(argThat(c -> "MANAGER_GRANTED".equals(c.getChangeType())));

        // With 1 manager grant present, remaining allowance is 1 + 1 - 1 = 1 > 0
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(1L);
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        ChangeResultDto secondResult = service.changeTicketSessions(TICKET_CODE, request, "idem-key-second-change-ok", "CORR-04", memberActor);
        assertThat(secondResult.applied()).isTrue();
        assertThat(secondResult.countedChange()).isTrue();
    }

    @Test
    @DisplayName("changeTicketSessions: advance change within 24 hours of first session start waits in the Manager queue (T117)")
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                null,
                null
        );

        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));

        ChangeResultDto queued = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-05", memberActor);

        assertThat(queued.applied()).isFalse();
        assertThat(queued.changeRequestReference()).isNotBlank();
        ArgumentCaptor<SettlementTicketChangeRequest> saved = ArgumentCaptor.forClass(SettlementTicketChangeRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getBlockedRules()).isEqualTo("ADVANCE_CHANGE_WINDOW_EXPIRED");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(ctx.firstSessionStart());
        verify(operations, never()).replaceTicketSessions(any());
    }

    @Test
    @DisplayName("changeTicketSessions: window closed and the old session already started -> nothing to wait for, refused 409")
    void advanceChange_oldSessionStarted_refused() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().minus(1, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null);

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-05b", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("ADVANCE_CHANGE_WINDOW_EXPIRED");
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeTicketSessions: a second request while one is pending -> 409 CHANGE_REQUEST_PENDING")
    void queuedRequest_secondOneWhilePending_refused() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(requestRepository.existsByTicketCodeAndStatus(TICKET_CODE, "PENDING")).thenReturn(true);
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null);

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-05c", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("CHANGE_REQUEST_PENDING");
    }

    @Test
    @DisplayName("changeTicketSessions: a Guest's request is queued and spends the buyer proof once")
    void queuedRequest_guest_consumesProofOnce() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
        TicketChangeRequestDto request = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null,
                "bvr-" + "a".repeat(20), null);

        ChangeResultDto queued = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-05d", null);

        assertThat(queued.changeRequestReference()).isNotBlank();
        verify(booking, times(1)).consumeBuyerProof(any());
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-06", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("LATE_ARRIVAL_WINDOW_EXPIRED");
    }

    @Test
    @DisplayName("changeTicketSessions: 003.2 reschedule quota exhausted waits in the Manager queue (T117)")
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                null,
                null
        );

        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));

        ChangeResultDto queued = service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-07", memberActor);

        assertThat(queued.applied()).isFalse();
        assertThat(queued.changeRequestReference()).isNotBlank();
        verify(requestRepository).save(argThat(r -> "RESCHEDULE_QUOTA_EXHAUSTED".equals(r.getBlockedRules())));
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
                Instant.now().plus(50, ChronoUnit.HOURS), 5, LocalDate.now().plusDays(2)
        );
        when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(targetSessionSnap);
        // FR-044a: 006 is asked about the ticket's whole new set, so the session that stays needs its facts too.
        lenient().when(operations.saleEligibility(FROM_SESSION_2)).thenReturn(new SaleEligibilitySnapshot(FROM_SESSION_2, true,
                "ACTIVE", 10L, Instant.now().plus(72, ChronoUnit.HOURS), Instant.now().plus(74, ChronoUnit.HOURS), 5,
                LocalDate.now().plusDays(3)));
        // 006 decides scope (BE-DESIGN-0007): the ticket leaves the voucher's scope and loses its 50,000 share.
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, false, BigInteger.valueOf(50_000L)));

        when(folioBalanceService.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
                Instant.now().plus(50, ChronoUnit.HOURS), 5, LocalDate.now().plusDays(2)
        );
        when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(targetSessionSnap);
        // FR-044a: 006 is asked about the ticket's whole new set, so the session that stays needs its facts too.
        lenient().when(operations.saleEligibility(FROM_SESSION_2)).thenReturn(new SaleEligibilitySnapshot(FROM_SESSION_2, true,
                "ACTIVE", 10L, Instant.now().plus(72, ChronoUnit.HOURS), Instant.now().plus(74, ChronoUnit.HOURS), 5,
                LocalDate.now().plusDays(3)));
        // 006 decides scope (BE-DESIGN-0007): the ticket leaves the voucher's scope and loses its 50,000 share.
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, false, BigInteger.valueOf(50_000L)));

        when(folioBalanceService.balanceOf(any())).thenReturn(
                new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L), // before
                new FolioBalanceService.FolioBalance(150_000L, 0L, 150_000L)  // after price increase
        );
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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

        // FR-045 + FR-030: 50,000 VND goes to the checkout balance only; the snapshot amount is untouched.
        assertThat(folio.getRemainingAmountVnd()).isEqualTo(150_000L);
        assertThat(folio.getPostPromotionAmountVnd()).isEqualTo(200_000L);
        verify(finalBills).save(folio);

        verify(operations).replaceTicketSessions(any());
        verify(changeRepository).save(argThat(c -> c.getPriceIncreaseAmountVnd() == 50_000L && c.isBuyerConfirmedIncrease()));
    }

    @Test
    @DisplayName("changeTicketSessions: 006 says the ticket stays in scope (e.g. a branch-free voucher moved to another branch) -> no increase")
    void voucherStaysInScope_perLoyalty_noIncreaseAndQueryCarriesTheTicket() {
        TicketFolioContext ctx = createDefaultContext(50_000L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);
        LocalDate day = LocalDate.now().plusDays(2);
        when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(new SaleEligibilitySnapshot(
                TARGET_SESSION_1, true, "ACTIVE", 20L, Instant.now().plus(48, ChronoUnit.HOURS),
                Instant.now().plus(50, ChronoUnit.HOURS), 5, day));
        // FR-044a: 006 is asked about the ticket's whole new set, so the session that stays needs its facts too.
        lenient().when(operations.saleEligibility(FROM_SESSION_2)).thenReturn(new SaleEligibilitySnapshot(FROM_SESSION_2, true,
                "ACTIVE", 10L, Instant.now().plus(72, ChronoUnit.HOURS), Instant.now().plus(74, ChronoUnit.HOURS), 5,
                LocalDate.now().plusDays(3)));
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, true, BigInteger.ZERO));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null), IDEMPOTENCY_KEY, "CORR-SCOPE", memberActor);

        assertThat(result.applied()).isTrue();
        assertThat(result.priceIncreaseAmount()).isEqualTo("0");
        assertThat(folio.getRemainingAmountVnd()).isEqualTo(100_000L);
        ArgumentCaptor<TicketVoucherScopeQuery> query = ArgumentCaptor.forClass(TicketVoucherScopeQuery.class);
        verify(loyalty).checkTicketVoucherScope(query.capture());
        assertThat(query.getValue().sourceKind()).isEqualTo(SourceKind.WORKSHOP);
        assertThat(query.getValue().sourceRef()).isEqualTo(GROUP_CODE);
        assertThat(query.getValue().ticketRef()).isEqualTo("L1-T1");
        assertThat(query.getValue().packageId()).isEqualTo(7L);
        assertThat(query.getValue().proposedSessions()).hasSize(2);
        assertThat(query.getValue().proposedSessions().get(0).sessionRef()).isEqualTo(TARGET_SESSION_1.toString());
        assertThat(query.getValue().proposedSessions().get(0).branchId()).isEqualTo(20L);
        assertThat(query.getValue().proposedSessions().get(0).workshopDate()).isEqualTo(day);
        assertThat(query.getValue().proposedSessions().get(1).sessionRef()).isEqualTo(FROM_SESSION_2.toString());
    }

    @Test
    @DisplayName("changeTicketSessions: a ticket with no programme discount never asks 006")
    void noProgrammeDiscount_doesNotAskLoyalty() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null), IDEMPOTENCY_KEY, "CORR-NOV", memberActor);

        verifyNoInteractions(loyalty);
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                null, // missing proof
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-11", null))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("BUYER_VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("Staff in the ticket's location but not assigned to a session of the ticket is refused (2026-11-06 rule)")
    void staffNotAssignedToTicketSession_isRefused() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(operations.staffAssignedToBooking(eq(STAFF_ACCOUNT_ID), any())).thenReturn(false);
        CurrentAccountActor staff = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(10L));
        TicketChangeRequestDto req = new TicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1),
                List.of(FROM_SESSION_1), null, "bvr-proof-1234567890", "Counter request");

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, req, IDEMPOTENCY_KEY, "CORR-NA", staff))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("SESSION_ASSIGNMENT_REQUIRED");
    }

    @Test
    @DisplayName("changeTicketSessions: Staff at counter requires reason and buyerVerificationReference (FR-055)")
    void staffAtCounter_requiresReasonAndProof() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(48, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        SettlementFinalBill folio = createDefaultFolio(100_000L);
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(folio));

        CurrentAccountActor staffActor = new CurrentAccountActor(STAFF_ACCOUNT_ID, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(10L));
        when(operations.staffAssignedToBooking(eq(STAFF_ACCOUNT_ID), any())).thenReturn(true);

        // Missing reason -> 422 REASON_REQUIRED
        TicketChangeRequestDto noReasonReq = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                "bvr-proof-1234567890",
                null
        );
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, noReasonReq, IDEMPOTENCY_KEY, "CORR-12", staffActor))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        // Missing proof -> 409 BUYER_VERIFICATION_REQUIRED
        TicketChangeRequestDto noProofReq = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
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
        verify(operations, never()).replaceTicketSessions(any());
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
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR-17", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("TICKET_FOLIO_NOT_OPEN");
    }

    // ---- T130 (FR-044, FR-045a): the session being changed decides the window and the expiry --------------------

    private void snapshot(UUID session, Instant start, Instant end) {
        lenient().when(operations.saleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true,
                "ACTIVE", 10L, start, end, 5, null));
    }

    private ChangeResultDto queueChange(TicketChangeType type, UUID from, UUID target, String key) {
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        // The allowance is spent, so the request waits in the Manager queue and its expiry can be read.
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(1L);
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        return service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(type, List.of(target), List.of(from),
                null, null, null), key, "CORR-T130", member);
    }

    private Instant savedExpiry() {
        ArgumentCaptor<SettlementTicketChangeRequest> saved = ArgumentCaptor.forClass(SettlementTicketChangeRequest.class);
        verify(requestRepository).save(saved.capture());
        return saved.getValue().getExpiresAt();
    }

    @Test
    @DisplayName("T130(a): changing session 2 while session 1 is still ahead expires at session 2's start, not session 1's")
    void expiry_usesSessionBeingChanged_notFirstSession() {
        Instant now = Instant.now();
        TicketFolioContext ctx = createDefaultContext(0L, now.plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        Instant session2 = now.plus(30, ChronoUnit.HOURS);
        snapshot(FROM_SESSION_2, session2, session2.plus(2, ChronoUnit.HOURS));
        snapshot(TARGET_SESSION_1, now.plus(60, ChronoUnit.HOURS), now.plus(62, ChronoUnit.HOURS));

        ChangeResultDto queued = queueChange(TicketChangeType.ADVANCE, FROM_SESSION_2, TARGET_SESSION_1, IDEMPOTENCY_KEY);

        assertThat(queued.changeRequestReference()).isNotBlank();
        assertThat(savedExpiry()).isEqualTo(session2);
    }

    @Test
    @DisplayName("T130(b): a target earlier than the old session expires at the target's start")
    void expiry_targetEarlierThanOldSession() {
        Instant now = Instant.now();
        TicketFolioContext ctx = createDefaultContext(0L, now.plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        snapshot(FROM_SESSION_2, now.plus(40, ChronoUnit.HOURS), now.plus(42, ChronoUnit.HOURS));
        Instant target = now.plus(26, ChronoUnit.HOURS);
        snapshot(TARGET_SESSION_1, target, target.plus(2, ChronoUnit.HOURS));

        queueChange(TicketChangeType.ADVANCE, FROM_SESSION_2, TARGET_SESSION_1, IDEMPOTENCY_KEY);

        assertThat(savedExpiry()).isEqualTo(target);
    }

    @Test
    @DisplayName("T130(c): a target later than the old session expires at the old session's start")
    void expiry_targetLaterThanOldSession() {
        Instant now = Instant.now();
        TicketFolioContext ctx = createDefaultContext(0L, now.plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        Instant old = now.plus(28, ChronoUnit.HOURS);
        snapshot(FROM_SESSION_2, old, old.plus(2, ChronoUnit.HOURS));
        snapshot(TARGET_SESSION_1, now.plus(90, ChronoUnit.HOURS), now.plus(92, ChronoUnit.HOURS));

        queueChange(TicketChangeType.ADVANCE, FROM_SESSION_2, TARGET_SESSION_1, IDEMPOTENCY_KEY);

        assertThat(savedExpiry()).isEqualTo(old);
    }

    @Test
    @DisplayName("T130: LATE_ARRIVAL expires at the earliest of the changed session's end date + 7 calendar days (VN) and the target's start")
    void expiry_lateArrival_endPlusSevenDays_orTargetStart() {
        Instant now = Instant.now();
        Instant end = now.plus(1, ChronoUnit.HOURS);
        TicketFolioContext ctx = createDefaultContext(0L, now.minus(1, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        snapshot(FROM_SESSION_1, now.minus(1, ChronoUnit.HOURS), end);
        snapshot(TARGET_SESSION_1, now.plus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));

        queueChange(TicketChangeType.LATE_ARRIVAL, FROM_SESSION_1, TARGET_SESSION_1, IDEMPOTENCY_KEY);
        // FR-062: the start of the 8th calendar day (Asia/Ho_Chi_Minh) after the end date, not end + 168 h.
        assertThat(savedExpiry()).isEqualTo(end.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()
                .plusDays(8).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());

        clearInvocations(requestRepository);
        Instant nearTarget = now.plus(3, ChronoUnit.DAYS);
        snapshot(TARGET_SESSION_2, nearTarget, nearTarget.plus(2, ChronoUnit.HOURS));
        queueChange(TicketChangeType.LATE_ARRIVAL, FROM_SESSION_1, TARGET_SESSION_2, "idem-key-ticket-change-002");
        assertThat(savedExpiry()).isEqualTo(nearTarget);
    }

    @Test
    @DisplayName("T130: the 24 h window is measured from the session being changed: session 2 on the day of session 1 is not blocked")
    void advanceWindow_usesSessionBeingChanged() {
        Instant now = Instant.now();
        // Session 1 starts in 12 h (inside 24 h), session 2 in 30 h; the buyer changes session 2.
        TicketFolioContext ctx = createDefaultContext(0L, now.plus(12, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        snapshot(FROM_SESSION_2, now.plus(30, ChronoUnit.HOURS), now.plus(32, ChronoUnit.HOURS));
        snapshot(TARGET_SESSION_1, now.plus(60, ChronoUnit.HOURS), now.plus(62, ChronoUnit.HOURS));
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_2), null, null, null),
                IDEMPOTENCY_KEY, "CORR-T130w", member);

        assertThat(result.applied()).isTrue();
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("T130: a from session that is not one of the ticket's sessions, or a missing/mismatched list, is refused 422")
    void fromSessions_notOfTicket_refused422() {
        TicketFolioContext ctx = createDefaultContext(0L, null);
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(stranger), null, null, null),
                IDEMPOTENCY_KEY, "CORR-T130x", member))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("FROM_SESSION_NOT_OF_TICKET");
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), null, null, null, null),
                IDEMPOTENCY_KEY, "CORR-T130y", member))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("FROM_SESSIONS_REQUIRED");
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1, TARGET_SESSION_2), List.of(FROM_SESSION_1), null, null, null),
                IDEMPOTENCY_KEY, "CORR-T130z", member))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("FROM_SESSIONS_MISMATCH");
        verify(operations, never()).replaceTicketSessions(any());
    }

    // ---- FR-044a: from names only the sessions that move; the other session of the ticket stays -----------------

    private void openFolioForChange() {
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET_CODE)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET_CODE, "MANAGER_GRANTED")).thenReturn(0L);
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
    }

    @Test
    @DisplayName("FR-044a: moving only session 1 of a two-session ticket sends 003.2 the whole new set, the other stays")
    void movingOneSession_sendsTheWholeNewSetToOperations() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(72, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        openFolioForChange();
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null),
                IDEMPOTENCY_KEY, "CORR-FR044a", member);

        assertThat(result.applied()).isTrue();
        assertThat(result.targetSessionReferences()).containsExactly(TARGET_SESSION_1);
        ArgumentCaptor<ReplaceTicketSessionsCommand> command = ArgumentCaptor.forClass(ReplaceTicketSessionsCommand.class);
        verify(operations).replaceTicketSessions(command.capture());
        assertThat(command.getValue().lines().get(0).targetSessionReferences())
                .containsExactly(TARGET_SESSION_1, FROM_SESSION_2);
    }

    @Test
    @DisplayName("FR-044a: the old shape (the unmoved session repeated as from and target) still works and ignores the pair")
    void repeatedUnmovedSession_isIgnored() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(72, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        openFolioForChange();
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));

        ChangeResultDto result = service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(TARGET_SESSION_1, FROM_SESSION_2),
                List.of(FROM_SESSION_1, FROM_SESSION_2), null, null, null), IDEMPOTENCY_KEY, "CORR-FR044b", member);

        assertThat(result.applied()).isTrue();
        ArgumentCaptor<ReplaceTicketSessionsCommand> command = ArgumentCaptor.forClass(ReplaceTicketSessionsCommand.class);
        verify(operations).replaceTicketSessions(command.capture());
        assertThat(command.getValue().lines().get(0).targetSessionReferences())
                .containsExactly(TARGET_SESSION_1, FROM_SESSION_2);
    }

    @Test
    @DisplayName("FR-044a: a request in which nothing moves, or that targets a session the ticket already holds, is 422")
    void nothingMoves_orDuplicate_isRefused() {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(72, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));

        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(FROM_SESSION_1), List.of(FROM_SESSION_1), null, null, null),
                IDEMPOTENCY_KEY, "CORR-FR044c", member))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("FROM_SESSIONS_MISMATCH");
        assertThatThrownBy(() -> service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(FROM_SESSION_2), List.of(FROM_SESSION_1), null, null, null),
                IDEMPOTENCY_KEY, "CORR-FR044d", member))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("FROM_SESSIONS_MISMATCH");
        verify(operations, never()).replaceTicketSessions(any());
    }

    private void stubCrossLocationChange(Long targetLocation) {
        TicketFolioContext ctx = createDefaultContext(0L, Instant.now().plus(72, ChronoUnit.HOURS));
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(finalBills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createDefaultFolio(100_000L)));
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        Instant start = Instant.now().plus(96, ChronoUnit.HOURS);
        lenient().when(operations.saleEligibility(TARGET_SESSION_1)).thenReturn(new SaleEligibilitySnapshot(TARGET_SESSION_1,
                true, "ACTIVE", targetLocation, start, start.plus(2, ChronoUnit.HOURS), 5, null));
    }

    private ChangeResultDto changeToTarget() {
        CurrentAccountActor member = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of(10L));
        return service.changeTicketSessions(TICKET_CODE, new TicketChangeRequestDto(TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION_1), List.of(FROM_SESSION_1), null, null, null), IDEMPOTENCY_KEY, "CORR-LOC", member);
    }

    @Test
    @DisplayName("T184: a change to another location needs no Manager and writes TICKET_LOCATION_CHANGED with both ids")
    void crossLocationChange_appliesAndAudits() {
        stubCrossLocationChange(20L);
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        ChangeResultDto result = changeToTarget();

        assertThat(result.applied()).isTrue();
        ArgumentCaptor<edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit> audit =
                ArgumentCaptor.forClass(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit.class);
        verify(auditRepository, atLeastOnce()).save(audit.capture());
        assertThat(audit.getAllValues()).anySatisfy(a -> {
            assertThat(a.getActionCode()).isEqualTo("TICKET_LOCATION_CHANGED");
            assertThat(a.getReasonReference()).isEqualTo("from=10;to=20");
        });
        verify(requestRepository, never()).save(any(SettlementTicketChangeRequest.class));
    }

    @Test
    @DisplayName("T184: a change inside the same location writes no TICKET_LOCATION_CHANGED")
    void sameLocationChange_noLocationAudit() {
        stubCrossLocationChange(10L);
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        assertThat(changeToTarget().applied()).isTrue();

        verify(auditRepository, never()).save(argThat(a -> "TICKET_LOCATION_CHANGED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("T184: a pair not ACTIVE at the target location is 409 PACKAGE_NOT_AVAILABLE_AT_LOCATION, nothing recorded")
    void crossLocationChange_packageNotAvailable_isRefused() {
        stubCrossLocationChange(20L);
        when(operations.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION, List.of(0)));

        assertThatThrownBy(this::changeToTarget).isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("PACKAGE_NOT_AVAILABLE_AT_LOCATION");

        verify(changeRepository, never()).save(any(SettlementTicketChange.class));
        verify(requestRepository, never()).save(any(SettlementTicketChangeRequest.class));
        verify(auditRepository, never()).save(argThat(a -> "TICKET_LOCATION_CHANGED".equals(a.getActionCode())));
    }
}
