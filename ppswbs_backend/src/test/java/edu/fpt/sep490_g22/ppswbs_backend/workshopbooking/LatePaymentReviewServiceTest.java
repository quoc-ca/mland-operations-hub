package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.GroupCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LatePaymentReviewService} with focus on 003.1 T094 / FR-040 Condition 3:
 * (1) recalculating automatic workshop discount,
 * (2) package/ticket prices unchanged,
 * (3) collected amount matches,
 * (4) vouchers and points still fail condition 3.
 */
class LatePaymentReviewServiceTest {

    private BookingGroupRepository groups;
    private BookingGroupLineRepository lines;
    private TicketBookingRepository tickets;
    private TicketPriceSnapshotRepository snapshots;
    private LatePaymentReviewRepository reviews;
    private BookingAuditEvidenceRepository auditEvidence;
    private GroupConfirmationService confirmation;
    private WorkshopOperationsAdapter operations;
    private GroupNotificationService notifications;
    private LatePaymentRefundPort refunds;
    private WorkshopBookingAuthorizationService authorization;
    private BookingIdempotencyService idempotency;
    private ApplicationEventPublisher events;
    private ObjectMapper mapper;
    private ManagerAlertRecipientSource managerRecipients;
    private WorkshopCartQuoteRepository quotes;
    private WorkshopCartBenefitService benefits;

    private LatePaymentReviewService service;

    private static final String GROUP_CODE = "GRP-TEST-LATE-01";
    private static final String MERCHANT_REF = "MERCHANT-01";
    private static final long PACKAGE_ID = 100L;
    private static final UUID SESSION_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        lines = mock(BookingGroupLineRepository.class);
        tickets = mock(TicketBookingRepository.class);
        snapshots = mock(TicketPriceSnapshotRepository.class);
        reviews = mock(LatePaymentReviewRepository.class);
        auditEvidence = mock(BookingAuditEvidenceRepository.class);
        confirmation = mock(GroupConfirmationService.class);
        operations = mock(WorkshopOperationsAdapter.class);
        notifications = mock(GroupNotificationService.class);
        refunds = mock(LatePaymentRefundPort.class);
        authorization = mock(WorkshopBookingAuthorizationService.class);
        idempotency = mock(BookingIdempotencyService.class);
        events = mock(ApplicationEventPublisher.class);
        mapper = new ObjectMapper().findAndRegisterModules();
        managerRecipients = mock(ManagerAlertRecipientSource.class);
        quotes = mock(WorkshopCartQuoteRepository.class);
        benefits = mock(WorkshopCartBenefitService.class);

        when(managerRecipients.activeManagerEmails()).thenReturn(List.of());
        when(reviews.save(any())).thenAnswer(inv -> {
            LatePaymentReview r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });

