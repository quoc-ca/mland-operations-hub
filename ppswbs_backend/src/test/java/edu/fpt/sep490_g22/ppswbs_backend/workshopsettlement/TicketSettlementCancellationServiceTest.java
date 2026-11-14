package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketCancellationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketCancellationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketCancellationDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketCancellation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class TicketSettlementCancellationServiceTest {

    @Mock
    private SettlementTicketCancellationRepository cancellationRepository;
    @Mock
    private SettlementFinalBillRepository bills;
    @Mock
    private SettlementBusinessAuditRepository audits;
    @Mock
    private WorkshopBookingSettlementAdapter booking;
    @Mock
    private WorkshopOperationsSettlementAdapter operations;
    @Mock
    private LoyaltyFacade loyalty;
    @Mock
    private SettlementIdempotencyService idempotency;

    private TicketSettlementCancellationService service;

    private static final String GROUP_CODE = "GRP-20261114";
    private static final String TICKET_1 = "TK-001";
    private static final String TICKET_2 = "TK-002";
    private static final UUID OPS_REF_1 = UUID.randomUUID();
    private static final UUID OPS_REF_2 = UUID.randomUUID();
    private static final Long BUYER_ACCOUNT_ID = 100L;
    private static final String VALID_IDEMPOTENCY_KEY = "idempotency-key-123456789";

    @BeforeEach
    void setUp() {
        service = new TicketSettlementCancellationService(
                cancellationRepository, bills, audits, booking, operations, loyalty, idempotency
        );
        lenient().when(idempotency.findOutcomeReference(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<String> supplier = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, supplier.get());
        });
    }

    private TicketFolioContext createContext(String ticketCode, UUID opsRef, Instant firstSessionStart, long pointsUsed, String partRef) {
        return new TicketFolioContext(
                ticketCode,
                GROUP_CODE,
                "LINE-01",
                "PKG-01",
                1L,
                List.of("SES-01"),
                BUYER_ACCOUNT_ID,
                opsRef.toString(),
                firstSessionStart,
                pointsUsed,
                partRef
        );
    }

    private SettlementFinalBill createOpenBill(String ticketCode) {
        return SettlementFinalBill.builder()
                .billReference("BILL-" + ticketCode)
                .bookingReference(ticketCode)
                .groupReference(GROUP_CODE)
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .build();
    }

    private CurrentAccountActor memberActor(Long accountId) {
        return new CurrentAccountActor(accountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
    }

    private CurrentAccountActor staffActor(Long accountId) {
        return new CurrentAccountActor(accountId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    }

    @Nested
    @DisplayName("Single Ticket Cancellation (FR-041, FR-042)")
    class SingleTicketTests {

        @Test
        @DisplayName("24h00m before first session is VALID_CANCELLATION: returns points and releases capacity")
        void validCancellationAt24HoursBefore() {
            // First session is exactly 24 hours from now (or later)
            Instant now = Instant.now();
            Instant firstSessionStart = now.plus(24, ChronoUnit.HOURS).plusSeconds(60);

            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 500L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);

            SettlementFinalBill bill = createOpenBill(TICKET_1);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill));
            when(bills.findByBookingReferenceForUpdate(TICKET_1)).thenReturn(Optional.of(bill));

            // Member owner authenticated
            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);
            when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

            when(operations.cancelTicket(eq(OPS_REF_1), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_1, List.of(UUID.randomUUID())));

            BookingGroupLoyaltyOutcomeSource loyaltySource = new BookingGroupLoyaltyOutcomeSource(
                    true, "SNAP-01", 1L, now.plus(7, ChronoUnit.DAYS),
                    List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_1, OPS_REF_1.toString()))
            );
            when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(loyaltySource);

            BenefitResult loyaltyResult = new BenefitResult(
                    SourceKind.WORKSHOP, GROUP_CODE, ComponentState.RELEASED, ComponentState.RESTORED,
                    EarnState.INELIGIBLE, now
            );
            when(loyalty.restoreCancelledWorkshopBenefits(any())).thenReturn(loyaltyResult);

            CancellationRequestDto request = new CancellationRequestDto("Change of plans", null);
            TicketCancellationDto result = service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-1", actor);

            assertThat(result.ticketCode()).isEqualTo(TICKET_1);
            assertThat(result.benefitOutcome()).isEqualTo("VALID_CANCELLATION");
            assertThat(result.pointsReturned()).isEqualTo(500L);
            assertThat(result.voucherRestored()).isTrue();

            assertThat(bill.getLifecycleStatus()).isEqualTo("CLOSED");
            verify(bills).save(bill);
            verify(cancellationRepository).save(argThat(c ->
                    c.getTicketCode().equals(TICKET_1)
                            && "VALID_CANCELLATION".equals(c.getBenefitOutcome())
                            && c.getPointsReturned() == 500L
                            && c.isVoucherRestored()
            ));
        }

        @Test
        @DisplayName("23h59m before first session is LATE_CANCELLATION: 0 points returned, capacity still released")
        void lateCancellationLessThan24HoursBefore() {
            // First session is 23h59m from now (less than 24h)
            Instant now = Instant.now();
            Instant firstSessionStart = now.plus(23, ChronoUnit.HOURS).plus(59, ChronoUnit.MINUTES);

            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 500L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);

            SettlementFinalBill bill = createOpenBill(TICKET_1);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill));
            when(bills.findByBookingReferenceForUpdate(TICKET_1)).thenReturn(Optional.of(bill));

            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);
            when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

            when(operations.cancelTicket(eq(OPS_REF_1), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_1, List.of(UUID.randomUUID())));

            BookingGroupLoyaltyOutcomeSource loyaltySource = new BookingGroupLoyaltyOutcomeSource(
                    true, "SNAP-01", 1L, now.plus(7, ChronoUnit.DAYS),
                    List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_1, OPS_REF_1.toString()))
            );
            when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(loyaltySource);

            BenefitResult loyaltyResult = new BenefitResult(
                    SourceKind.WORKSHOP, GROUP_CODE, ComponentState.CONSUMED, ComponentState.CONSUMED,
                    EarnState.INELIGIBLE, now
            );
            when(loyalty.finishIneligibleEarn(any())).thenReturn(loyaltyResult);

            CancellationRequestDto request = new CancellationRequestDto("Late cancellation", null);
            TicketCancellationDto result = service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-2", actor);

            assertThat(result.ticketCode()).isEqualTo(TICKET_1);
            assertThat(result.benefitOutcome()).isEqualTo("LATE_CANCELLATION");
            assertThat(result.pointsReturned()).isEqualTo(0L);
            assertThat(result.voucherRestored()).isFalse();

            verify(loyalty).finishIneligibleEarn(argThat(auth ->
                    auth.outcome() == OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL
                            && "L0-T1".equals(auth.partRef())
            ));
            assertThat(bill.getLifecycleStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("First session already started throws 409 SESSION_ALREADY_STARTED")
        void sessionAlreadyStartedThrowsConflict() {
            Instant now = Instant.now();
            Instant firstSessionStart = now.minusSeconds(10); // started 10 seconds ago

            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 0L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);

            SettlementFinalBill bill = createOpenBill(TICKET_1);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill));

            CancellationRequestDto request = new CancellationRequestDto("Tried to cancel after start", null);
            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);

            assertThatThrownBy(() -> service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-3", actor))
                    .isInstanceOf(WorkshopSettlementConflictException.class)
                    .hasMessage("SESSION_ALREADY_STARTED");

            verifyNoInteractions(operations);
            verifyNoInteractions(loyalty);
        }

        @Test
        @DisplayName("Unknown ticket throws 404 TICKET_NOT_FOUND")
        void unknownTicketThrowsNotFound() {
            when(booking.getTicketFolioContext("UNKNOWN")).thenReturn(Optional.empty());

            CancellationRequestDto request = new CancellationRequestDto("Cancel non-existent", null);
            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);

            assertThatThrownBy(() -> service.cancelTicket("UNKNOWN", request, VALID_IDEMPOTENCY_KEY, "corr-4", actor))
                    .isInstanceOf(WorkshopSettlementNotFoundException.class)
                    .hasMessage("TICKET_NOT_FOUND");
        }

        @Test
        @DisplayName("Member caller who is NOT the group owner throws 403 NOT_GROUP_OWNER")
        void strangerMemberThrowsForbidden() {
            Instant firstSessionStart = Instant.now().plus(48, ChronoUnit.HOURS);
            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 0L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(createOpenBill(TICKET_1)));

            // Caller is Member with account ID 999L != BUYER_ACCOUNT_ID (100L)
            CurrentAccountActor stranger = memberActor(999L);

            CancellationRequestDto request = new CancellationRequestDto("Trying to cancel someone else's ticket", null);

            assertThatThrownBy(() -> service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-5", stranger))
                    .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                    .hasMessage("NOT_GROUP_OWNER");
        }

        @Test
        @DisplayName("Guest buyer without verification reference throws 403 BUYER_VERIFICATION_REQUIRED")
        void guestWithoutProofThrowsForbidden() {
            Instant firstSessionStart = Instant.now().plus(48, ChronoUnit.HOURS);
            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 0L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(createOpenBill(TICKET_1)));

            CancellationRequestDto request = new CancellationRequestDto("Guest cancel without OTP ref", null);

            assertThatThrownBy(() -> service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-6", null))
                    .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                    .hasMessage("BUYER_VERIFICATION_REQUIRED");
        }

        @Test
        @DisplayName("Guest buyer with valid OTP verification reference succeeds")
        void guestWithValidProofSucceeds() {
            Instant firstSessionStart = Instant.now().plus(48, ChronoUnit.HOURS);
            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 0L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);

            SettlementFinalBill bill = createOpenBill(TICKET_1);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill));
            when(bills.findByBookingReferenceForUpdate(TICKET_1)).thenReturn(Optional.of(bill));

            when(booking.consumeBuyerProof(argThat(cmd ->
                    "OTP-REF-123".equals(cmd.buyerVerificationReference()) && cmd.memberAccountId() == null
            ))).thenReturn(ConsumeBuyerProofResult.accepted());

            when(operations.cancelTicket(eq(OPS_REF_1), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_1, List.of(UUID.randomUUID())));

            CancellationRequestDto request = new CancellationRequestDto("Guest cancel with proof", "OTP-REF-123");
            TicketCancellationDto result = service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-7", null);

            assertThat(result.ticketCode()).isEqualTo(TICKET_1);
            assertThat(result.benefitOutcome()).isEqualTo("VALID_CANCELLATION");

            verify(cancellationRepository).save(argThat(c ->
                    "GUEST".equals(c.getRequesterRole())
                            && "ONLINE".equals(c.getChannel())
                            && "OTP-REF-123".equals(c.getBuyerProof())
            ));
        }

        @Test
        @DisplayName("Staff at counter acting on buyer's behalf succeeds with channel STAFF")
        void staffAtCounterSucceeds() {
            Instant firstSessionStart = Instant.now().plus(48, ChronoUnit.HOURS);
            TicketFolioContext context = createContext(TICKET_1, OPS_REF_1, firstSessionStart, 0L, "L0-T1");
            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(context));
            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);

            SettlementFinalBill bill = createOpenBill(TICKET_1);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill));
            when(bills.findByBookingReferenceForUpdate(TICKET_1)).thenReturn(Optional.of(bill));

            when(booking.consumeBuyerProof(argThat(cmd ->
                    "COUNTER-PROOF-456".equals(cmd.buyerVerificationReference())
            ))).thenReturn(ConsumeBuyerProofResult.accepted());

            when(operations.cancelTicket(eq(OPS_REF_1), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_1, List.of(UUID.randomUUID())));

            CurrentAccountActor staffActor = staffActor(50L);
            CancellationRequestDto request = new CancellationRequestDto("Buyer requested cancellation at counter", "COUNTER-PROOF-456");

            TicketCancellationDto result = service.cancelTicket(TICKET_1, request, VALID_IDEMPOTENCY_KEY, "corr-8", staffActor);

            assertThat(result.ticketCode()).isEqualTo(TICKET_1);
            verify(cancellationRepository).save(argThat(c ->
                    "STAFF".equals(c.getRequesterRole())
                            && "STAFF".equals(c.getChannel())
                            && Long.valueOf(50L).equals(c.getRequesterAccountId())
                            && "COUNTER-PROOF-456".equals(c.getBuyerProof())
            ));
        }
    }

    @Nested
    @DisplayName("Group Multi-Ticket Cancellation All-or-None (FR-041, FR-042)")
    class GroupTicketTests {

        @Test
        @DisplayName("All-or-none: when 1 of 2 tickets cannot be cancelled, none is cancelled and 409 reports failingTicketCodes")
        void allOrNoneRefusesWhenOneTicketCannotBeCancelled() {
            Instant now = Instant.now();
            Instant future = now.plus(48, ChronoUnit.HOURS);
            Instant past = now.minusSeconds(60); // already started!

            TicketFolioContext ctx1 = createContext(TICKET_1, OPS_REF_1, future, 100L, "L0-T1");
            TicketFolioContext ctx2 = createContext(TICKET_2, OPS_REF_2, past, 100L, "L0-T2");

            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(ctx1));
            when(booking.getTicketFolioContext(TICKET_2)).thenReturn(Optional.of(ctx2));

            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);
            when(cancellationRepository.existsByTicketCode(TICKET_2)).thenReturn(false);

            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(createOpenBill(TICKET_1)));
            when(bills.findByBookingReference(TICKET_2)).thenReturn(Optional.of(createOpenBill(TICKET_2)));

            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);
            GroupCancellationRequestDto request = new GroupCancellationRequestDto(
                    "Cancel both tickets", null, List.of(TICKET_1, TICKET_2)
            );

            assertThatThrownBy(() -> service.cancelGroup(GROUP_CODE, request, VALID_IDEMPOTENCY_KEY, "corr-group-1", actor))
                    .isInstanceOf(GroupCancellationConflictException.class)
                    .satisfies(ex -> {
                        GroupCancellationConflictException conflict = (GroupCancellationConflictException) ex;
                        assertThat(conflict.getCode()).isEqualTo("GROUP_CANCELLATION_CONFLICT");
                        assertThat(conflict.getFailingTicketCodes()).containsExactly(TICKET_2);
                    });

            verifyNoInteractions(operations);
            verify(booking, never()).consumeBuyerProof(any());
            verify(cancellationRepository, never()).save(any());
        }

        @Test
        @DisplayName("All tickets eligible: both cancelled, voucher restored once on the last ticket")
        void allTicketsEligibleBothCancelledVoucherRestoredOnLast() {
            Instant now = Instant.now();
            Instant future = now.plus(48, ChronoUnit.HOURS);

            TicketFolioContext ctx1 = createContext(TICKET_1, OPS_REF_1, future, 200L, "L0-T1");
            TicketFolioContext ctx2 = createContext(TICKET_2, OPS_REF_2, future, 300L, "L0-T2");

            when(booking.getTicketFolioContext(TICKET_1)).thenReturn(Optional.of(ctx1));
            when(booking.getTicketFolioContext(TICKET_2)).thenReturn(Optional.of(ctx2));

            when(cancellationRepository.existsByTicketCode(TICKET_1)).thenReturn(false);
            when(cancellationRepository.existsByTicketCode(TICKET_2)).thenReturn(false);

            SettlementFinalBill bill1 = createOpenBill(TICKET_1);
            SettlementFinalBill bill2 = createOpenBill(TICKET_2);
            when(bills.findByBookingReference(TICKET_1)).thenReturn(Optional.of(bill1));
            when(bills.findByBookingReference(TICKET_2)).thenReturn(Optional.of(bill2));
            when(bills.findByBookingReferenceForUpdate(TICKET_1)).thenReturn(Optional.of(bill1));
            when(bills.findByBookingReferenceForUpdate(TICKET_2)).thenReturn(Optional.of(bill2));

            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);
            when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());

            when(operations.cancelTicket(eq(OPS_REF_1), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_1, List.of(UUID.randomUUID())));
            when(operations.cancelTicket(eq(OPS_REF_2), anyString(), anyString(), anyString()))
                    .thenReturn(TicketCancellationOutcome.accepted(OPS_REF_2, List.of(UUID.randomUUID())));

            BookingGroupLoyaltyOutcomeSource loyaltySource = new BookingGroupLoyaltyOutcomeSource(
                    true, "SNAP-01", 1L, now.plus(7, ChronoUnit.DAYS),
                    List.of(
                            new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_1, OPS_REF_1.toString()),
                            new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_2, OPS_REF_2.toString())
                    )
            );
            when(booking.groupLoyaltyOutcomeSource(GROUP_CODE)).thenReturn(loyaltySource);

            // On ticket 1: voucher not yet restored (ticket 2 still active)
            BenefitResult result1 = new BenefitResult(
                    SourceKind.WORKSHOP, GROUP_CODE, ComponentState.RELEASED, ComponentState.CONSUMED,
                    EarnState.PENDING, now
            );
            // On ticket 2: all tickets cancelled -> voucher restored!
            BenefitResult result2 = new BenefitResult(
                    SourceKind.WORKSHOP, GROUP_CODE, ComponentState.RELEASED, ComponentState.RESTORED,
                    EarnState.INELIGIBLE, now
            );
            when(loyalty.restoreCancelledWorkshopBenefits(any()))
                    .thenReturn(result1)
                    .thenReturn(result2);

            GroupCancellationRequestDto request = new GroupCancellationRequestDto(
                    "Cancel group tickets", null, List.of(TICKET_1, TICKET_2)
            );

            GroupCancellationResponseDto response = service.cancelGroup(
                    GROUP_CODE, request, VALID_IDEMPOTENCY_KEY, "corr-group-2", actor
            );

            assertThat(response.groupCode()).isEqualTo(GROUP_CODE);
            assertThat(response.tickets()).hasSize(2);

            TicketCancellationDto t1 = response.tickets().get(0);
            assertThat(t1.ticketCode()).isEqualTo(TICKET_1);
            assertThat(t1.benefitOutcome()).isEqualTo("VALID_CANCELLATION");
            assertThat(t1.pointsReturned()).isEqualTo(200L);
            assertThat(t1.voucherRestored()).isFalse();

            TicketCancellationDto t2 = response.tickets().get(1);
            assertThat(t2.ticketCode()).isEqualTo(TICKET_2);
            assertThat(t2.benefitOutcome()).isEqualTo("VALID_CANCELLATION");
            assertThat(t2.pointsReturned()).isEqualTo(300L);
            assertThat(t2.voucherRestored()).isTrue();

            assertThat(bill1.getLifecycleStatus()).isEqualTo("CLOSED");
            assertThat(bill2.getLifecycleStatus()).isEqualTo("CLOSED");
            verify(cancellationRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("Idempotent replay with same Idempotency-Key returns recorded result without re-executing")
        void idempotentReplayReturnsRecordedResult() {
            when(idempotency.findOutcomeReference(eq("CANCEL-" + VALID_IDEMPOTENCY_KEY), anyString()))
                    .thenReturn(Optional.of("GCAN-EXISTING"));

            SettlementTicketCancellation record1 = SettlementTicketCancellation.builder()
                    .cancellationReference("TCAN-1")
                    .groupRequestReference("GCAN-EXISTING")
                    .ticketCode(TICKET_1)
                    .groupCode(GROUP_CODE)
                    .benefitOutcome("VALID_CANCELLATION")
                    .pointsReturned(150L)
                    .voucherRestored(false)
                    .build();
            SettlementTicketCancellation record2 = SettlementTicketCancellation.builder()
                    .cancellationReference("TCAN-2")
                    .groupRequestReference("GCAN-EXISTING")
                    .ticketCode(TICKET_2)
                    .groupCode(GROUP_CODE)
                    .benefitOutcome("VALID_CANCELLATION")
                    .pointsReturned(250L)
                    .voucherRestored(true)
                    .build();

            when(cancellationRepository.findByGroupRequestReferenceOrderByTicketCodeAsc("GCAN-EXISTING"))
                    .thenReturn(List.of(record1, record2));

            GroupCancellationRequestDto request = new GroupCancellationRequestDto(
                    "Replayed cancel request", null, List.of(TICKET_1, TICKET_2)
            );
            CurrentAccountActor actor = memberActor(BUYER_ACCOUNT_ID);

            GroupCancellationResponseDto response = service.cancelGroup(
                    GROUP_CODE, request, VALID_IDEMPOTENCY_KEY, "corr-group-replay", actor
            );

            assertThat(response.groupCode()).isEqualTo(GROUP_CODE);
            assertThat(response.tickets()).hasSize(2);
            assertThat(response.tickets().get(0).pointsReturned()).isEqualTo(150L);
            assertThat(response.tickets().get(1).pointsReturned()).isEqualTo(250L);
            assertThat(response.tickets().get(1).voucherRestored()).isTrue();

            verifyNoInteractions(operations);
            verifyNoInteractions(loyalty);
            verify(booking, never()).consumeBuyerProof(any());
        }
    }
}

