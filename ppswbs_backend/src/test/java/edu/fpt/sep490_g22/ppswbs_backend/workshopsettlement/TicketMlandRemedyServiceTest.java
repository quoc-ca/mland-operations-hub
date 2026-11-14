package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketMlandRemedyServiceTest {

    @Mock
    private SettlementTicketMlandRemedyRepository remedyRepository;
    @Mock
    private SettlementFinalBillRepository bills;
    @Mock
    private SettlementFolioCollectionRepository collections;
    @Mock
    private SettlementEvidenceConclusionRepository evidence;
    @Mock
    private SettlementBusinessAuditRepository audits;
    @Mock
    private WorkshopBookingSettlementAdapter booking;
    @Mock
    private WorkshopOperationsSettlementAdapter operations;
    @Mock
    private BankRefundService bankRefunds;
    @Mock
    private LoyaltyFacade loyalty;
    @Mock
    private SettlementCustomerNotifier notifier;
    @Mock
    private SettlementIdempotencyService idempotency;

    private TicketMlandRemedyService service;

    private static final String GROUP_CODE = "GRP-MLAND-01";
    private static final String TICKET_CODE = "TK-MLAND-001";
    private static final UUID OPS_TICKET_REF = UUID.randomUUID();
    private static final Long BUYER_ACCOUNT_ID = 200L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key-mland-remedy-123";
    private static final String OFFERED_SESSION = "11111111-1111-4111-8111-111111111111";

    @BeforeEach
    void setUp() {
        service = new TicketMlandRemedyService(
                remedyRepository,
                bills,
                collections,
                evidence,
                audits,
                booking,
                operations,
                bankRefunds,
                loyalty,
                notifier,
                idempotency,
                new WorkshopSettlementAuthorizationService(
                        mock(edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade.class),
                        audits, operations)
        );
        lenient().when(idempotency.findOutcomeReference(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<String> supplier = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, supplier.get());
        });
    }

    private TicketFolioContext createContext(String ticketCode, UUID opsRef, long pointsUsed, String partRef) {
        return new TicketFolioContext(
                ticketCode,
                GROUP_CODE,
                "LINE-1",
                "PKG-01",
                1L,
                List.of(UUID.randomUUID().toString()),
                BUYER_ACCOUNT_ID,
                opsRef != null ? opsRef.toString() : null,
                Instant.now().plus(48, ChronoUnit.HOURS),
                pointsUsed,
                partRef
        );
    }

    private SettlementFinalBill createBill(String ticketCode, long depositAmount) {
        return SettlementFinalBill.builder()
                .billReference("BILL-" + ticketCode)
                .bookingReference(ticketCode)
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .postPromotionAmountVnd(depositAmount)
                .acceptedDepositAmountVnd(depositAmount)
                .remainingAmountVnd(0L)
                .currencyCode("VND")
                .build();
    }

    private SettlementTicketMlandRemedy createPendingRemedy(String ticketCode, Instant replacementStartAt) {
        Instant deadline = replacementStartAt.minus(24, ChronoUnit.HOURS);
        return SettlementTicketMlandRemedy.builder()
                .id(1L)
                .remedyReference("MREMEDY-" + UUID.randomUUID())
                .ticketCode(ticketCode)
                .groupCode(GROUP_CODE)
                .cancelledSessionReference(UUID.randomUUID().toString())
                .replacementSessionReferences(OFFERED_SESSION)
                .replacementStartAt(replacementStartAt)
                .answerDeadlineAt(deadline)
                .outcome("PENDING_ANSWER")
                .createdAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .updatedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
    }

    @Test
    @DisplayName("createOffer: creates remedy offer with answer deadline 24h before replacement start and notifies buyer")
    void createOffer_success() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 500L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 100_000L)));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.empty());
        when(remedyRepository.save(any(SettlementTicketMlandRemedy.class))).thenAnswer(i -> i.getArgument(0));

        Instant replacementStart = Instant.now().plus(72, ChronoUnit.HOURS);
        String cancelledSession = UUID.randomUUID().toString();
        List<String> replacementSessions = List.of(UUID.randomUUID().toString());

        SettlementTicketMlandRemedy result = service.createOffer(
                TICKET_CODE,
                cancelledSession,
                replacementSessions,
                replacementStart,
                "CORR-01"
        );

        assertThat(result).isNotNull();
        assertThat(result.getTicketCode()).isEqualTo(TICKET_CODE);
        assertThat(result.getGroupCode()).isEqualTo(GROUP_CODE);
        assertThat(result.getOutcome()).isEqualTo("PENDING_ANSWER");
        assertThat(result.getAnswerDeadlineAt()).isEqualTo(replacementStart.minus(24, ChronoUnit.HOURS));

        verify(notifier).notifyMlandRemedyOffer(eq(TICKET_CODE), eq(cancelledSession), anyString(), eq(result.getAnswerDeadlineAt()), eq("CORR-01"));
        verify(audits).save(argThat(a -> "MLAND_REMEDY_OFFER_CREATED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("createOffer: throws NOT_FOUND when ticket unknown")
    void createOffer_ticketNotFound() {
        when(booking.getTicketFolioContext("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOffer("UNKNOWN", "S1", List.of("S2"), Instant.now().plus(48, ChronoUnit.HOURS), "CORR"))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    @Test
    @DisplayName("createOffer: throws CONFLICT when final bill is not OPEN")
    void createOffer_billNotOpen() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementFinalBill bill = createBill(TICKET_CODE, 100_000L);
        bill.setLifecycleStatus("FINALIZED");
        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.createOffer(TICKET_CODE, "S1", List.of("S2"), Instant.now().plus(48, ChronoUnit.HOURS), "CORR"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FINAL_BILL_NOT_OPEN");
    }

    @Test
    @DisplayName("answerOffer: Member buyer accepts replacement before deadline -> sessions replaced in 003.2, outcome MOVED")
    void answerOffer_memberAcceptReplacement_success() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 500L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, OFFERED_SESSION);

        TicketRemedyAnswerResponseDto response = service.answerOffer(
                TICKET_CODE,
                request,
                IDEMPOTENCY_KEY,
                "CORR-01",
                memberActor
        );

        assertThat(response).isNotNull();
        assertThat(response.answer()).isEqualTo(TicketRemedyAnswer.ACCEPT_REPLACEMENT);
        assertThat(response.outcome()).isEqualTo("MOVED");
        assertThat(response.refundRequestReference()).isNull();
        assertThat(response.pointsReturned()).isZero();
        assertThat(response.voucherRestored()).isFalse();

        verify(operations).replaceTicketSessions(argThat(cmd ->
                cmd.type() == TicketReplacementType.MLAND
                        && cmd.lines().getFirst().ticketReference().equals(OPS_TICKET_REF)
                        && cmd.lines().getFirst().targetSessionReferences().equals(List.of(UUID.fromString(OFFERED_SESSION)))
        ));
        assertThat(response.selectedReplacementSessionReference()).isEqualTo(OFFERED_SESSION);
        verifyNoInteractions(bankRefunds);
        verify(audits).save(argThat(a -> "MLAND_REMEDY_ACCEPTED_REPLACEMENT".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("answerOffer: Member buyer chooses refund -> 100% bank refund, cancel seats, return points, restore voucher once, close folio")
    void answerOffer_memberChooseRefund_success() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 300L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

        // Collections: 50,000 deposit + 20,000 counter confirmed = 70,000 VND
        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET_CODE)).thenReturn(List.of(
                SettlementFolioCollection.builder().amountVnd(20_000L).status(FolioCollectionStatus.CONFIRMED).build()
        ));
        when(evidence.findByBookingReferenceAndConclusion(eq(TICKET_CODE), anyString())).thenReturn(List.of());

        SettlementRefundRequest refundReq = SettlementRefundRequest.builder()
                .requestReference("REFUND-M-01")
                .amountVnd(70_000L)
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build();
        when(bankRefunds.open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(70_000L), anyString()))
                .thenReturn(new BankRefundService.OpenedRefund(refundReq, "token-123"));

        when(operations.cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString()))
                .thenReturn(TicketCancellationOutcome.accepted(OPS_TICKET_REF, List.of()));

        // Loyalty: returns points + restores voucher
        BookingGroupLoyaltyOutcomeSource loyaltySource = new BookingGroupLoyaltyOutcomeSource(
                true, "SNAP-1", 1, Instant.now().plus(7, ChronoUnit.DAYS), List.of()
        );
        when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(loyaltySource);
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP_CODE)).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, GROUP_CODE, ComponentState.CONSUMED, ComponentState.CONSUMED, EarnState.PENDING, null)
        );
        when(loyalty.restoreCancelledWorkshopBenefits(any())).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, GROUP_CODE, ComponentState.RESTORED, ComponentState.RESTORED, EarnState.INELIGIBLE, null)
        );
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, null);

        TicketRemedyAnswerResponseDto response = service.answerOffer(
                TICKET_CODE,
                request,
                IDEMPOTENCY_KEY,
                "CORR-02",
                memberActor
        );

        assertThat(response).isNotNull();
        assertThat(response.answer()).isEqualTo(TicketRemedyAnswer.REFUND);
        assertThat(response.outcome()).isEqualTo("REFUND");
        assertThat(response.refundRequestReference()).isEqualTo("REFUND-M-01");
        assertThat(response.pointsReturned()).isEqualTo(300L);
        assertThat(response.voucherRestored()).isTrue();

        verify(bankRefunds).open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(70_000L), anyString());
        verify(operations).cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString());
        verify(loyalty).restoreCancelledWorkshopBenefits(argThat(auth ->
                auth.outcome() == OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION
                        && "L1-T1".equals(auth.partRef())
        ));
        // T113: money goes back, so the folio stays open until the bank refund is REFUNDED.
        verify(bills, never()).save(argThat(b -> SettlementFinalBillLifecycleStatus.CLOSED.name().equals(b.getLifecycleStatus())));
        verify(audits).save(argThat(a -> "MLAND_REMEDY_CHOSEN_REFUND".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("answerOffer: Guest buyer with valid buyerVerificationReference chooses refund -> succeeds")
    void answerOffer_guestBuyer_success() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET_CODE)).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(eq(TICKET_CODE), anyString())).thenReturn(List.of());

        SettlementRefundRequest refundReq = SettlementRefundRequest.builder()
                .requestReference("REFUND-G-01")
                .amountVnd(50_000L)
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build();
        when(bankRefunds.open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(50_000L), anyString()))
                .thenReturn(new BankRefundService.OpenedRefund(refundReq, "token-456"));

        when(operations.cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString()))
                .thenReturn(TicketCancellationOutcome.accepted(OPS_TICKET_REF, List.of()));
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String bvr = "bvr-" + "a".repeat(20);
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, bvr);

        TicketRemedyAnswerResponseDto response = service.answerOffer(
                TICKET_CODE,
                request,
                IDEMPOTENCY_KEY,
                "CORR-03",
                null // Guest unauthenticated
        );

        assertThat(response).isNotNull();
        assertThat(response.outcome()).isEqualTo("REFUND");
        assertThat(response.refundRequestReference()).isEqualTo("REFUND-G-01");
        verify(booking).consumeBuyerProof(argThat(cmd -> bvr.equals(cmd.buyerVerificationReference())));
    }

    @Test
    @DisplayName("answerOffer: counter Staff with buyerVerificationReference chooses refund -> succeeds")
    void answerOffer_counterStaff_success() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 50_000L)));
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET_CODE)).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(eq(TICKET_CODE), anyString())).thenReturn(List.of());

        SettlementRefundRequest refundReq = SettlementRefundRequest.builder()
                .requestReference("REFUND-S-01")
                .amountVnd(50_000L)
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build();
        when(bankRefunds.open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(50_000L), anyString()))
                .thenReturn(new BankRefundService.OpenedRefund(refundReq, "token-789"));

        when(operations.cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString()))
                .thenReturn(TicketCancellationOutcome.accepted(OPS_TICKET_REF, List.of()));
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String bvr = "bvr-" + "b".repeat(20);
        CurrentAccountActor staffActor = new CurrentAccountActor(888L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, bvr);

        TicketRemedyAnswerResponseDto response = service.answerOffer(
                TICKET_CODE,
                request,
                IDEMPOTENCY_KEY,
                "CORR-04",
                staffActor
        );

        assertThat(response).isNotNull();
        assertThat(response.outcome()).isEqualTo("REFUND");
        verify(booking).consumeBuyerProof(argThat(cmd -> bvr.equals(cmd.buyerVerificationReference())));
    }

    @Test
    @DisplayName("answerOffer: non-owner Member (or companion recipient) refused with 403 NOT_GROUP_OWNER")
    void answerOffer_nonOwnerMember_throws403() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));

        CurrentAccountActor strangerMember = new CurrentAccountActor(999L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, null);

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", strangerMember))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("NOT_GROUP_OWNER");
    }

    @Test
    @DisplayName("T116: answerOffer by Staff outside the ticket's location, or suspended, is refused before the proof")
    void answerOffer_staffOutOfScopeOrInactive_isRefused() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, Instant.now().plus(48, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, "bvr-" + "c".repeat(20));

        CurrentAccountActor otherBranch = new CurrentAccountActor(888L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(5L));
        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", otherBranch))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        CurrentAccountActor suspended = new CurrentAccountActor(888L, ActorRole.STAFF, ActorStatus.SUSPENDED, true, List.of(1L));
        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", suspended))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);

        verify(booking, never()).consumeBuyerProof(any());
        verify(bankRefunds, never()).open(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("answerOffer: Guest without buyerVerificationReference refused with 403 BUYER_VERIFICATION_REQUIRED")
    void answerOffer_guestWithoutProof_throws403() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));

        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, null);

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("BUYER_VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("answerOffer: expired buyerVerificationReference refused with 409 BUYER_VERIFICATION_EXPIRED")
    void answerOffer_expiredProof_throws409() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        Instant replacementStart = Instant.now().plus(48, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_EXPIRED));

        String bvr = "bvr-" + "c".repeat(20);
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, bvr);

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", null))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("BUYER_VERIFICATION_EXPIRED");
    }

    @Test
    @DisplayName("answerOffer: accepting replacement after 24h deadline before replacement starts -> throws 409 ANSWER_DEADLINE_EXPIRED")
    void answerOffer_acceptAfterDeadline_throws409() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        // Replacement starts in 10 hours, so answer deadline was 14 hours ago
        Instant replacementStart = Instant.now().plus(10, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, OFFERED_SESSION);

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("ANSWER_DEADLINE_EXPIRED");
        verify(booking, never()).consumeBuyerProof(any());
    }

    @Test
    @DisplayName("processUnansweredRemedies: default to refund when deadline passed without answer")
    void processUnansweredRemedies_defaultsToRefund() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 200L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));

        // Expired remedy: replacement was in 12 hours, deadline was 12 hours ago
        Instant replacementStart = Instant.now().plus(12, ChronoUnit.HOURS);
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, replacementStart);
        when(remedyRepository.findByOutcomeAndAnswerDeadlineAtLessThanEqual(eq("PENDING_ANSWER"), any(Instant.class)))
                .thenReturn(List.of(offer));

        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 100_000L)));
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 100_000L)));
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET_CODE)).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(eq(TICKET_CODE), anyString())).thenReturn(List.of());

        SettlementRefundRequest refundReq = SettlementRefundRequest.builder()
                .requestReference("REFUND-AUTO-01")
                .amountVnd(100_000L)
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build();
        when(bankRefunds.open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(100_000L), anyString()))
                .thenReturn(new BankRefundService.OpenedRefund(refundReq, "token-auto"));

        when(operations.cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString()))
                .thenReturn(TicketCancellationOutcome.accepted(OPS_TICKET_REF, List.of()));

        BookingGroupLoyaltyOutcomeSource loyaltySource = new BookingGroupLoyaltyOutcomeSource(
                true, "SNAP-AUTO", 1, Instant.now().plus(7, ChronoUnit.DAYS), List.of()
        );
        when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(loyaltySource);
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP_CODE)).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, GROUP_CODE, ComponentState.CONSUMED, ComponentState.CONSUMED, EarnState.PENDING, null)
        );
        when(loyalty.restoreCancelledWorkshopBenefits(any())).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, GROUP_CODE, ComponentState.RESTORED, ComponentState.RESTORED, EarnState.INELIGIBLE, null)
        );
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int processed = service.processUnansweredRemedies();

        assertThat(processed).isEqualTo(1);
        assertThat(offer.getOutcome()).isEqualTo("REFUND");
        assertThat(offer.getBuyerAnswer()).isNull(); // defaulted, not chosen
        assertThat(offer.getRefundRequestReference()).isEqualTo("REFUND-AUTO-01");
        assertThat(offer.getPointsReturned()).isEqualTo(200L);
        assertThat(offer.isVoucherRestored()).isTrue();

        verify(bankRefunds).open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(100_000L), anyString());
        verify(operations).cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString());
        verify(loyalty).restoreCancelledWorkshopBenefits(argThat(auth ->
                auth.outcome() == OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION
        ));
        // T113: money goes back, so the folio stays open until the bank refund is REFUNDED.
        verify(bills, never()).save(argThat(b -> SettlementFinalBillLifecycleStatus.CLOSED.name().equals(b.getLifecycleStatus())));
        verify(audits).save(argThat(a -> "MLAND_REMEDY_DEFAULTED_TO_REFUND".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("answerOffer: idempotent replay returns cached response without repeating side effects")
    void answerOffer_idempotentReplay() {
        SettlementTicketMlandRemedy stored = createPendingRemedy(TICKET_CODE, Instant.now().plus(48, ChronoUnit.HOURS));
        stored.setBuyerAnswer("ACCEPT_REPLACEMENT");
        stored.setOutcome("MOVED");
        stored.setAnsweredAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1")));
        when(idempotency.findOutcomeReference(anyString(), anyString())).thenReturn(Optional.of(stored.getRemedyReference()));
        when(remedyRepository.findByRemedyReference(stored.getRemedyReference())).thenReturn(Optional.of(stored));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, OFFERED_SESSION);

        TicketRemedyAnswerResponseDto response = service.answerOffer(
                TICKET_CODE,
                request,
                IDEMPOTENCY_KEY,
                "CORR-REPLAY",
                memberActor
        );

        assertThat(response).isNotNull();
        assertThat(response.outcome()).isEqualTo("MOVED");
        verifyNoInteractions(operations);
        verifyNoInteractions(bankRefunds);
    }

    // ---- T113: the folio closes only when the bank refund is REFUNDED ----

    @Test
    @DisplayName("T113: MlandBankRefundConfirmedEvent closes the remedy's folio once; other refunds change nothing")
    void bankRefundConfirmed_closesTheRemedyFolioOnce() {
        SettlementTicketMlandRemedy remedy = createPendingRemedy(TICKET_CODE, Instant.now().plus(48, ChronoUnit.HOURS));
        remedy.setOutcome("REFUND");
        remedy.setRefundRequestReference("REFUND-M-01");
        when(remedyRepository.findByRemedyReference(remedy.getRemedyReference())).thenReturn(Optional.of(remedy));
        SettlementFinalBill bill = createBill(TICKET_CODE, 50_000L);
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(bill));

        // A confirmation of a different refund request with the same source is ignored.
        service.onBankRefundConfirmed(new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                "REFUND-OTHER", TICKET_CODE, remedy.getRemedyReference(), 50_000L, Instant.now()));
        verify(bills, never()).save(any());

        service.onBankRefundConfirmed(new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                "REFUND-M-01", TICKET_CODE, remedy.getRemedyReference(), 50_000L, Instant.now()));
        assertThat(bill.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.CLOSED.name());
        verify(audits).save(argThat(a -> "TICKET_FOLIO_CLOSED_MLAND_REMEDY".equals(a.getActionCode())));

        // Replay: already closed, nothing more is written.
        service.onBankRefundConfirmed(new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                "REFUND-M-01", TICKET_CODE, remedy.getRemedyReference(), 50_000L, Instant.now()));
        verify(bills, times(1)).save(any());
    }

    // ---- T112: several sessions offered, the buyer chooses one (decided 2026-11-14) -------------------------

    private static final String SESSION_EARLY = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String SESSION_LATE = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    private SettlementTicketMlandRemedy twoSessionOffer(Instant earlyStart, Instant lateStart) {
        SettlementTicketMlandRemedy offer = createPendingRemedy(TICKET_CODE, lateStart);
        offer.setReplacementSessionReferences(SESSION_EARLY + "," + SESSION_LATE);
        offer.setReplacementOptions(SESSION_EARLY + "|" + earlyStart + "|" + earlyStart.plus(2, ChronoUnit.HOURS)
                + ";" + SESSION_LATE + "|" + lateStart + "|");
        return offer;
    }

    @Test
    @DisplayName("createOffer: stores every session, the deadline is 24 h before the LATEST one and the email lists them all")
    void createOffer_manySessions_deadlineFromLatest() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 100_000L)));
        when(remedyRepository.save(any(SettlementTicketMlandRemedy.class))).thenAnswer(i -> i.getArgument(0));

        Instant early = Instant.now().plus(50, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant late = Instant.now().plus(120, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        SettlementTicketMlandRemedy result = service.createOffer(TICKET_CODE, "cancelled-1", List.of(
                new TicketMlandRemedyService.ReplacementOption(SESSION_LATE, late, null),
                new TicketMlandRemedyService.ReplacementOption(SESSION_EARLY, early, early.plus(2, ChronoUnit.HOURS))),
                "CORR-MANY");

        assertThat(result.getAnswerDeadlineAt()).isEqualTo(late.minus(24, ChronoUnit.HOURS));
        assertThat(result.getReplacementSessionReferences()).isEqualTo(SESSION_EARLY + "," + SESSION_LATE);
        ArgumentCaptor<String> listed = ArgumentCaptor.forClass(String.class);
        verify(notifier).notifyMlandRemedyOffer(eq(TICKET_CODE), eq("cancelled-1"), listed.capture(),
                eq(result.getAnswerDeadlineAt()), eq("CORR-MANY"));
        assertThat(listed.getValue()).contains(SESSION_EARLY).contains(SESSION_LATE);
    }

    @Test
    @DisplayName("createOffer: a replay for the same ticket and cancelled session returns the stored remedy")
    void createOffer_replay_isIdempotent() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy stored = createPendingRemedy(TICKET_CODE, Instant.now().plus(72, ChronoUnit.HOURS));
        when(remedyRepository.findFirstByTicketCodeAndCancelledSessionReferenceOrderByCreatedAtDesc(TICKET_CODE, "cancelled-1"))
                .thenReturn(Optional.of(stored));

        SettlementTicketMlandRemedy result = service.createOffer(TICKET_CODE, "cancelled-1", List.of(
                new TicketMlandRemedyService.ReplacementOption(SESSION_EARLY, Instant.now().plus(72, ChronoUnit.HOURS), null)),
                "CORR");

        assertThat(result).isSameAs(stored);
        verify(remedyRepository, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("answerOffer: choosing a session that was not offered -> 409 REPLACEMENT_SESSION_NOT_OFFERED, buyer proof not spent")
    void answerOffer_sessionNotOffered_throws409() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(60, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null,
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc");

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE, request, IDEMPOTENCY_KEY, "CORR", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REPLACEMENT_SESSION_NOT_OFFERED");
        verify(booking, never()).consumeBuyerProof(any());
        verifyNoInteractions(bankRefunds);
    }

    @Test
    @DisplayName("answerOffer: accepting without naming a session -> validation REPLACEMENT_SESSION_REQUIRED")
    void answerOffer_sessionRequired() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(60, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));

        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE,
                new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null), IDEMPOTENCY_KEY, "CORR", memberActor))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("REPLACEMENT_SESSION_REQUIRED");
    }

    @Test
    @DisplayName("answerOffer: the early session is within 24 h but the late one is not -> only the late one can be chosen")
    void answerOffer_earlySessionTooSoon_lateOneChosen() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(10, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE,
                new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, SESSION_EARLY),
                IDEMPOTENCY_KEY, "CORR", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("ANSWER_DEADLINE_EXPIRED");

        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TicketRemedyAnswerResponseDto response = service.answerOffer(TICKET_CODE,
                new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, SESSION_LATE),
                IDEMPOTENCY_KEY, "CORR", memberActor);

        assertThat(response.outcome()).isEqualTo("MOVED");
        assertThat(response.selectedReplacementSessionReference()).isEqualTo(SESSION_LATE);
    }

    @Test
    @DisplayName("answerOffer: the chosen session filled up -> 409 REPLACEMENT_SESSION_UNAVAILABLE and the offer stays open")
    void answerOffer_chosenSessionFull_throws409() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(60, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
        when(operations.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY, List.of(0)));
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        assertThatThrownBy(() -> service.answerOffer(TICKET_CODE,
                new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, SESSION_EARLY),
                IDEMPOTENCY_KEY, "CORR", memberActor))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REPLACEMENT_SESSION_UNAVAILABLE");
        assertThat(offer.getOutcome()).isEqualTo("PENDING_ANSWER");
        verify(remedyRepository, never()).save(any());
    }

    @Test
    @DisplayName("answerOffer: a two-session ticket moves only the cancelled session, the other keeps its place")
    void answerOffer_twoSessionTicket_replacesOnlyCancelledSession() {
        String kept = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
        String cancelled = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
        TicketFolioContext ctx = new TicketFolioContext(TICKET_CODE, GROUP_CODE, "LINE-1", "PKG-01", 1L,
                List.of(kept, cancelled), BUYER_ACCOUNT_ID, OPS_TICKET_REF.toString(),
                Instant.now().plus(48, ChronoUnit.HOURS), 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(60, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        offer.setCancelledSessionReference(cancelled);
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        when(remedyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        service.answerOffer(TICKET_CODE,
                new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, SESSION_LATE),
                IDEMPOTENCY_KEY, "CORR", memberActor);

        verify(operations).replaceTicketSessions(argThat(cmd -> cmd.lines().getFirst().targetSessionReferences()
                .equals(List.of(UUID.fromString(kept), UUID.fromString(SESSION_LATE)))));
    }

    @Test
    @DisplayName("openDirectRefund: no equivalent session -> the remedy is created and defaults to the 100% bank refund at once")
    void openDirectRefund_refundsAtOnce() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 80_000L)));
        lenient().when(bills.findByBookingReferenceForUpdate(TICKET_CODE)).thenReturn(Optional.of(createBill(TICKET_CODE, 80_000L)));
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET_CODE)).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(eq(TICKET_CODE), anyString())).thenReturn(List.of());
        when(remedyRepository.save(any(SettlementTicketMlandRemedy.class))).thenAnswer(i -> i.getArgument(0));
        SettlementRefundRequest refundReq = SettlementRefundRequest.builder().requestReference("REFUND-DIRECT-01")
                .amountVnd(80_000L).status(RefundRequestStatus.PENDING_ACCOUNT).build();
        when(bankRefunds.open(eq(TICKET_CODE), eq(GROUP_CODE), eq(RefundReason.MLAND_CANCELLATION), anyString(), eq(80_000L), anyString()))
                .thenReturn(new BankRefundService.OpenedRefund(refundReq, "token-direct"));
        when(operations.cancelTicket(eq(OPS_TICKET_REF), anyString(), anyString(), anyString()))
                .thenReturn(TicketCancellationOutcome.accepted(OPS_TICKET_REF, List.of()));
        when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(BookingGroupLoyaltyOutcomeSource.none());

        SettlementTicketMlandRemedy result = service.openDirectRefund(TICKET_CODE, "cancelled-1", "CORR-DIRECT");

        assertThat(result.getOutcome()).isEqualTo("REFUND");
        assertThat(result.getRefundRequestReference()).isEqualTo("REFUND-DIRECT-01");
        verify(notifier).notifyMlandDirectRefund(TICKET_CODE, "cancelled-1", "CORR-DIRECT");
        verify(notifier, never()).notifyMlandRemedyOffer(any(), any(), any(), any(), any());
        verify(audits).save(argThat(a -> "MLAND_REMEDY_DIRECT_REFUND_OPENED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("getOffer: lists every session; one inside 24 h or no longer free is not selectable")
    void getOffer_marksSelectableSessions() {
        TicketFolioContext ctx = createContext(TICKET_CODE, OPS_TICKET_REF, 0L, "L1-T1");
        when(booking.getTicketFolioContext(TICKET_CODE)).thenReturn(Optional.of(ctx));
        SettlementTicketMlandRemedy offer = twoSessionOffer(Instant.now().plus(10, ChronoUnit.HOURS),
                Instant.now().plus(100, ChronoUnit.HOURS));
        when(remedyRepository.findByTicketCodeAndOutcome(TICKET_CODE, "PENDING_ANSWER")).thenReturn(Optional.of(offer));
        when(operations.equivalentReplacementSessions(eq(OPS_TICKET_REF), any())).thenReturn(
                new EquivalentReplacementSessions(List.of(new EquivalentReplacementSessions.Candidate(
                        UUID.fromString(SESSION_LATE), Instant.now().plus(100, ChronoUnit.HOURS), null))));
        CurrentAccountActor memberActor = new CurrentAccountActor(BUYER_ACCOUNT_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        TicketRemedyOfferDto dto = service.getOffer(TICKET_CODE, memberActor);

        assertThat(dto.offeredSessions()).extracting(OfferedSessionDto::sessionReference)
                .containsExactly(SESSION_EARLY, SESSION_LATE);
        assertThat(dto.offeredSessions()).extracting(OfferedSessionDto::selectable).containsExactly(false, true);
    }
}
