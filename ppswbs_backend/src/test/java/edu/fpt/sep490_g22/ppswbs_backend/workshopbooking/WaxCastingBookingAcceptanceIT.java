package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MemberQuoteBookingAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.1 T053 — Integration and acceptance contract tests for Workshop Booking consuming 003.2's Wax Casting policy:
 * - Forwards intact pair of opaque UUID session references to Operations facade without mutating or re-deriving them.
 * - Booking owns zero calendar/weekday logic; delegates authoritatively to Operations facade.
 * - Valid schedules (Monday -> Thursday, Friday -> Wednesday next week) are accepted when Operations approves.
 * - Invalid schedules (Monday -> Wednesday, Friday -> Tuesday next week) are atomically rejected with PACKAGE_SELECTION_INVALID.
 * - Operations' refusal produces zero booking, zero hold, zero ticket, zero payment attempt, and zero Loyalty reservation.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WaxCastingBookingAcceptanceIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String WAX_PKG_REF = "88";
    private static final Long WAX_PKG_ID = 88L;
    private static final long MEMBER_ID = 5001L;

    @Autowired private BookingCreationService bookingCreationService;
    @Autowired private MemberQuoteBookingAcceptanceService memberQuoteAcceptanceService;
    @Autowired private WorkshopBookingRepository bookingRepository;
    @Autowired private BookingSessionCommitmentRepository commitmentRepository;
    @Autowired private BookingCapacityHoldRepository holdRepository;
    @Autowired private BookingInvoiceRepository invoiceRepository;
    @Autowired private BookingTicketRepository ticketRepository;
    @Autowired private BookingPaymentAttemptRepository paymentAttemptRepository;
    @Autowired private MemberWorkshopBookingQuoteRepository quoteRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private WorkshopOperationsFacade operationsFacade;
    @MockBean private AccountFacade accountFacade;
    @MockBean private LoyaltyFacade loyaltyFacade;
    @MockBean private BookingAuditEvidenceRepository auditRepository;

    private UUID sessionMon;
    private UUID sessionWed;
    private UUID sessionThu;
    private UUID sessionFri;
    private UUID sessionNextTue;
    private UUID sessionNextWed;

    private LocalDate baseMonday;
    private LocalDate wednesday;
    private LocalDate thursday;
    private LocalDate friday;
    private LocalDate nextTuesday;
    private LocalDate nextWednesday;

    @BeforeEach
    void setUp() {
        reset(operationsFacade, loyaltyFacade, auditRepository);

        baseMonday = LocalDate.now(ZONE).plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        wednesday = baseMonday.plusDays(2);
        thursday = baseMonday.plusDays(3);
        friday = baseMonday.plusDays(4);
        nextTuesday = baseMonday.plusDays(8);
        nextWednesday = baseMonday.plusDays(9);

        sessionMon = UUID.randomUUID();
        sessionWed = UUID.randomUUID();
        sessionThu = UUID.randomUUID();
        sessionFri = UUID.randomUUID();
        sessionNextTue = UUID.randomUUID();
        sessionNextWed = UUID.randomUUID();

        // Published package snapshot returns 2 required foundry working days between sessions
        when(operationsFacade.getPublishedPackageSelection(new PublishedPackageSelectionQuery(WAX_PKG_REF)))
                .thenReturn(new PublishedPackageSelectionSnapshot(
                        WAX_PKG_REF, 1_200_000L, 1, 6, 2, true, 0, 2, "Wax Casting Workshop"));

        // Configure session sale eligibility snapshots
        configureSessionEligibility(sessionMon, baseMonday, LocalTime.of(16, 0), 101L);
        configureSessionEligibility(sessionWed, wednesday, LocalTime.of(9, 30), 101L);
        configureSessionEligibility(sessionThu, thursday, LocalTime.of(9, 30), 101L);
        configureSessionEligibility(sessionFri, friday, LocalTime.of(16, 0), 101L);
        configureSessionEligibility(sessionNextTue, nextTuesday, LocalTime.of(9, 30), 101L);
        configureSessionEligibility(sessionNextWed, nextWednesday, LocalTime.of(9, 30), 101L);
    }

    @Test
    @DisplayName("Monday to Thursday Wax Casting: Booking forwards opaque UUIDs intact and creates booking on accepted hold")
    void mondayToThursdayValidAccepted() {
        long bookingsBefore = bookingRepository.count();
        long holdsBefore = holdRepository.count();

        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.accepted(List.of(UUID.randomUUID(), UUID.randomUUID()), Instant.now().plusSeconds(900)));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .packageId(WAX_PKG_ID)
                .sessionReferences(List.of(sessionMon, sessionThu))
                .participantCount(2)
                .contactEmail("guest@example.com")
                .contactPhone("0901234567")
                .build();

        BookingSummaryResponse response = bookingCreationService.createGuest(request, "corr-wax-mon-thu");

        assertThat(response).isNotNull();
        assertThat(response.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getSessions()).extracting(s -> s.getSessionReference())
                .containsExactlyInAnyOrder(sessionMon, sessionThu);

        // Verify opaque references were forwarded unchanged to Operations facade without calendar mutation
        ArgumentCaptor<ReserveBookingCapacityCommand> commandCaptor = ArgumentCaptor.forClass(ReserveBookingCapacityCommand.class);
        verify(operationsFacade).reserveBookingCapacity(commandCaptor.capture());
        ReserveBookingCapacityCommand captured = commandCaptor.getValue();
        assertThat(captured.packageReference()).isEqualTo(WAX_PKG_REF);
        assertThat(captured.orderedRequiredSessionReferences()).containsExactly(sessionMon, sessionThu);
        assertThat(captured.participantCount()).isEqualTo(2);

        // Verify local booking entities persisted
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore + 1);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore + 2);
    }

    @Test
    @DisplayName("Friday to Wednesday next week Wax Casting: Booking forwards opaque UUIDs intact and creates booking")
    void fridayToWednesdayNextWeekValidAccepted() {
        long bookingsBefore = bookingRepository.count();
        long holdsBefore = holdRepository.count();

        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.accepted(List.of(UUID.randomUUID(), UUID.randomUUID()), Instant.now().plusSeconds(900)));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .packageId(WAX_PKG_ID)
                .sessionReferences(List.of(sessionFri, sessionNextWed))
                .participantCount(2)
                .contactEmail("guest@example.com")
                .contactPhone("0901234567")
                .build();

        BookingSummaryResponse response = bookingCreationService.createGuest(request, "corr-wax-fri-wed");

        assertThat(response).isNotNull();
        assertThat(response.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore + 1);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore + 2);

        ArgumentCaptor<ReserveBookingCapacityCommand> commandCaptor = ArgumentCaptor.forClass(ReserveBookingCapacityCommand.class);
        verify(operationsFacade).reserveBookingCapacity(commandCaptor.capture());
        assertThat(commandCaptor.getValue().orderedRequiredSessionReferences()).containsExactly(sessionFri, sessionNextWed);
    }

    @Test
    @DisplayName("Monday to Wednesday Wax Casting: Operations rejection produces PACKAGE_SELECTION_INVALID and zero mutations")
    void mondayToWednesdayInvalidRejectedWithoutMutations() {
        long bookingsBefore = bookingRepository.count();
        long commitmentsBefore = commitmentRepository.count();
        long holdsBefore = holdRepository.count();
        long invoicesBefore = invoiceRepository.count();
        long ticketsBefore = ticketRepository.count();
        long attemptsBefore = paymentAttemptRepository.count();

        // Operations rejects because only 1 foundry working day is strictly between Monday and Wednesday
        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.rejected(RejectionReason.PACKAGE_SELECTION_INVALID));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .packageId(WAX_PKG_ID)
                .sessionReferences(List.of(sessionMon, sessionWed))
                .participantCount(2)
                .contactEmail("guest@example.com")
                .contactPhone("0901234567")
                .build();

        assertThatThrownBy(() -> bookingCreationService.createGuest(request, "corr-wax-mon-wed-fail"))
                .isInstanceOf(BookingCreationRejectedException.class)
                .extracting("code").isEqualTo("PACKAGE_SELECTION_INVALID");

        // Verify ZERO persistence mutations occurred across all booking aggregate tables
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
        assertThat(ticketRepository.count()).isEqualTo(ticketsBefore);
        assertThat(paymentAttemptRepository.count()).isEqualTo(attemptsBefore);
    }

    @Test
    @DisplayName("Friday to Tuesday next week Wax Casting: Operations rejection produces PACKAGE_SELECTION_INVALID and zero mutations")
    void fridayToTuesdayNextWeekInvalidRejectedWithoutMutations() {
        long bookingsBefore = bookingRepository.count();
        long commitmentsBefore = commitmentRepository.count();
        long holdsBefore = holdRepository.count();
        long invoicesBefore = invoiceRepository.count();
        long ticketsBefore = ticketRepository.count();
        long attemptsBefore = paymentAttemptRepository.count();

        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.rejected(RejectionReason.PACKAGE_SELECTION_INVALID));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .packageId(WAX_PKG_ID)
                .sessionReferences(List.of(sessionFri, sessionNextTue))
                .participantCount(2)
                .contactEmail("guest@example.com")
                .contactPhone("0901234567")
                .build();

        assertThatThrownBy(() -> bookingCreationService.createGuest(request, "corr-wax-fri-tue-fail"))
                .isInstanceOf(BookingCreationRejectedException.class)
                .extracting("code").isEqualTo("PACKAGE_SELECTION_INVALID");

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
        assertThat(ticketRepository.count()).isEqualTo(ticketsBefore);
        assertThat(paymentAttemptRepository.count()).isEqualTo(attemptsBefore);
    }

    @Test
    @DisplayName("Member quote acceptance with invalid Wax Casting calendar produces zero booking, zero hold, and zero Loyalty reservation")
    void memberQuoteAcceptanceRejectionLeavesZeroLoyaltyReservationOrBooking() throws Exception {
        long bookingsBefore = bookingRepository.count();
        long holdsBefore = holdRepository.count();

        String quoteRef = UUID.randomUUID().toString();
        String termsDigest = "digest-" + UUID.randomUUID();
        quoteRepository.save(MemberWorkshopBookingQuote.builder()
                .quoteRef(quoteRef)
                .memberAccountId(MEMBER_ID)
                .packageId(WAX_PKG_ID)
                .packageNameSnapshot("Wax Casting Workshop")
                .pricePerParticipant(1_200_000L)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .participantCount(2)
                .sessionFactsJson(objectMapper.writeValueAsString(List.of(
                        edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact.of(
                                sessionMon.toString(), 101L, baseMonday),
                        edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact.of(
                                sessionWed.toString(), 101L, wednesday))))
                .benefitSelectionJson(objectMapper.writeValueAsString(
                        edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection.none()))
                .grossAmount(2_400_000L)
                .programDiscount(0L)
                .pointsUsed(0L)
                .pointDiscount(0L)
                .postPromotionAmount(2_400_000L)
                .earnBase(2_400_000L)
                .termsDigest(termsDigest)
                .quoteState("OPEN")
                .build());

        when(loyaltyFacade.previewBenefits(any(), any()))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview(
                        java.math.BigInteger.valueOf(2_400_000L),
                        java.math.BigInteger.valueOf(2_400_000L),
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.valueOf(2_400_000L),
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.valueOf(2_400_000L),
                        java.math.BigInteger.valueOf(2_400_000L),
                        null,
                        null,
                        null,
                        1L,
                        false,
                        List.of(),
                        termsDigest));

        // Operations rejects the 1-day gap
        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.rejected(RejectionReason.PACKAGE_SELECTION_INVALID));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .packageId(WAX_PKG_ID)
                .quoteRef(quoteRef)
                .sessionReferences(List.of(sessionMon, sessionWed))
                .participantCount(2)
                .contactEmail("member@example.com")
                .contactPhone("0901234567")
                .build();

        assertThatThrownBy(() -> memberQuoteAcceptanceService.accept(
                request, MEMBER_ID, "corr-member-wax-fail", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .extracting("code").isEqualTo("PACKAGE_SELECTION_INVALID");

        // Verify no booking created, no hold created, and no Loyalty reservation called
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        verify(loyaltyFacade, never()).reserveAcceptedTransaction(any(), any(), any());
    }

    private void configureSessionEligibility(UUID ref, LocalDate date, LocalTime startTime, Long locationId) {
        Instant start = date.atTime(startTime).atZone(ZONE).toInstant();
        Instant end = start.plusSeconds(3 * 3600);
        when(operationsFacade.getSaleEligibility(new SaleEligibilityQuery(ref)))
                .thenReturn(new SaleEligibilitySnapshot(
                        ref, true, "PUBLISHED", locationId, start, end, 10, date));
    }
}

