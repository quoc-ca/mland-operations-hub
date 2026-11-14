package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens the single gateway payment of a booking group (003.1 FR-038, FR-032).
 *
 * <p>One attempt covers the whole group, for the group collection, which the FR-036 floor guarantees is at
 * least 5,000 VND. The link expires at {@code holdExpiresAt − 2 minutes}, which is the group's stored
 * payment deadline and the only deadline a customer is shown; retrying or recreating the payment never
 * extends the hold, so a new attempt inherits the same deadline.
 */
@Service
@RequiredArgsConstructor
public class GroupPaymentService {

    private static final DateTimeFormatter PROVIDER_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));

    private final BookingGroupRepository groups;
    private final GroupPaymentAttemptRepository attempts;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository tickets;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository priceSnapshots;
    private final PaymentGatewayCheckoutPort gateway;
    private final PaymentOwnerRegistrationPort owners;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Transactional
    public GroupPaymentAttemptResponse initiate(String groupCode, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        if (groupCode == null || groupCode.isBlank()) {
            throw new BookingValidationException("groupCode is required");
        }
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey.trim(), "START_GROUP_PAYMENT",
                "groupCode=" + groupCode, UUID.randomUUID().toString());
        if (claim.cachedOutcome()) {
            try {
                return mapper.readValue(claim.responsePayload(), GroupPaymentAttemptResponse.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("The stored group payment response could not be read", ex);
            }
        }
        Instant now = Instant.now();
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking group " + groupCode + " was not found"));
        if (group.getPaymentMode() != GroupPaymentMode.VNPAY) {
            throw new BookingConflictException("This group collects nothing at booking and needs no gateway payment");
        }
        if (group.getGroupStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException("Booking group is " + group.getGroupStatus()
                    + " and is not awaiting payment");
        }
        if (group.getPaymentDeadlineAt() == null || !group.getPaymentDeadlineAt().isAfter(now)) {
            throw new BookingCreationRejectedException("GROUP_PAYMENT_WINDOW_CLOSED",
                    "The payment window of this group has closed");
        }

        Optional<GroupPaymentAttempt> latest = attempts.findByGroupIdOrderByCreatedAtDesc(group.getId())
                .stream().findFirst();
        if (latest.isPresent() && latest.get().getAttemptStatus() == PaymentAttemptStatus.PENDING) {
            GroupPaymentAttempt current = latest.get();
            if (current.getExpiresAt().isAfter(now)) {
                // Retrying never extends the hold, so the live attempt is handed back unchanged (FR-032).
                return complete(claim, response(current));
            }
            current.setAttemptStatus(PaymentAttemptStatus.EXPIRED);
            attempts.save(current);
        }

        long collection = recomputedCollectionVnd(group);
        // The link dies with the payment deadline, two minutes before the hold, never later.
        Instant expiresAt = group.getPaymentDeadlineAt();
        String merchantReference = "GRP-" + groupCode + "-" + UUID.randomUUID();
        String providerCreateDate = PROVIDER_DATE.format(now);
        String url = gateway.createDepositUrl(merchantReference, groupCode, collection, now,
                expiresAt, clientIp);
        GroupPaymentAttempt attempt = attempts.saveAndFlush(GroupPaymentAttempt.builder()
                .group(group)
                .attemptReference("GATT-" + UUID.randomUUID())
                .merchantReference(merchantReference)
                .amountVnd(collection)
                .providerCreateDate(providerCreateDate)
                .paymentUrlSnapshot(url)
                .attemptStatus(PaymentAttemptStatus.PENDING)
                .expiresAt(expiresAt)
                .build());
        // The booking-time collection of a group is a deposit-kind owner, as a single booking's is.
        owners.registerOwner(merchantReference, groupCode, "DEPOSIT", attempt.getAmountVnd(), "VND",
                providerCreateDate);
        return complete(claim, response(attempt));
    }

    /**
     * FR-036: the money sent to the gateway is re-added from the tickets' own snapshots, never trusted from the
     * stored group total. A total that differs, or one under the gateway minimum, is refused with a typed 409.
     */
    private long recomputedCollectionVnd(BookingGroup group) {
        long sum = 0;
        for (var ticket : tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())) {
            var snapshot = priceSnapshots.findByTicketId(ticket.getId()).orElseThrow(() ->
                    new BookingCreationRejectedException("GROUP_COLLECTION_MISMATCH",
                            "A ticket of this group has no price snapshot, so the payment cannot be created"));
            sum = Math.addExact(sum, snapshot.getCollectedAtBookingVnd());
        }
        if (sum != group.getGroupCollectionVnd()) {
            throw new BookingCreationRejectedException("GROUP_COLLECTION_MISMATCH",
                    "The amount to collect no longer matches the tickets of this group");
        }
        if (sum < TicketBenefitAllocator.VNPAY_MINIMUM_VND) {
            throw new BookingCreationRejectedException("GROUP_COLLECTION_BELOW_MINIMUM",
                    "The amount to collect is below the gateway minimum of "
                            + TicketBenefitAllocator.VNPAY_MINIMUM_VND + " VND");
        }
        return sum;
    }

    private GroupPaymentAttemptResponse complete(BookingIdempotencyClaim claim,
                                                 GroupPaymentAttemptResponse response) {
        try {
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 200,
                    mapper.writeValueAsString(response));
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The group payment response could not be stored", ex);
        }
    }

    private static GroupPaymentAttemptResponse response(GroupPaymentAttempt attempt) {
        return new GroupPaymentAttemptResponse(attempt.getAttemptReference(), attempt.getMerchantReference(),
                attempt.getAmountVnd(), "VND", attempt.getPaymentUrlSnapshot(), attempt.getExpiresAt(),
                attempt.getAttemptStatus().name());
    }
}
