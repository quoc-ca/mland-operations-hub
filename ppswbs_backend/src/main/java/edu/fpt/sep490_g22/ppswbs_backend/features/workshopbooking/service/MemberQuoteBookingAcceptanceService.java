package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookedSessionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Accepts a Member-owned quote while atomically reserving Operations capacity and Loyalty benefits. */
@Service
@RequiredArgsConstructor
public class MemberQuoteBookingAcceptanceService {
    private static final TypeReference<List<WorkshopSessionFact>> SESSION_FACTS = new TypeReference<>() {};

    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopOperationsAdapter operations;
    private final LoyaltyFacade loyalty;
    private final MemberWorkshopBookingQuoteRepository quotes;
    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final BookingCapacityHoldRepository holds;
    private final BookingInvoiceRepository invoices;
    private final BookingAuditEvidenceRepository audits;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    /** Authenticate before the controller returns a cached idempotent response. */
    public Long requireMemberAccountId(String authorizationHeader, String quoteReference) {
        Long memberAccountId = authorization.requireMemberOwnership(authorizationHeader, null).accountId();
        quotes.findByQuoteRefAndMemberAccountId(quoteReference, memberAccountId)
                .orElseThrow(() -> new BookingNotFoundException("Booking quote was not found"));
        return memberAccountId;
    }

