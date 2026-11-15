package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRateLimitException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupRecoveryRateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 003.1 T110 / FR-048: Guest lookup, release and resend are limited per action, group code and client. */
class GroupRecoveryRateLimiterTest {

    @Test
    void theAttemptAfterTheLimitAnswers429AndOtherKeysAreUnaffected() {
        GroupRecoveryRateLimiter limiter = new GroupRecoveryRateLimiter(3, 900);
        for (int i = 0; i < 3; i++) limiter.check("LOOKUP", "GRP-1", "10.0.0.1");

        assertThatThrownBy(() -> limiter.check("LOOKUP", "GRP-1", "10.0.0.1"))
                .isInstanceOf(BookingRateLimitException.class);
        assertThatCode(() -> limiter.check("LOOKUP", "GRP-1", "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("LOOKUP", "GRP-2", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("RELEASE", "GRP-1", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void theWindowSlidesSoAttemptsReturnAfterIt() throws Exception {
        GroupRecoveryRateLimiter limiter = new GroupRecoveryRateLimiter(1, 1);
        limiter.check("RESEND", "GRP-1", "c");
        assertThatThrownBy(() -> limiter.check("RESEND", "GRP-1", "c")).isInstanceOf(BookingRateLimitException.class);
        Thread.sleep(1100);
        assertThatCode(() -> limiter.check("RESEND", "GRP-1", "c")).doesNotThrowAnyException();
    }
}
