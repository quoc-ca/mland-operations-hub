package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupActionOutcomeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.ContactProof;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.GroupLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.GroupMagicLinkExchangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.GroupMagicLinkIncidentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.GroupPendingHoldReleaseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupRecoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Group read, lookup, Magic Link and support routes that replace the single-booking ones (003.1 FR-047, FR-048). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingGroupRecoveryController {

    private final BookingGroupRecoveryService recovery;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @GetMapping("/workshop-booking-groups/{groupCode}")
    public ResponseEntity<BookingGroupView> readOwned(
            @PathVariable String groupCode,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return noStore(recovery.readOwned(authorization, groupCode));
    }

    @GetMapping("/members/me/workshop-booking-groups")
    public ResponseEntity<List<BookingGroupView>> listOwned(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return noStore(recovery.listOwned(authorization));
    }

    @PostMapping("/workshop-booking-group-lookups")
    public ResponseEntity<BookingGroupView> lookup(@Valid @RequestBody GroupLookupRequest request) {
        return noStore(recovery.lookupGuest(request.groupCode(), request.contactEmail(), request.contactPhone()));
    }

    @PostMapping("/workshop-booking-group-magic-links/exchanges")
    public ResponseEntity<BookingGroupView> exchange(@Valid @RequestBody GroupMagicLinkExchangeRequest request) {
        return noStore(recovery.exchangeMagicLink(request.token()));
    }

    @PostMapping("/workshop-booking-groups/{groupCode}/pending-hold-release")
    public ResponseEntity<Void> releasePendingHold(
            @PathVariable String groupCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GroupPendingHoldReleaseRequest request) {
        requireKey(idempotencyKey);
        String correlation = correlation(correlationId);
        BookingGroupRecoveryService.Actor actor = recovery.resolveActor(authorization);
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, "RELEASE_GROUP_PENDING_HOLD",
                groupCode + "|" + proofKey(request.contactEmail(), request.contactPhone()) + "|"
                        + request.reason().trim() + "|" + actor.key(), correlation);
        if (!claim.cachedOutcome()) {
            recovery.releasePendingHold(groupCode, actor, request.contactEmail(), request.contactPhone(),
                    request.reason(), correlation, claim);
            return ResponseEntity.noContent().build();
        }
        if (claim.statusCode() != null && claim.statusCode() == 204) return ResponseEntity.noContent().build();
        throw new IllegalStateException("Unexpected stored outcome for a pending-hold release");
    }

    @PostMapping("/workshop-booking-groups/{groupCode}/confirmation-deliveries")
    public ResponseEntity<GroupActionOutcomeResponse> resendConfirmation(
            @PathVariable String groupCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) ContactProof request) {
        requireKey(idempotencyKey);
        String correlation = correlation(correlationId);
        BookingGroupRecoveryService.Actor actor = recovery.resolveActor(authorization);
        String email = request == null ? null : request.contactEmail();
        String phone = request == null ? null : request.contactPhone();
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, "RESEND_GROUP_CONFIRMATION",
                groupCode + "|" + proofKey(email, phone) + "|" + actor.key(), correlation);
        if (claim.cachedOutcome()) return cached(claim);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(recovery.resendConfirmation(groupCode, actor, email, phone, correlation, claim));
    }

    @PostMapping("/workshop-booking-groups/{groupCode}/magic-link-incidents")
    public ResponseEntity<GroupActionOutcomeResponse> magicLinkIncident(
            @PathVariable String groupCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GroupMagicLinkIncidentRequest request) {
        requireKey(idempotencyKey);
        String correlation = correlation(correlationId);
        BookingGroupRecoveryService.Actor actor = recovery.resolveActor(authorization);
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, "GROUP_MAGIC_LINK_INCIDENT",
                groupCode + "|" + request.action() + "|" + request.reason().trim() + "|" + actor.key(), correlation);
        if (claim.cachedOutcome()) return cached(claim);
        return ResponseEntity.ok(recovery.handleMagicLinkIncident(groupCode, actor, request.action(),
                request.reason(), correlation, claim));
    }

    private ResponseEntity<GroupActionOutcomeResponse> cached(BookingIdempotencyClaim claim) {
        try {
            return ResponseEntity.status(claim.statusCode())
                    .body(mapper.readValue(claim.responsePayload(), GroupActionOutcomeResponse.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The stored group action outcome could not be read", ex);
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** A digest of the proof, so the idempotency record never keeps contact data in clear. */
    private String proofKey(String email, String phone) {
        String raw = (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + (phone == null ? "" : phone.trim());
        return idempotency.computeFingerprint(raw);
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new BookingValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
    }

    private static String correlation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId.trim();
    }
}
