package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingBuyerVerificationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingBuyerVerification;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Buyer verification by OTP (003.1 FR-041, decision 2026-11-13). The OTP goes only to the group's original
 * contact email, is valid 10 minutes with at most 5 wrong attempts, and only its salted hash is stored. A success
 * yields a single-use buyerVerificationReference valid 15 minutes. A new request supersedes the active challenge;
 * requests are at least 60 seconds apart and at most 5 per hour per group. Neither the response nor the timing
 * path reveals whether the supplied contact matched.
 */
@Service
@Slf4j
public class BuyerVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingGroupRepository groups;
    private final BookingBuyerVerificationRepository verifications;
    private final GroupNotificationService notifications;
    private final Duration otpTtl;
    private final Duration referenceTtl;
    private final Duration cooldown;
    private final int maxPerHour;
    private final int maxAttempts;

    public BuyerVerificationService(
            BookingGroupRepository groups,
            BookingBuyerVerificationRepository verifications,
            GroupNotificationService notifications,
            @Value("${workshop.booking.buyer-verification.otp-ttl:PT10M}") Duration otpTtl,
            @Value("${workshop.booking.buyer-verification.reference-ttl:PT15M}") Duration referenceTtl,
            @Value("${workshop.booking.buyer-verification.resend-cooldown:PT60S}") Duration cooldown,
            @Value("${workshop.booking.buyer-verification.max-sends-per-hour:5}") int maxPerHour,
            @Value("${workshop.booking.buyer-verification.max-wrong-attempts:5}") int maxAttempts) {
        this.groups = groups;
        this.verifications = verifications;
        this.notifications = notifications;
        this.otpTtl = otpTtl;
        this.referenceTtl = referenceTtl;
        this.cooldown = cooldown;
        this.maxPerHour = maxPerHour;
        this.maxAttempts = maxAttempts;
    }

    /** {@code maskedEmail} is set only for a Staff or Manager request (they do not know the booking's email); never for a buyer. */
    public record Challenge(String verificationId, Instant expiresAt, String maskedEmail) {
        public Challenge(String verificationId, Instant expiresAt) {
            this(verificationId, expiresAt, null);
        }
    }

    public record VerifiedReference(String buyerVerificationReference, Instant expiresAt) { }

    /** A consumed proof: the verification row, now CONSUMED. */
    public record ConsumedProof(Long verificationId) { }

    @Transactional
    public Challenge requestChallenge(String groupCode, String contactEmail, String contactPhone,
                                      String correlationReference) {
        boolean hasEmail = contactEmail != null && !contactEmail.isBlank();
        boolean hasPhone = contactPhone != null && !contactPhone.isBlank();
        if (hasEmail == hasPhone) {
            throw new BookingValidationException("Provide exactly one of contactEmail or contactPhone");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String verificationId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(otpTtl);
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode).orElse(null);
        if (group == null) {
            // Unknown group: same answer as a mismatch, nothing stored.
            return new Challenge(verificationId, expiresAt);
        }
        requireWithinSendLimits(group, now);
        boolean matches = group.getGroupStatus() == BookingStatus.CONFIRMED && (hasEmail
                ? EmailMasking.normalize(contactEmail).equals(EmailMasking.normalize(group.getContactEmail()))
                : ContactPhones.sameContact(contactPhone, group.getContactPhone()));
        if (!matches) {
            verifications.save(BookingBuyerVerification.builder()
                    .verificationReference(verificationId).group(group)
                    .status(BookingBuyerVerification.PROOF_MISMATCH).otpExpiresAt(expiresAt).createdAt(now).build());
            return new Challenge(verificationId, expiresAt);
        }
        issue(group, verificationId, now, expiresAt, correlationReference);
        return new Challenge(verificationId, expiresAt);
    }

    /**
     * Staff or Manager request (FR-048, T148): the caller's authority over the group is checked by the caller; no
     * contact value is sent and the OTP still goes only to the group's original contact email. Same send limits.
     */
    @Transactional
    public Challenge requestChallengeForStaff(String groupCode, String correlationReference) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode)
                .filter(g -> g.getGroupStatus() == BookingStatus.CONFIRMED)
                .orElseThrow(() -> new WorkshopBookingProblemException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Booking not found"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        requireWithinSendLimits(group, now);
        String verificationId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(otpTtl);
        issue(group, verificationId, now, expiresAt, correlationReference);
        return new Challenge(verificationId, expiresAt, EmailMasking.mask(group.getContactEmail()));
    }

    private void requireWithinSendLimits(BookingGroup group, Instant now) {
        List<BookingBuyerVerification> recent = verifications.findRecentByGroup(group.getId(), now.minus(Duration.ofHours(1)));
        if (recent.size() >= maxPerHour
                || (!recent.isEmpty() && recent.getFirst().getCreatedAt().isAfter(now.minus(cooldown)))) {
            throw new WorkshopBookingProblemException(HttpStatus.TOO_MANY_REQUESTS, "BUYER_VERIFICATION_RATE_LIMITED",
                    "Please wait before requesting another verification code");
        }
    }

    private void issue(BookingGroup group, String verificationId, Instant now, Instant expiresAt,
                       String correlationReference) {
        for (BookingBuyerVerification active : verifications.findByGroupAndStatus(group.getId(), BookingBuyerVerification.PENDING)) {
            active.setStatus(BookingBuyerVerification.SUPERSEDED);
        }
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        String salt = HexFormat.of().formatHex(randomBytes(16));
        verifications.save(BookingBuyerVerification.builder()
                .verificationReference(verificationId).group(group).status(BookingBuyerVerification.PENDING)
                .otpHash(hash(salt, otp)).otpSalt(salt).otpExpiresAt(expiresAt).createdAt(now).build());
        notifications.queue(group, null, GroupNotificationService.KIND_BUYER_VERIFICATION_OTP, group.getContactEmail(),
                "MLAND – Mã xác minh đặt chỗ " + group.getGroupCode(),
                otpBody(group.getGroupCode(), otp, otpTtl), List.of(), expiresAt, correlationReference);
        log.info("Buyer verification challenge issued for group {}", group.getGroupCode());
    }

    /**
     * Checks the OTP. A wrong code is counted and committed even though the caller is refused, so this runs in its
     * own transaction and returns the refusal instead of throwing inside it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmResult confirm(String groupCode, String verificationId, String otp) {
        if (otp == null || !otp.matches("^[0-9]{6}$")) {
            return ConfirmResult.failed();
        }
        BookingBuyerVerification verification = verifications.findByVerificationReferenceForUpdate(verificationId).orElse(null);
        if (verification == null || !verification.getGroup().getGroupCode().equals(groupCode)
                || BookingBuyerVerification.PROOF_MISMATCH.equals(verification.getStatus())) {
            return ConfirmResult.failed();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (BookingBuyerVerification.LOCKED.equals(verification.getStatus())) {
            return ConfirmResult.of("BUYER_VERIFICATION_LOCKED");
        }
        if (!BookingBuyerVerification.PENDING.equals(verification.getStatus()) || !verification.getOtpExpiresAt().isAfter(now)) {
            return ConfirmResult.of("BUYER_VERIFICATION_EXPIRED");
        }
        if (!MessageDigest.isEqual(hash(verification.getOtpSalt(), otp).getBytes(StandardCharsets.US_ASCII),
                verification.getOtpHash().getBytes(StandardCharsets.US_ASCII))) {
            verification.setFailedAttempts(verification.getFailedAttempts() + 1);
            if (verification.getFailedAttempts() >= maxAttempts) {
                verification.setStatus(BookingBuyerVerification.LOCKED);
                return ConfirmResult.of("BUYER_VERIFICATION_LOCKED");
            }
            return ConfirmResult.failed();
        }
        verification.setStatus(BookingBuyerVerification.VERIFIED);
        verification.setOtpHash(null);
        verification.setOtpSalt(null);
        verification.setBuyerVerificationReference(UUID.randomUUID().toString());
        verification.setReferenceExpiresAt(now.plus(referenceTtl));
        return new ConfirmResult(null, new VerifiedReference(verification.getBuyerVerificationReference(),
                verification.getReferenceExpiresAt()));
    }

    /**
     * Consumes a buyerVerificationReference for one recipient change of this group, inside the caller's
     * transaction (a rollback restores it). Unknown or foreign references are refused as missing proof.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConsumedProof consume(BookingGroup group, String buyerVerificationReference) {
        if (buyerVerificationReference == null || buyerVerificationReference.isBlank()) {
            throw new WorkshopBookingProblemException(HttpStatus.UNAUTHORIZED, "BUYER_VERIFICATION_REQUIRED",
                    "Buyer verification is required");
        }
        BookingBuyerVerification verification = verifications
                .findByBuyerVerificationReferenceForUpdate(buyerVerificationReference.trim()).orElse(null);
        if (verification == null || !verification.getGroup().getId().equals(group.getId())) {
            throw new WorkshopBookingProblemException(HttpStatus.UNAUTHORIZED, "BUYER_VERIFICATION_REQUIRED",
                    "Buyer verification is required");
        }
        Instant now = Instant.now();
        if (!BookingBuyerVerification.VERIFIED.equals(verification.getStatus())
                || verification.getReferenceExpiresAt() == null || !verification.getReferenceExpiresAt().isAfter(now)) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "BUYER_VERIFICATION_EXPIRED",
                    "Buyer verification has expired or was already used");
        }
        verification.setStatus(BookingBuyerVerification.CONSUMED);
        verification.setConsumedAt(now);
        return new ConsumedProof(verification.getId());
    }

    /**
     * Verifies and consumes buyer proof for group operations (003.1 T107 for 003.3 T099, T100, T101).
     * A Member is accepted when they own the group; otherwise the single-use buyerVerificationReference
     * must be valid and is marked CONSUMED. Never reveals whether the group exists to a stranger.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConsumeBuyerProofResult consumeProof(String groupCode, Long memberAccountId, String buyerVerificationReference) {
        if (groupCode == null || groupCode.isBlank()) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_REQUIRED);
        }
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode.trim()).orElse(null);
        if (group == null) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_REQUIRED);
        }
        if (memberAccountId != null && group.getBuyerMemberAccountId() != null
                && group.getBuyerMemberAccountId().equals(memberAccountId)) {
            return ConsumeBuyerProofResult.accepted();
        }
        if (buyerVerificationReference == null || buyerVerificationReference.isBlank()) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_REQUIRED);
        }
        BookingBuyerVerification verification = verifications
                .findByBuyerVerificationReferenceForUpdate(buyerVerificationReference.trim()).orElse(null);
        if (verification == null || !verification.getGroup().getId().equals(group.getId())) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_REQUIRED);
        }
        if (BookingBuyerVerification.LOCKED.equals(verification.getStatus())) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_FAILED);
        }
        Instant now = Instant.now();
        if (!BookingBuyerVerification.VERIFIED.equals(verification.getStatus())
                || verification.getReferenceExpiresAt() == null || !verification.getReferenceExpiresAt().isAfter(now)) {
            return ConsumeBuyerProofResult.refused(ConsumeBuyerProofResult.CODE_EXPIRED);
        }
        verification.setStatus(BookingBuyerVerification.CONSUMED);
        verification.setConsumedAt(now);
        verifications.save(verification);
        return ConsumeBuyerProofResult.accepted();
    }

    public record ConfirmResult(String errorCode, VerifiedReference reference) {
        static ConfirmResult failed() {
            return new ConfirmResult("BUYER_VERIFICATION_FAILED", null);
        }

        static ConfirmResult of(String code) {
            return new ConfirmResult(code, null);
        }
    }

    private static String otpBody(String groupCode, String otp, Duration ttl) {
        return "Xin chào,\n\n"
                + "Mã xác minh để quản lý người nhận vé của đặt chỗ " + groupCode + " là:\n\n"
                + otp + "\n\n"
                + "Mã có hiệu lực trong " + ttl.toMinutes() + " phút và chỉ dùng được một lần. Mã này là riêng của bạn, đừng chia sẻ cho "
                + "người khác; nhân viên MLAND chỉ hỏi mã khi bạn đang ở quầy.\n\n"
                + "Nếu bạn không yêu cầu mã, hãy bỏ qua email này; đặt chỗ của bạn không thay đổi.";
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static String hash(String salt, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((salt + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
