package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingCreationServiceTest {
    private WorkshopOperationsAdapter operations;
    private WorkshopBookingRepository bookings;
    private BookingSessionCommitmentRepository commitments;
    private BookingCapacityHoldRepository holds;
    private BookingInvoiceRepository invoices;
    private BookingAuditEvidenceRepository audits;
    private BookingCreationService service;

    @BeforeEach
    void setUp() {
        operations = mock(WorkshopOperationsAdapter.class);
        bookings = mock(WorkshopBookingRepository.class);
        commitments = mock(BookingSessionCommitmentRepository.class);
        holds = mock(BookingCapacityHoldRepository.class);
        invoices = mock(BookingInvoiceRepository.class);
        audits = mock(BookingAuditEvidenceRepository.class);
        BookingIdempotencyService idempotency = mock(BookingIdempotencyService.class);
        when(bookings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commitments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(holds.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(audits.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new BookingCreationService(operations, new BookingPricingService(), bookings,
                commitments, holds, invoices, audits, idempotency, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    @Test
    void sendsOpaquePackageSelectionAndPersistsOnlyAfterAtomicAcceptedHold() {
        UUID later = UUID.randomUUID();
        UUID earlier = UUID.randomUUID();
        when(operations.getPublishedPackageSelection("7"))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 100_001, 1, 6, 2, true, 1, "Clay Workshop"));
        when(operations.getSaleEligibility(later)).thenReturn(snapshot(later, "2026-11-01T14:00:00Z"));
        when(operations.getSaleEligibility(earlier)).thenReturn(snapshot(earlier, "2026-11-01T09:00:00Z"));
        when(operations.reserveBookingCapacity(anyString(), eq("7"), eq(List.of(later, earlier)), eq(2), anyString(), eq("corr-1")))
                .thenReturn(CapacityHoldOutcome.accepted(List.of(UUID.randomUUID(), UUID.randomUUID()), Instant.parse("2026-10-31T03:30:00Z")));
        CreateBookingRequest request = CreateBookingRequest.builder().packageId(7L)
                .sessionReferences(List.of(later, earlier)).participantCount(2)
                .contactEmail("buyer@example.com").contactPhone("0901234567").build();

        var response = service.createGuest(request, "corr-1");

        assertThat(response.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getSessions()).extracting(s -> s.getSessionReference()).containsExactly(earlier, later);
        ArgumentCaptor<BookingInvoice> invoice = ArgumentCaptor.forClass(BookingInvoice.class);
        verify(invoices).save(invoice.capture());
        assertThat(invoice.getValue().getPackageTotal()).isEqualTo(200_002L);
        assertThat(invoice.getValue().getDepositAmount()).isEqualTo(100_001L);
        assertThat(invoice.getValue().getRemainingAmount()).isEqualTo(100_001L);
        verify(operations).reserveBookingCapacity(anyString(), eq("7"), eq(List.of(later, earlier)), eq(2), anyString(), eq("corr-1"));
    }

    @Test
    void leavesNoLocalBookingWhenOperationsRejectsAtomicReserve() {
        UUID session = UUID.randomUUID();
        when(operations.getPublishedPackageSelection("7"))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 50_000, 1, 4, 1, true, 0, "Workshop"));
        when(operations.getSaleEligibility(session)).thenReturn(snapshot(session, "2026-11-01T09:00:00Z"));
        when(operations.reserveBookingCapacity(anyString(), eq("7"), eq(List.of(session)), eq(1), anyString(), anyString()))
                .thenReturn(CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY));
        CreateBookingRequest request = CreateBookingRequest.builder().packageId(7L)
                .sessionReferences(List.of(session)).participantCount(1)
                .contactEmail("buyer@example.com").contactPhone("0901234567").build();

        assertThatThrownBy(() -> service.createGuest(request, "corr-2"))
                .isInstanceOf(BookingCreationRejectedException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_CAPACITY");
        verifyNoInteractions(bookings, commitments, holds, invoices, audits);
    }

    @Test
    void rejectsNonPositiveParticipantCountAndDuplicateSessionReferencesBeforeCallingOperations() {
        UUID session = UUID.randomUUID();
        CreateBookingRequest invalidCount = CreateBookingRequest.builder().packageId(7L)
                .sessionReferences(List.of(session)).participantCount(0)
                .contactEmail("buyer@example.com").contactPhone("0901234567").build();
        assertThatThrownBy(() -> service.createGuest(invalidCount, "corr-3"))
                .isInstanceOf(BookingValidationException.class);

        when(operations.getPublishedPackageSelection("7"))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 50_000, 1, 4, 2, true, 0, "Workshop"));
        CreateBookingRequest duplicate = CreateBookingRequest.builder().packageId(7L)
                .sessionReferences(List.of(session, session)).participantCount(1)
                .contactEmail("buyer@example.com").contactPhone("0901234567").build();
        assertThatThrownBy(() -> service.createGuest(duplicate, "corr-4"))
                .isInstanceOf(BookingValidationException.class);
        verify(operations, never()).reserveBookingCapacity(anyString(), anyString(), anyList(), anyInt(), anyString(), anyString());
        verifyNoInteractions(bookings, commitments, holds, invoices, audits);
    }

    private SaleEligibilitySnapshot snapshot(UUID reference, String start) {
        Instant startAt = Instant.parse(start);
        return new SaleEligibilitySnapshot(reference, true, "PUBLISHED", 12L,
                startAt, startAt.plusSeconds(3600), 20, java.time.LocalDate.parse("2026-11-01"));
    }
}
