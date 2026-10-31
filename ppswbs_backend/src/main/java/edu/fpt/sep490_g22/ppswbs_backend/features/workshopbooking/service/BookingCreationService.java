package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingCreationService {
    private final WorkshopOperationsAdapter operations;
    private final BookingPricingService pricing;
    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final BookingCapacityHoldRepository holds;
    private final BookingInvoiceRepository invoices;
    private final BookingAuditEvidenceRepository audits;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Transactional
    public BookingSummaryResponse createGuest(CreateBookingRequest request, String correlationReference) {
        return createGuestInternal(request, correlationReference, null);
    }

    @Transactional
    public BookingSummaryResponse createGuest(CreateBookingRequest request, String correlationReference,
                                               BookingIdempotencyClaim idempotencyClaim) {
        return createGuestInternal(request, correlationReference, idempotencyClaim);
    }

    private BookingSummaryResponse createGuestInternal(CreateBookingRequest request, String correlationReference,
                                                        BookingIdempotencyClaim idempotencyClaim) {
        if (request == null || request.getPackageId() == null || request.getPackageId() <= 0
                || request.getParticipantCount() == null || request.getParticipantCount() < 1
                || request.getSessionReferences() == null || request.getSessionReferences().isEmpty()
                || request.getContactEmail() == null || request.getContactPhone() == null) {
            throw new BookingValidationException("A package, participants, sessions and contact details are required");
        }
        if (request.getSessionReferences().stream().anyMatch(Objects::isNull)) {
            throw new BookingValidationException("sessionReferences must contain distinct UUIDs");
        }
        List<UUID> selected = List.copyOf(request.getSessionReferences());
        if (new HashSet<>(selected).size() != selected.size()) {
            throw new BookingValidationException("sessionReferences must be distinct UUIDs");
        }

        String packageReference = request.getPackageId().toString();
        PublishedPackageSelectionSnapshot pkg = operations.getPublishedPackageSelection(packageReference);
        if (pkg == null || pkg.packageName() == null || pkg.packageName().isBlank()) {
            throw new BookingConflictException("The selected Package is not currently available");
        }

        // Read snapshots only to persist customer-safe canonical location and display order.
        // Operations performs authoritative count/location/gap/sale/capacity validation atomically below.
        Map<UUID, SaleEligibilitySnapshot> facts = new HashMap<>();
        for (UUID ref : selected) {
            SaleEligibilitySnapshot snapshot = operations.getSaleEligibility(ref);
            if (snapshot == null || snapshot.locationReference() == null
                    || (snapshot.sessionReference() != null && !ref.equals(snapshot.sessionReference()))) {
                throw new BookingConflictException("A selected session is not currently available");
            }
            facts.put(ref, snapshot);
        }
        List<UUID> chronological = selected.stream().sorted(Comparator.comparing(
                ref -> Optional.ofNullable(facts.get(ref).scheduledStart()).orElse(Instant.MAX))).toList();
        long packageTotal;
        try {
            packageTotal = Math.multiplyExact(pkg.publicPerParticipantPrice(), (long) request.getParticipantCount());
        } catch (ArithmeticException ex) {
            throw new BookingValidationException("Package total exceeds the supported VND range");
        }
        BookingPricingService.FrozenBookingAmounts amounts = pricing.freeze(
                pkg.publicPerParticipantPrice(), request.getParticipantCount(), packageTotal);

        String bookingCode = "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        CapacityHoldOutcome outcome = operations.reserveBookingCapacity(bookingCode, packageReference, selected,
                request.getParticipantCount(), "CREATE-" + bookingCode, correlationReference);
        if (outcome == null || outcome.status() != OutcomeStatus.ACCEPTED) {
            RejectionReason reason = outcome == null ? null : outcome.reason();
            String code = reason == RejectionReason.PACKAGE_NOT_PUBLISHED ? "PACKAGE_NOT_PUBLISHED"
                    : reason == RejectionReason.INSUFFICIENT_CAPACITY ? "INSUFFICIENT_CAPACITY"
                    : "PACKAGE_SELECTION_INVALID";
            throw new BookingCreationRejectedException(code);
        }
        if (outcome.holdReferences().size() != selected.size() || outcome.expiresAt() == null) {
            throw new BookingConflictException("Workshop Operations returned an incomplete capacity hold outcome");
        }

        WorkshopBooking booking = bookings.save(WorkshopBooking.builder()
                .bookingCode(bookingCode).packageId(request.getPackageId()).packageNameSnapshot(pkg.packageName())
                .contactEmail(request.getContactEmail().trim()).contactPhone(request.getContactPhone().trim())
                .participantCount(request.getParticipantCount()).bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID).holdExpiresAt(outcome.expiresAt()).build());

        Map<UUID, UUID> holdBySession = new HashMap<>();
        for (int i = 0; i < selected.size(); i++) holdBySession.put(selected.get(i), outcome.holdReferences().get(i));
        List<BookedSessionDto> sessionDtos = new ArrayList<>();
        Long auditLocationId = null;
        int ordinal = 1;
        for (UUID ref : chronological) {
            SaleEligibilitySnapshot snapshot = facts.get(ref);
            if (auditLocationId == null) auditLocationId = snapshot.locationReference();
            BookingSessionCommitment commitment = commitments.save(BookingSessionCommitment.builder()
                    .booking(booking).sessionReference(ref.toString()).locationId(snapshot.locationReference())
                    .ordinal(ordinal).seatCount(request.getParticipantCount()).build());
            holds.save(BookingCapacityHold.builder().booking(booking).sessionCommitment(commitment)
                    .holdReference(holdBySession.get(ref).toString()).holdStatus("RESERVED").build());
            sessionDtos.add(new BookedSessionDto(ref, snapshot.locationReference(), ordinal++,
                    snapshot.scheduledStart(), snapshot.scheduledEnd()));
        }
        invoices.save(BookingInvoice.builder().booking(booking).invoiceNumber("INV-" + bookingCode)
                .pricePerParticipant(amounts.pricePerParticipant()).packageTotal(amounts.packageTotal())
                .postPromotionAmount(amounts.postPromotionAmount()).depositAmount(amounts.depositAmount())
                .remainingAmount(amounts.remainingAmount()).currency("VND").build());
        audits.save(BookingAuditEvidence.builder().bookingCode(bookingCode).locationId(auditLocationId)
                .actorType("GUEST").action("RESERVE_CAPACITY").outcome("ACCEPTED")
                .correlationReference(correlationReference).occurredAt(Instant.now()).build());
        BookingSummaryResponse response = BookingSummaryResponse.builder().bookingCode(bookingCode).bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus().name()).packageId(booking.getPackageId())
                .packageName(booking.getPackageNameSnapshot()).participantCount(booking.getParticipantCount())
                .holdExpiresAt(booking.getHoldExpiresAt()).sessions(sessionDtos).tickets(List.of()).build();
        if (idempotencyClaim != null) {
            try {
                idempotency.completeClaimWithinTransaction(idempotencyClaim.recordId(), idempotencyClaim.claimVersion(),
                        201, mapper.writeValueAsString(response));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Booking response could not be stored", ex);
            }
        }
        return response;
    }
}
