package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T024: verifies Booking's persistence boundary around the authoritative Operations decision.
 * Run with the isolated test profile; Operations owns selection rules and capacity concurrency.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingCreationIT {
    @Autowired private BookingCreationService bookingCreationService;
    @Autowired private WorkshopBookingRepository bookingRepository;
    @Autowired private BookingSessionCommitmentRepository commitmentRepository;
    @Autowired private BookingCapacityHoldRepository holdRepository;
    @Autowired private BookingInvoiceRepository invoiceRepository;

    @MockBean private WorkshopOperationsFacade operationsFacade;
    @MockBean private AccountFacade accountFacade;
    @MockBean private BookingAuditEvidenceRepository auditRepository;

    @BeforeEach
    void resetBoundaries() {
        reset(operationsFacade, auditRepository);
    }

    @Test
    void operationRefusalsCreateNoBookingInvoiceCommitmentOrLocalHold() {
        assertRefusal(RejectionReason.PACKAGE_NOT_PUBLISHED, "PACKAGE_NOT_PUBLISHED");
        assertRefusal(RejectionReason.PACKAGE_SELECTION_INVALID, "PACKAGE_SELECTION_INVALID");
        assertRefusal(RejectionReason.INSUFFICIENT_CAPACITY, "INSUFFICIENT_CAPACITY");
        // Operations represents SALES_PAUSED through its non-disclosing SALE_NOT_ELIGIBLE result.
        assertRefusal(RejectionReason.SALE_NOT_ELIGIBLE, "PACKAGE_SELECTION_INVALID");
    }

    @Test
    void localFailureAfterBookingWritesRollsBackTheWholeBookingAggregate() {
        long bookingsBefore = bookingRepository.count();
        long commitmentsBefore = commitmentRepository.count();
        long holdsBefore = holdRepository.count();
        long invoicesBefore = invoiceRepository.count();
        UUID session = UUID.randomUUID();
        configureSelection(session);
        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.accepted(List.of(UUID.randomUUID()), Instant.now().plusSeconds(900)));
        when(auditRepository.save(any())).thenThrow(new IllegalStateException("simulated local audit persistence failure"));

        assertThatThrownBy(() -> bookingCreationService.createGuest(request(session), "corr-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated local audit persistence failure");

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
    }

    @Test
    void concurrentLastSeatDecisionPersistsOnlyTheSingleAcceptedBooking() throws Exception {
        long bookingsBefore = bookingRepository.count();
        long commitmentsBefore = commitmentRepository.count();
        long holdsBefore = holdRepository.count();
        long invoicesBefore = invoiceRepository.count();
        UUID session = UUID.randomUUID();
        configureSelection(session);
        AtomicInteger remainingSeats = new AtomicInteger(1);
        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class))).thenAnswer(invocation -> {
            if (remainingSeats.compareAndSet(1, 0)) {
                return CapacityHoldOutcome.accepted(List.of(UUID.randomUUID()), Instant.now().plusSeconds(900));
            }
            return CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> submitAfter(start, session, "corr-last-seat-1"));
            Future<Boolean> second = executor.submit(() -> submitAfter(start, session, "corr-last-seat-2"));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore + 1);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsBefore + 1);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore + 1);
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore + 1);
    }

    private void assertRefusal(RejectionReason reason, String expectedCode) {
        long bookingsBefore = bookingRepository.count();
        long commitmentsBefore = commitmentRepository.count();
        long holdsBefore = holdRepository.count();
        long invoicesBefore = invoiceRepository.count();
        UUID session = UUID.randomUUID();
        configureSelection(session);
        when(operationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(CapacityHoldOutcome.rejected(reason));

        assertThatThrownBy(() -> bookingCreationService.createGuest(request(session), "corr-" + UUID.randomUUID()))
                .isInstanceOf(BookingCreationRejectedException.class)
                .extracting("code").isEqualTo(expectedCode);

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
        verify(operationsFacade).reserveBookingCapacity(any(ReserveBookingCapacityCommand.class));
        verifyNoInteractions(auditRepository);
        reset(operationsFacade);
    }

    private void configureSelection(UUID session) {
        when(operationsFacade.getPublishedPackageSelection(any(PublishedPackageSelectionQuery.class)))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 50_000, 1, 8, 1, true, 0, "Workshop"));
        when(operationsFacade.getSaleEligibility(any(SaleEligibilityQuery.class)))
                .thenReturn(new SaleEligibilitySnapshot(session, true, "PUBLISHED", 12L,
                        Instant.parse("2026-11-01T09:00:00Z"), Instant.parse("2026-11-01T10:00:00Z"),
                        10, java.time.LocalDate.parse("2026-11-01")));
    }

    private CreateBookingRequest request(UUID session) {
        return CreateBookingRequest.builder().packageId(7L).sessionReferences(List.of(session)).participantCount(1)
                .contactEmail("buyer@example.com").contactPhone("0901234567").build();
    }

    private boolean submitAfter(CountDownLatch start, UUID session, String correlation) throws Exception {
        start.await();
        try {
            bookingCreationService.createGuest(request(session), correlation);
            return true;
        } catch (BookingCreationRejectedException rejected) {
            assertThat(rejected.getCode()).isEqualTo("INSUFFICIENT_CAPACITY");
            return false;
        }
    }
}