    @Transactional
    public BookingSummaryResponse accept(CreateBookingRequest request, Long authenticatedMemberId,
                                         String correlationReference, BookingIdempotencyClaim claim) {
        if (request == null || request.getQuoteRef() == null || authenticatedMemberId == null) {
            throw new BookingValidationException("A Member quote and authenticated Member are required");
        }

        MemberWorkshopBookingQuote quote = quotes.findOwnedForUpdate(request.getQuoteRef(), authenticatedMemberId)
                .orElseThrow(() -> new BookingNotFoundException("Booking quote was not found"));
        Instant acceptedAt = Instant.now();
        if (!acceptedAt.isBefore(quote.getExpiresAt())) {
            throw new BookingCreationRejectedException("QUOTE_EXPIRED", "The booking quote has expired");
        }
        if (!"OPEN".equals(quote.getQuoteState())) {
            throw new BookingCreationRejectedException("QUOTE_ALREADY_ACCEPTED", "The booking quote is no longer available");
        }

        List<WorkshopSessionFact> quotedSessions = readSessionFacts(quote.getSessionFactsJson());
        List<UUID> sessionReferences = quotedSessions.stream().map(fact -> UUID.fromString(fact.sessionRef())).toList();
        if (sessionReferences.isEmpty() || new HashSet<>(sessionReferences).size() != sessionReferences.size()) {
            throw new BookingConflictException("Stored quote has an invalid session selection");
        }

        PublishedPackageSelectionSnapshot currentPackage = operations.getPublishedPackageSelection(
                Long.toString(quote.getPackageId()));
        if (currentPackage == null || !Long.toString(quote.getPackageId()).equals(currentPackage.packageReference())
                || currentPackage.packageName() == null || currentPackage.packageName().isBlank()) {
            throw new BookingCreationRejectedException("QUOTE_CHANGED", "The selected workshop package is no longer available");
        }
        if (quote.getParticipantCount() < currentPackage.minParticipants()
                || quote.getParticipantCount() > currentPackage.maxParticipants()
                || quotedSessions.size() != currentPackage.requiredSessionCount()) {
            throw new BookingCreationRejectedException("QUOTE_CHANGED", "The workshop package requirements have changed");
        }

        List<WorkshopSessionFact> currentSessionFacts = new ArrayList<>();
        Map<UUID, SaleEligibilitySnapshot> sessionSnapshots = new HashMap<>();
        Long auditLocationId = null;
        for (UUID reference : sessionReferences) {
            SaleEligibilitySnapshot snapshot = operations.getSaleEligibility(reference);
            if (snapshot == null || !snapshot.customerSelectable() || snapshot.locationReference() == null
                    || snapshot.workshopDate() == null || snapshot.remainingCapacity() < quote.getParticipantCount()) {
                throw new BookingCreationRejectedException("QUOTE_CHANGED", "A selected workshop session is no longer available");
            }
            if (snapshot.sessionReference() != null && !reference.equals(snapshot.sessionReference())) {
                throw new BookingConflictException("Workshop Operations returned a mismatched session reference");
            }
            currentSessionFacts.add(WorkshopSessionFact.of(reference.toString(), snapshot.locationReference(), snapshot.workshopDate()));
            sessionSnapshots.put(reference, snapshot);
            if (auditLocationId == null) auditLocationId = snapshot.locationReference();
        }

        long currentGross;
        try {
            currentGross = Math.multiplyExact(currentPackage.publicPerParticipantPrice(), (long) quote.getParticipantCount());
        } catch (ArithmeticException e) {
            throw new BookingValidationException("Package total exceeds the supported VND range");
        }
        if (currentPackage.publicPerParticipantPrice() != quote.getPricePerParticipant()
                || currentGross != quote.getGrossAmount()) {
            throw new BookingCreationRejectedException("QUOTE_CHANGED", "The workshop package price has changed");
        }

        BenefitSelection selection = readBenefitSelection(quote.getBenefitSelectionJson());
        WorkshopFact workshopFact = new WorkshopFact(quote.getPackageId(), quote.getParticipantCount(),
                BigInteger.valueOf(currentGross), currentSessionFacts);
        TrustedSourceContext trusted = new TrustedSourceContext(SourceKind.WORKSHOP, authenticatedMemberId,
                acceptedAt, List.of(), workshopFact, BigInteger.ZERO);

        BenefitPreview currentPreview = loyalty.previewBenefits(trusted, selection);
        if (currentPreview == null || !currentPreview.reasons().isEmpty()) {
            throw new BookingCreationRejectedException("PROMOTION_INELIGIBLE",
                    "The quoted promotion or points selection is no longer eligible");
        }
        if (!Objects.equals(quote.getTermsDigest(), currentPreview.termsDigest())
                || !BigInteger.valueOf(quote.getPostPromotionAmount()).equals(currentPreview.postPromotionAmount())
                || !BigInteger.valueOf(quote.getProgramDiscount()).equals(currentPreview.programDiscount())
                || !BigInteger.valueOf(quote.getPointsUsed()).equals(currentPreview.pointsUsed())
                || !BigInteger.valueOf(quote.getPointDiscount()).equals(currentPreview.pointDiscount())) {
            throw new BookingCreationRejectedException("QUOTE_CHANGED",
                    "The workshop price or benefit terms have changed; preview the booking again");
        }

        String bookingCode = "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        CapacityHoldOutcome holdOutcome = operations.reserveBookingCapacity(bookingCode,
                Long.toString(quote.getPackageId()), sessionReferences, quote.getParticipantCount(),
                "CREATE-" + bookingCode, correlationReference);
        if (holdOutcome == null || holdOutcome.status() != OutcomeStatus.ACCEPTED
                || holdOutcome.holdReferences() == null || holdOutcome.holdReferences().size() != sessionReferences.size()
                || holdOutcome.expiresAt() == null) {
            RejectionReason reason = holdOutcome == null ? null : holdOutcome.reason();
            String code = reason == RejectionReason.INSUFFICIENT_CAPACITY ? "INSUFFICIENT_CAPACITY" : "PACKAGE_SELECTION_INVALID";
            throw new BookingCreationRejectedException(code, "Workshop Operations rejected the quoted session selection");
        }
        if (!holdOutcome.expiresAt().isAfter(acceptedAt)) {
            throw new BookingConflictException("Workshop Operations returned an expired capacity hold");
        }

        AcceptedSourceContext accepted = new AcceptedSourceContext(trusted, bookingCode, 1L, true,
                acceptedAt, holdOutcome.expiresAt(), quote.getQuoteRef(), quote.getTermsDigest());
        AcceptedBenefitRef acceptedBenefits = loyalty.reserveAcceptedTransaction(accepted, selection, "create-" + bookingCode);
        long postPromotionAmount = exactVnd(acceptedBenefits.postPromotionAmount());
        long depositAmount = Math.floorDiv(postPromotionAmount, 2) + (postPromotionAmount % 2);
        long remainingAmount = postPromotionAmount - depositAmount;

        WorkshopBooking booking = bookings.save(WorkshopBooking.builder()
                .bookingCode(bookingCode).packageId(quote.getPackageId()).packageNameSnapshot(quote.getPackageNameSnapshot())
                .contactEmail(request.getContactEmail().trim()).contactPhone(request.getContactPhone().trim())
                .memberAccountId(authenticatedMemberId).participantCount(quote.getParticipantCount())
                .bookingStatus(BookingStatus.PENDING_PAYMENT).paymentStatus(PaymentStatus.UNPAID)
                .holdExpiresAt(holdOutcome.expiresAt()).build());

