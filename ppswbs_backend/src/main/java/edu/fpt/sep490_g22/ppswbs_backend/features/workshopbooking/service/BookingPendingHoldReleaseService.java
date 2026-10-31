package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.PendingHoldReleaseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/** Authorizes the customer proof before acquiring a release idempotency claim. */
@Service
@RequiredArgsConstructor
public class BookingPendingHoldReleaseService {

    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)\\d(?:[\\s.-]?\\d){8,10}(?!\\d)");
    private static final Pattern UUID_TOKEN = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}(?![0-9a-f])");

    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final WorkshopBookingFacade bookingFacade;
    private final WorkshopBookingAuthorizationService authorization;
    private final BookingIdempotencyService idempotency;
    private final BookingPendingHoldReleaseTransactionService transactionService;

    public BookingRecoveryCommandResult release(String authorizationHeader, String bookingCode,
                                                 String idempotencyKey, String correlationReference,
                                                 PendingHoldReleaseRequest request) {
        String normalizedCode = bookingCode == null ? "" : bookingCode.trim();
        String reason = normalizeReason(request == null ? null : request.getReason());
        boolean hasEmail = hasText(request == null ? null : request.getContactEmail());
        boolean hasPhone = hasText(request == null ? null : request.getContactPhone());
        boolean memberMode = hasText(authorizationHeader);

        if (memberMode && (hasEmail || hasPhone)) {
            throw new BookingValidationException("An authenticated Member must not submit contact proof");
        }
        if (!memberMode && (hasEmail == hasPhone)) {
            throw new BookingValidationException("A Guest must provide exactly one stored contact proof");
        }

        WorkshopBooking booking = bookings.findByBookingCode(normalizedCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        Long locationId = requireSingleBookingLocation(booking.getId());
        String actorType;
        Long actorId = null;
        if (memberMode) {
            AccountSummary member = authorization.requireMemberOwnership(authorizationHeader, null,
                    normalizedCode, locationId, correlationReference);
            actorType = "MEMBER";
            actorId = member.accountId();
            requireAccessDecision(booking.getBookingCode(), actorId, null, null);
        } else {
            actorType = "GUEST";
            requireAccessDecision(booking.getBookingCode(), null,
                    hasEmail ? request.getContactEmail().trim() : null,
                    hasPhone ? request.getContactPhone().trim() : null);
        }

        String semanticContent = normalizedCode + "|" + actorType + "|" + actorId + "|" + reason;
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey,
                "RELEASE_PENDING_HOLD", semanticContent, correlationReference);
        if (claim.cachedOutcome()) {
            // A 204 response has no representation; normalize legacy/cached JSON empty-string payloads.
            return new BookingRecoveryCommandResult(claim.statusCode(),
                    claim.statusCode() == 204 ? "" : claim.responsePayload());
        }
        return transactionService.release(authorizationHeader, normalizedCode, actorType, actorId,
                hasEmail ? request.getContactEmail().trim() : null,
                hasPhone ? request.getContactPhone().trim() : null,
                reason, correlationReference, claim);
    }

    static String normalizeReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.isBlank() || reason.length() > 1000) {
            throw new BookingValidationException("A reason of 1 to 1000 characters is required");
        }
        if (reason.contains("#") || reason.matches("(?is).*https?://\\S+.*")
                || reason.matches("(?is).*www\\.\\S+.*") || UUID_TOKEN.matcher(reason).find()
                || EMAIL.matcher(reason).find() || PHONE.matcher(reason).find()) {
            throw new BookingValidationException("Reason must not contain contact details, links, or credentials");
        }
        return reason;
    }

    private void requireAccessDecision(String bookingCode, Long memberAccountId, String email, String phone) {
        CustomerBookingAccessDecision decision = bookingFacade.verifyCustomerBookingAccess(
                new CustomerBookingAccessQuery(bookingCode, memberAccountId, email, phone));
        if (decision == null || !decision.authorized()) {
            throw new BookingNotFoundException("Booking not found");
        }
    }

    private Long requireSingleBookingLocation(Long bookingId) {
        List<BookingSessionCommitment> rows = commitments.findByBookingIdOrderByOrdinalAsc(bookingId);
        if (rows.isEmpty()) throw new BookingConflictException("Booking location evidence is unavailable");
        Long locationId = rows.get(0).getLocationId();
        if (locationId == null || rows.stream().anyMatch(row -> !locationId.equals(row.getLocationId()))) {
            throw new BookingConflictException("Booking location evidence is inconsistent");
        }
        return locationId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