        service = new LatePaymentReviewService(
                groups, lines, tickets, snapshots, reviews, auditEvidence, confirmation,
                operations, notifications, refunds, authorization, idempotency, events,
                mapper, managerRecipients, quotes, benefits
        );
    }

    private record TestSetup(BookingGroup group, BookingGroupLine line, TicketBooking ticket, TicketPriceSnapshot snapshot) { }

    private TestSetup createFixture(long packagePrice, long discountVnd, long pointsUsed, long pointDiscountVnd,
                                    String benefitSelectionJson, Long buyerMemberId) throws Exception {
        long collected = packagePrice - discountVnd - pointDiscountVnd;
        Instant now = Instant.now();
        Instant futureSessionStart = now.plus(Duration.ofHours(5));

        BookingGroup group = BookingGroup.builder()
                .id(1L)
                .groupCode(GROUP_CODE)
                .buyerMemberAccountId(buyerMemberId)
                .contactEmail("buyer@example.com")
                .contactPhone("0900000000")
                .groupStatus(BookingStatus.EXPIRED)
                .paymentMode(GroupPaymentMode.VNPAY)
                .groupCollectionVnd(collected)
                .holdExpiresAt(now.minus(Duration.ofMinutes(10)))
                .paymentDeadlineAt(now.minus(Duration.ofMinutes(12)))
                .voucherUseReference(discountVnd > 0 || benefitSelectionJson != null ? "acc-101" : null)
                .build();

        BookingGroupLine line = BookingGroupLine.builder()
                .id(1L)
                .group(group)
                .lineIndex(0)
                .lineReference(GROUP_CODE + "-L0")
                .packageId(PACKAGE_ID)
                .packageNameSnapshot("Ceramic Class")
                .pricePerParticipantVnd(packagePrice)
                .ticketCount(1)
                .build();

        BookingGroupLineSession session = BookingGroupLineSession.builder()
                .id(1L)
                .line(line)
                .ordinal(1)
                .sessionReference(SESSION_UUID.toString())
                .locationReference(10L)
                .scheduledStart(futureSessionStart)
                .holdStatus(LineHoldStatus.RELEASED)
                .build();
        line.setSessions(new ArrayList<>(List.of(session)));

        TicketBooking ticket = TicketBooking.builder()
                .id(1L)
                .ticketCode(GROUP_CODE + "-T1")
                .group(group)
                .lineIndex(0)
                .participantOrdinal(1)
                .packageId(PACKAGE_ID)
                .packageNameSnapshot("Ceramic Class")
                .ticketStatus(BookingStatus.EXPIRED)
                .financialStatus(PaymentStatus.UNPAID)
                .build();

        TicketPriceSnapshot snapshot = TicketPriceSnapshot.builder()
                .id(1L)
                .ticket(ticket)
                .packagePriceVnd(packagePrice)
                .voucherDiscountVnd(discountVnd)
                .pointsUsed(pointsUsed)
                .pointDiscountVnd(pointDiscountVnd)
                .finalPriceVnd(collected)
                .collectedAtBookingVnd(collected)
                .collectionKind(TicketCollectionKind.FULL)
                .remainingVnd(0)
                .nonRefundableDisclosed(true)
                .build();

        when(groups.findByGroupCodeForUpdate(GROUP_CODE)).thenReturn(Optional.of(group));
        when(reviews.findByVerifiedEvidenceReference(MERCHANT_REF)).thenReturn(Optional.empty());
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(List.of(line));
        when(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(1L)).thenReturn(List.of(ticket));
        when(snapshots.findByTicketId(1L)).thenReturn(Optional.of(snapshot));

        // Default Operations mock: capacity available, session eligible
        when(operations.getSaleEligibility(SESSION_UUID)).thenReturn(new SaleEligibilitySnapshot(
                SESSION_UUID, true, "AVAILABLE", 10L, futureSessionStart, futureSessionStart.plus(Duration.ofHours(2)),
                10, LocalDate.now().plusDays(1)
        ));
        when(operations.getPublishedPackageSelection(Long.toString(PACKAGE_ID))).thenReturn(new PublishedPackageSelectionSnapshot(
                Long.toString(PACKAGE_ID), packagePrice, 1, 10, 1, true, 0, "Ceramic Class"
        ));

        // Capacity reserve succeeds for reheld
        when(operations.reserveGroupCapacity(eq(GROUP_CODE), anyList(), anyString(), anyString())).thenReturn(
                GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), now.plus(Duration.ofMinutes(12)))
        );

        if (benefitSelectionJson != null || discountVnd > 0) {
            WorkshopCartQuote quote = WorkshopCartQuote.builder()
                    .id(1L)
                    .quoteRef("Q-REF-01")
                    .acceptedGroupCode(GROUP_CODE)
                    .benefitSelectionJson(benefitSelectionJson)
                    .voucherDiscountVnd(discountVnd)
                    .pointsUsed(pointsUsed)
                    .pointDiscountVnd(pointDiscountVnd)
                    .build();
            when(quotes.findByAcceptedGroupCode(GROUP_CODE)).thenReturn(Optional.of(quote));
        } else {
            when(quotes.findByAcceptedGroupCode(GROUP_CODE)).thenReturn(Optional.empty());
        }

        return new TestSetup(group, line, ticket, snapshot);
    }

    @Test
    @DisplayName("Condition 3 PASSES: automatic workshop discount recomputes and equals snapshot discount -> AUTO_RECONFIRMED")
    void condition3_passes_when_autoDiscount_recalculated_matches_snapshot() throws Exception {
        String autoSelectionJson = mapper.writeValueAsString(new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        TestSetup setup = createFixture(200_000, 30_000, 0, 0, autoSelectionJson, null); // Guest booking

        // Recalculated auto discount returns exactly 30,000 VND
        when(benefits.preview(eq(null), any(), any())).thenReturn(
                new WorkshopCartBenefitService.CartBenefits(30_000, 0, 0, 0, List.of(0), "digest", 1L, List.of(), 0, 0)
        );

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 170_000, "corr-01");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.AUTO_RECONFIRMED);
        assertThat(review.getFailedConditions()).isNull();
        verify(confirmation).confirmReheldLateGroup(eq(GROUP_CODE), eq(170_000L), eq(MERCHANT_REF),
                eq("LATE_PAYMENT_RECONFIRMED"), anyString(), eq("corr-01"));
    }

    @Test
    @DisplayName("Condition 3 FAILS: automatic discount recalculated differs from snapshot discount -> AMOUNT in failedConditions")
    void condition3_fails_when_autoDiscount_recalculated_differs_from_snapshot() throws Exception {
        String autoSelectionJson = mapper.writeValueAsString(new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        TestSetup setup = createFixture(200_000, 30_000, 0, 0, autoSelectionJson, null);

        // Recalculated auto discount has ended / changed to 0 VND
        when(benefits.preview(eq(null), any(), any())).thenReturn(
                new WorkshopCartBenefitService.CartBenefits(0, 0, 0, 0, List.of(), "digest", 1L, List.of(), 0, 0)
        );

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 170_000, "corr-02");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains(LatePaymentReviewService.AMOUNT);
        verify(confirmation, never()).confirmReheldLateGroup(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Condition 3 FAILS: booking used a voucher -> AMOUNT in failedConditions (vouchers still fail in V1)")
    void condition3_fails_when_voucher_was_used() throws Exception {
        String voucherSelectionJson = mapper.writeValueAsString(new BenefitSelection(SelectionChoice.VOUCHER, "VOUCH50", BigInteger.ZERO));
        TestSetup setup = createFixture(200_000, 50_000, 0, 0, voucherSelectionJson, 99L);

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 150_000, "corr-03");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains(LatePaymentReviewService.AMOUNT);
    }

    @Test
    @DisplayName("Condition 3 FAILS: booking used points -> AMOUNT in failedConditions (points still fail in V1)")
    void condition3_fails_when_points_were_used() throws Exception {
        String autoWithPointsSelectionJson = mapper.writeValueAsString(new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.valueOf(100)));
        TestSetup setup = createFixture(200_000, 0, 100, 10_000, autoWithPointsSelectionJson, 99L);

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 190_000, "corr-04");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains(LatePaymentReviewService.AMOUNT);
    }

    @Test
    @DisplayName("Condition 3 FAILS: package public price in Operations has changed -> AMOUNT in failedConditions")
    void condition3_fails_when_package_price_changed() throws Exception {
        String autoSelectionJson = mapper.writeValueAsString(new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        TestSetup setup = createFixture(200_000, 30_000, 0, 0, autoSelectionJson, null);

        // Package price increased to 250,000 VND
        when(operations.getPublishedPackageSelection(Long.toString(PACKAGE_ID))).thenReturn(new PublishedPackageSelectionSnapshot(
                Long.toString(PACKAGE_ID), 250_000, 1, 10, 1, true, 0, "Ceramic Class"
        ));

        when(benefits.preview(eq(null), any(), any())).thenReturn(
                new WorkshopCartBenefitService.CartBenefits(30_000, 0, 0, 0, List.of(0), "digest", 1L, List.of(), 0, 0)
        );

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 170_000, "corr-05");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains(LatePaymentReviewService.AMOUNT);
    }

    @Test
    @DisplayName("Condition 3 FAILS: paid amount does not match group collection amount -> AMOUNT in failedConditions")
    void condition3_fails_when_paid_amount_does_not_match_group_collection() throws Exception {
        TestSetup setup = createFixture(200_000, 0, 0, 0, null, null);

        // Paid amount is 150_000 instead of 200_000
        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 150_000, "corr-06");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains(LatePaymentReviewService.AMOUNT);
    }

    @Test
    @DisplayName("Condition 3 PASSES: plain booking with no discount and no points, no auto-discount applies -> AUTO_RECONFIRMED")
    void condition3_passes_when_no_discount_and_no_auto_discount_applies() throws Exception {
        TestSetup setup = createFixture(200_000, 0, 0, 0, null, null);

        when(benefits.preview(eq(null), any(), any())).thenReturn(
                new WorkshopCartBenefitService.CartBenefits(0, 0, 0, 0, List.of(), "digest", 1L, List.of(), 0, 0)
        );

        LatePaymentReview review = service.receive(GROUP_CODE, MERCHANT_REF, 200_000, "corr-07");

        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.AUTO_RECONFIRMED);
        assertThat(review.getFailedConditions()).isNull();
    }
}

