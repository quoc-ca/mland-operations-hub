package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Rechecks proof under the booking lock and commits capacity release, audit and idempotent response together. */
@Service
@RequiredArgsConstructor
public class BookingPendingHoldReleaseTransactionService {

    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final WorkshopBookingFacade bookingFacade;
    private final WorkshopBookingAuthorizationService authorization;
    private final BookingHoldReleaseService holdRelease;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Transactional
    public BookingRecoveryCommandResult release(String authorizationHeader, String bookingCode,
                                                String actorType, Long expectedActorId,
                                                String contactEmail, String contactPhone,
                                                String reason, String correlationReference,
                                                BookingIdempotencyClaim claim) {
        WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElse(null);
        if (booking == null) return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));

        List<BookingSessionCommitment> rows = commitments.findByBookingIdOrderByOrdinalAsc(booking.getId());
        Long locationId = rows.isEmpty() ? null : rows.get(0).getLocationId();
        if (locationId == null || rows.stream().anyMatch(row -> !locationId.equals(row.getLocationId()))) {
            return complete(claim, 409, problem(409, "CONFLICT", "Booking location evidence is unavailable", correlationReference));
        }

        if ("MEMBER".equals(actorType)) {
            AccountSummary member;
            try {
                member = authorization.requireMemberOwnership(authorizationHeader, null,
                        bookingCode, locationId, correlationReference);
            } catch (WorkshopBookingUnauthorizedException ex) {
                return complete(claim, 401, problem(401, "UNAUTHORIZED", "Authentication is required", correlationReference));
            } catch (WorkshopBookingForbiddenException ex) {
                return complete(claim, 403, problem(403, "FORBIDDEN", "Member authorization is required", correlationReference));
            }
            if (!member.accountId().equals(expectedActorId)) {
                return complete(claim, 403, problem(403, "FORBIDDEN", "Member identity changed during the request", correlationReference));
            }
            if (!authorized(bookingCode, member.accountId(), null, null)) {
                return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
            }
        } else if (!authorized(bookingCode, null, contactEmail, contactPhone)) {
            return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
        }

        Instant now = Instant.now();
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT
                || booking.getPaymentStatus() != PaymentStatus.UNPAID
                || booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(now)) {
            return complete(claim, 409, problem(409, "CONFLICT", "Booking no longer has an active unpaid hold", correlationReference));
        }

        String operationReference = "REL-CUSTOMER-" + claim.recordId();
        holdRelease.releasePendingHold(bookingCode, operationReference, correlationReference,
                actorType, expectedActorId, reason);
        return complete(claim, 204, "");
    }

    private boolean authorized(String bookingCode, Long memberAccountId, String contactEmail, String contactPhone) {
        CustomerBookingAccessDecision decision = bookingFacade.verifyCustomerBookingAccess(
                new CustomerBookingAccessQuery(bookingCode, memberAccountId, contactEmail, contactPhone));
        return decision != null && decision.authorized();
    }

    private BookingRecoveryCommandResult complete(BookingIdempotencyClaim claim, int status, Object response) {
        try {
            String payload = status == 204 ? "" : mapper.writeValueAsString(response);
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), status, payload);
            return new BookingRecoveryCommandResult(status, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Pending-hold release response could not be serialized", ex);
        }
    }

    private ProblemDetailResponse problem(int status, String code, String detail, String correlationReference) {
        return ProblemDetailResponse.builder()
                .type(URI.create("urn:problem-type:workshop-booking:" + code.toLowerCase(Locale.ROOT)))
                .title(status == 404 ? "Resource Not Found" : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : "Conflict")
                .status(status).code(code).detail(detail).correlationId(correlationReference).build();
    }
}