        Map<UUID, UUID> holdBySession = new HashMap<>();
        for (int i = 0; i < sessionReferences.size(); i++) {
            holdBySession.put(sessionReferences.get(i), holdOutcome.holdReferences().get(i));
        }
        List<UUID> chronological = sessionReferences.stream().sorted(Comparator.comparing(reference ->
                Optional.ofNullable(sessionSnapshots.get(reference).scheduledStart()).orElse(Instant.MAX))).toList();
        List<BookedSessionDto> responseSessions = new ArrayList<>();
        int ordinal = 1;
        for (UUID reference : chronological) {
            SaleEligibilitySnapshot snapshot = sessionSnapshots.get(reference);
            BookingSessionCommitment commitment = commitments.save(BookingSessionCommitment.builder()
                    .booking(booking).sessionReference(reference.toString()).locationId(snapshot.locationReference())
                    .ordinal(ordinal).seatCount(quote.getParticipantCount()).build());
            holds.save(BookingCapacityHold.builder().booking(booking).sessionCommitment(commitment)
                    .holdReference(holdBySession.get(reference).toString()).holdStatus("RESERVED").build());
            responseSessions.add(new BookedSessionDto(reference, snapshot.locationReference(), ordinal,
                    snapshot.scheduledStart(), snapshot.scheduledEnd()));
            ordinal++;
        }
        invoices.save(BookingInvoice.builder().booking(booking).invoiceNumber("INV-" + bookingCode)
                .pricePerParticipant(quote.getPricePerParticipant()).packageTotal(quote.getGrossAmount())
                .postPromotionAmount(postPromotionAmount).depositAmount(depositAmount)
                .remainingAmount(remainingAmount).currency("VND").build());
        audits.save(BookingAuditEvidence.builder().bookingCode(bookingCode).locationId(auditLocationId)
                .actorType("MEMBER").actorId(authenticatedMemberId).action("ACCEPT_MEMBER_QUOTE")
                .outcome("ACCEPTED").correlationReference(correlationReference).occurredAt(Instant.now()).build());

        quote.setQuoteState("ACCEPTED");
        quote.setAcceptedBookingCode(bookingCode);
        quotes.save(quote);

        BookingSummaryResponse response = BookingSummaryResponse.builder().bookingCode(bookingCode)
                .bookingStatus(booking.getBookingStatus().name()).paymentStatus(booking.getPaymentStatus().name())
                .packageId(booking.getPackageId()).packageName(booking.getPackageNameSnapshot())
                .participantCount(booking.getParticipantCount()).holdExpiresAt(booking.getHoldExpiresAt())
                .sessions(responseSessions).tickets(List.of()).build();
        completeClaim(claim, response);
        return response;
    }

    private List<WorkshopSessionFact> readSessionFacts(String json) {
        try {
            return mapper.readValue(normalizeJsonColumn(json), SESSION_FACTS);
        } catch (JsonProcessingException e) {
            throw new BookingConflictException("Stored quote session facts cannot be read");
        }
    }

    private BenefitSelection readBenefitSelection(String json) {
        try {
            return mapper.readValue(normalizeJsonColumn(json), BenefitSelection.class);
        } catch (JsonProcessingException e) {
            throw new BookingConflictException("Stored quote benefit selection cannot be read");
        }
    }

    /** Some JDBC JSON column implementations return the serialized JSON document as a JSON string scalar. */
    private String normalizeJsonColumn(String json) throws JsonProcessingException {
        JsonNode storedValue = mapper.readTree(json);
        return storedValue != null && storedValue.isTextual() ? storedValue.textValue() : json;
    }

    private long exactVnd(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BookingCreationRejectedException("WORKSHOP_NET_AMOUNT_INVALID", "The accepted workshop amount must be positive");
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new BookingValidationException("Accepted amount exceeds the supported VND range");
        }
    }

    private void completeClaim(BookingIdempotencyClaim claim, BookingSummaryResponse response) {
        if (claim == null) return;
        try {
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 201,
                    mapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Booking response could not be stored", e);
        }
    }
}
