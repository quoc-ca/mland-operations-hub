package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingBuyerVerificationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingBuyerVerification;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BuyerVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T123 / FR-041a: the Guest buyer OTP limits come from {@code workshop.booking.buyer-verification.*}
 * (constructor arguments here) rather than constants. Each value is overridden once and observed.
 */
class BuyerVerificationLimitsConfigTest {

    private static final String CODE = "GRP-LIMITS";
    private static final String EMAIL = "buyer@example.com";

    private BookingGroupRepository groups;
    private BookingBuyerVerificationRepository verifications;
    private GroupNotificationService notifications;
    private BookingGroup group;

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        verifications = mock(BookingBuyerVerificationRepository.class);
        notifications = mock(GroupNotificationService.class);
        group = BookingGroup.builder().id(7L).groupCode(CODE).groupStatus(BookingStatus.CONFIRMED)
                .contactEmail(EMAIL).build();
        when(groups.findByGroupCodeForUpdate(CODE)).thenReturn(Optional.of(group));
        when(verifications.findRecentByGroup(any(), any())).thenReturn(List.of());
        when(verifications.findByGroupAndStatus(any(), anyString())).thenReturn(List.of());
    }

    private BuyerVerificationService service(Duration otpTtl, Duration referenceTtl, Duration cooldown,
                                             int maxPerHour, int maxAttempts) {
        return new BuyerVerificationService(groups, verifications, notifications, otpTtl, referenceTtl, cooldown,
                maxPerHour, maxAttempts);
    }

    private BuyerVerificationService defaults() {
        return service(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(60), 5, 5);
    }

    @Test
    @DisplayName("a Staff or Manager request is answered with the masked booking email; a buyer request never is")
    void maskedEmailOnlyForStaff() {
        BuyerVerificationService.Challenge forStaff = defaults().requestChallengeForStaff(CODE, "c-staff");
        assertThat(forStaff.maskedEmail()).isEqualTo(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.EmailMasking.mask(EMAIL));
        assertThat(forStaff.maskedEmail()).contains("***@").doesNotContain(EMAIL);

        BuyerVerificationService.Challenge forBuyer = defaults().requestChallenge(CODE, EMAIL, null, "c-buyer");
        assertThat(forBuyer.maskedEmail()).isNull();
    }

    @Test
    @DisplayName("T123: OTP lifetime follows the configured otp-ttl (default 10 min, override 3 min)")
    void otpTtl_isConfigurable() {
        Instant before = Instant.now();
        BuyerVerificationService.Challenge byDefault = defaults().requestChallenge(CODE, EMAIL, null, "c1");
        assertThat(Duration.between(before, byDefault.expiresAt())).isBetween(Duration.ofMinutes(10).minusSeconds(5),
                Duration.ofMinutes(10).plusSeconds(5));

        BuyerVerificationService.Challenge shorter = service(Duration.ofMinutes(3), Duration.ofMinutes(15),
                Duration.ofSeconds(60), 5, 5).requestChallenge(CODE, EMAIL, null, "c2");
        assertThat(Duration.between(before, shorter.expiresAt())).isBetween(Duration.ofMinutes(3).minusSeconds(5),
                Duration.ofMinutes(3).plusSeconds(5));
    }

    @Test
    @DisplayName("T123: the OTP email states the configured lifetime")
    void emailBody_usesConfiguredTtl() {
        service(Duration.ofMinutes(3), Duration.ofMinutes(15), Duration.ofSeconds(60), 5, 5)
                .requestChallenge(CODE, EMAIL, null, "c");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).queue(any(), any(), eq(GroupNotificationService.KIND_BUYER_VERIFICATION_OTP), anyString(),
                anyString(), body.capture(), any(), any(), anyString());
        assertThat(body.getValue()).contains("hiệu lực trong 3 phút");
    }

    @Test
    @DisplayName("T123: resend-cooldown is configurable (default 60 s: 30 s ago refused; override 10 s accepts)")
    void resendCooldown_isConfigurable() {
        BookingBuyerVerification thirtySecondsAgo = BookingBuyerVerification.builder()
                .status(BookingBuyerVerification.PENDING).createdAt(Instant.now().minusSeconds(30)).build();
        when(verifications.findRecentByGroup(any(), any())).thenReturn(List.of(thirtySecondsAgo));

        assertThatThrownBy(() -> defaults().requestChallenge(CODE, EMAIL, null, "c"))
                .isInstanceOf(WorkshopBookingProblemException.class)
                .hasMessageContaining("wait");
        assertThat(service(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(10), 5, 5)
                .requestChallenge(CODE, EMAIL, null, "c")).isNotNull();
    }

    @Test
    @DisplayName("T123: max-sends-per-hour is configurable (default 5 refused at 5; override 2 refused at 2)")
    void maxSendsPerHour_isConfigurable() {
        List<BookingBuyerVerification> twoOld = List.of(
                BookingBuyerVerification.builder().status(BookingBuyerVerification.SUPERSEDED)
                        .createdAt(Instant.now().minusSeconds(600)).build(),
                BookingBuyerVerification.builder().status(BookingBuyerVerification.SUPERSEDED)
                        .createdAt(Instant.now().minusSeconds(900)).build());
        when(verifications.findRecentByGroup(any(), any())).thenReturn(twoOld);

        assertThat(defaults().requestChallenge(CODE, EMAIL, null, "c")).isNotNull();
        assertThatThrownBy(() -> service(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(60), 2, 5)
                .requestChallenge(CODE, EMAIL, null, "c"))
                .isInstanceOf(WorkshopBookingProblemException.class);
    }

    @Test
    @DisplayName("T123: max-wrong-attempts is configurable (override 2 locks on the second wrong code)")
    void maxWrongAttempts_isConfigurable() {
        BookingBuyerVerification pending = BookingBuyerVerification.builder().verificationReference("V-1").group(group)
                .status(BookingBuyerVerification.PENDING).otpSalt("s").otpHash("not-the-hash")
                .otpExpiresAt(Instant.now().plusSeconds(300)).createdAt(Instant.now()).build();
        when(verifications.findByVerificationReferenceForUpdate("V-1")).thenReturn(Optional.of(pending));
        BuyerVerificationService strict = service(Duration.ofMinutes(10), Duration.ofMinutes(15),
                Duration.ofSeconds(60), 5, 2);

        assertThat(strict.confirm(CODE, "V-1", "123456").errorCode()).isEqualTo("BUYER_VERIFICATION_FAILED");
        assertThat(strict.confirm(CODE, "V-1", "123456").errorCode()).isEqualTo("BUYER_VERIFICATION_LOCKED");
        assertThat(pending.getStatus()).isEqualTo(BookingBuyerVerification.LOCKED);
    }

    @Test
    @DisplayName("T123: reference-ttl is configurable (default 15 min, override 4 min)")
    void referenceTtl_isConfigurable() {
        BuyerVerificationService svc = service(Duration.ofMinutes(10), Duration.ofMinutes(4), Duration.ofSeconds(60), 5, 5);
        String otp = "654321";
        String salt = "salt";
        BookingBuyerVerification pending = BookingBuyerVerification.builder().verificationReference("V-2").group(group)
                .status(BookingBuyerVerification.PENDING).otpSalt(salt)
                .otpHash(sha256(salt, otp))
                .otpExpiresAt(Instant.now().plusSeconds(300)).createdAt(Instant.now()).build();
        when(verifications.findByVerificationReferenceForUpdate("V-2")).thenReturn(Optional.of(pending));

        Instant before = Instant.now();
        BuyerVerificationService.ConfirmResult result = svc.confirm(CODE, "V-2", otp);

        assertThat(result.errorCode()).isNull();
        assertThat(Duration.between(before, result.reference().expiresAt())).isBetween(
                Duration.ofMinutes(4).minusSeconds(5), Duration.ofMinutes(4).plusSeconds(5));
    }

    private static String sha256(String salt, String otp) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest((salt + ":" + otp).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
