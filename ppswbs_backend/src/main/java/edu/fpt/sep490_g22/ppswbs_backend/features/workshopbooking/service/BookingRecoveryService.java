package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.MagicLinkIncidentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ConfirmationDeliveryRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.LatePaymentFollowUpRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;

/** Coordinates authorized, idempotent Booking recovery commands. */
@Service
@RequiredArgsConstructor
public class BookingRecoveryService {

    private static final Pattern UUID_TOKEN = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}(?![0-9a-f])");

    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final WorkshopBookingAuthorizationService authorization;
    private final BookingIdempotencyService idempotency;
    private final BookingRecoveryTransactionService transactionService;
    private final WorkshopBookingFacade bookingFacade;
    private final ObjectMapper mapper;

    public BookingRecoveryCommandResult handleMagicLinkIncident(
            String authorizationHeader,
            String bookingCode,
            String idempotencyKey,
            String correlationReference,
            MagicLinkIncidentRequest request) {
        ensureReasonContainsNoCredential(request.getReason());
        String normalizedCode = bookingCode == null ? "" : bookingCode.trim();
        WorkshopBooking booking = bookings.findByBookingCode(normalizedCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        Long locationId = requireSingleBookingLocation(booking.getId());

        // Reject unauthenticated/non-Manager calls before reserving an idempotency claim.
        authorization.requireMagicLinkIncidentAuthority(authorizationHeader, locationId,
                normalizedCode, request.getAction(), request.getReason(), correlationReference);

        String semanticContent = normalizedCode + "|" + request.getAction() + "|" + request.getReason().trim();
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey.trim(),
                "MAGIC_LINK_INCIDENT", semanticContent, correlationReference);
        if (claim.cachedOutcome()) {
            return cached(claim);
        }
        return transactionService.applyMagicLinkIncident(authorizationHeader, normalizedCode,
                request.getAction(), request.getReason().trim(), correlationReference, claim);
    }

    public BookingRecoveryCommandResult handleLatePaymentFollowup(
            String authorizationHeader, String bookingCode, String idempotencyKey,
            String correlationReference, LatePaymentFollowUpRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        if (request == null || request.getAction() == null || request.getReason() == null) {
            throw new BookingValidationException("action and reason are required");
        }
        ensureReasonContainsNoCredential(request.getReason());
        String code = bookingCode == null ? "" : bookingCode.trim();
        WorkshopBooking booking = bookings.findByBookingCode(code)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        Long locationId = requireSingleBookingLocation(booking.getId());
        authorization.requireLatePaymentFollowupAuthority(authorizationHeader, locationId, code,
                request.getAction(), request.getReason().trim(), correlationReference);
        String action = request.getAction().trim();
        String reason = request.getReason().trim();
        if (!"RECORD".equals(action) && !"ESCALATE".equals(action)) {
            throw new BookingValidationException("action must be RECORD or ESCALATE");
        }
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey.trim(),
                "LATE_PAYMENT_FOLLOW_UP", code + "|" + action + "|" + reason, correlationReference);
        if (claim.cachedOutcome()) return cached(claim);
        return transactionService.handleLatePaymentFollowup(authorizationHeader, code, action, reason,
                correlationReference, claim);
    }

    public BookingRecoveryCommandResult requestConfirmationDelivery(
            String authorizationHeader, String bookingCode, String idempotencyKey,
            String correlationReference, ConfirmationDeliveryRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        String normalizedCode = bookingCode == null ? "" : bookingCode.trim();
        if (normalizedCode.isBlank()) throw new BookingNotFoundException("Booking not found");
        WorkshopBooking booking = bookings.findByBookingCode(normalizedCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        Long locationId = requireSingleBookingLocation(booking.getId());
        ConfirmationDeliveryRequest safeRequest = request == null ? new ConfirmationDeliveryRequest() : request;
        String actorType;
        Long actorId = null;
        String email = blankToNull(safeRequest.getContactEmail());
        String phone = blankToNull(safeRequest.getContactPhone());

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            if ((email == null) == (phone == null)) {
                throw new BookingValidationException("Exactly one stored contact proof is required");
            }
            CustomerBookingAccessDecision decision = bookingFacade.verifyCustomerBookingAccess(
                    new CustomerBookingAccessQuery(normalizedCode, null, email, phone));
            if (decision == null || !decision.authorized()) throw new BookingNotFoundException("Booking not found");
            actorType = "GUEST";
        } else {
            if (email != null || phone != null) {
                throw new BookingValidationException("Authenticated callers must not submit contact proof");
            }
            AccountSummary account = authorization.authenticate(authorizationHeader);
            actorType = account.role().name();
            actorId = account.accountId();
            if (account.role() == ActorRole.MEMBER) {
                if (booking.getMemberAccountId() == null) throw new BookingNotFoundException("Booking not found");
                authorization.requireMemberOwnership(authorizationHeader, booking.getMemberAccountId(),
                        normalizedCode, locationId, correlationReference);
            } else {
                authorization.requireConfirmationResendAuthority(authorizationHeader, locationId,
                        normalizedCode, correlationReference);
            }
        }

        String proofFingerprint = idempotency.computeFingerprint(String.valueOf(email) + "|" + String.valueOf(phone));
        String semanticContent = normalizedCode + "|" + actorType + "|" + actorId + "|" + proofFingerprint;
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey.trim(),
                "CONFIRMATION_DELIVERY", semanticContent, correlationReference);
        if (claim.cachedOutcome()) return cached(claim);
        return transactionService.requestConfirmationDelivery(authorizationHeader, normalizedCode,
                email, phone, actorType, actorId, correlationReference, claim);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureReasonContainsNoCredential(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.contains("#") || normalized.matches("(?is).*https?://\\S+.*")
                || UUID_TOKEN.matcher(normalized).find()) {
            throw new BookingValidationException("Reason must not contain a URL, link fragment, or token");
        }
    }

    private Long requireSingleBookingLocation(Long bookingId) {
        List<BookingSessionCommitment> rows = commitments.findByBookingIdOrderByOrdinalAsc(bookingId);
        if (rows.isEmpty()) throw new BookingConflictException("Booking branch evidence is unavailable");
        Long locationId = rows.get(0).getLocationId();
        if (locationId == null || rows.stream().anyMatch(row -> !locationId.equals(row.getLocationId()))) {
            throw new BookingConflictException("Booking branch evidence is inconsistent");
        }
        return locationId;
    }

    private BookingRecoveryCommandResult cached(BookingIdempotencyClaim claim) {
        return new BookingRecoveryCommandResult(claim.statusCode(), claim.responsePayload());
    }
}
