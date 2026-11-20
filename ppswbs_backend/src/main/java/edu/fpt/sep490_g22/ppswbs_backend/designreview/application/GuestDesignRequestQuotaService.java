package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.GuestDesignRequestQuota;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.GuestDesignRequestQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestDesignRequestQuotaService {

    private final GuestDesignRequestQuotaRepository quotaRepository;

    public static final int GUEST_DAILY_LIMIT = 5;
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Transactional
    public void validateAndConsumeQuota(String sessionDigest) {
        if (sessionDigest == null || sessionDigest.isBlank()) {
            return;
        }

        LocalDate today = LocalDate.now(TIMEZONE);
        GuestDesignRequestQuota quota = quotaRepository.findBySessionDigestAndQuotaDate(sessionDigest, today)
                .orElseGet(() -> GuestDesignRequestQuota.builder()
                        .sessionDigest(sessionDigest)
                        .quotaDate(today)
                        .requestCount(0)
                        .build());

        if (quota.getRequestCount() >= GUEST_DAILY_LIMIT) {
            long retryAfterSeconds = computeSecondsUntilMidnight();
            log.warn("Guest daily design request limit exceeded: sessionDigest={}, count={}", sessionDigest, quota.getRequestCount());
            throw DesignReviewException.rateLimited(
                    "DESIGN_REQUEST_GUEST_DAILY_LIMIT_EXCEEDED",
                    "Bạn đã đạt giới hạn 5 yêu cầu thiết kế mỗi ngày dành cho khách vãng lai. Vui lòng đăng ký/đăng nhập tài khoản thành viên hoặc quay lại vào ngày mai.",
                    retryAfterSeconds
            );
        }

        quota.setRequestCount(quota.getRequestCount() + 1);
        quotaRepository.save(quota);
        log.info("Guest consumed design request quota: sessionDigest={}, count={}/{}", sessionDigest, quota.getRequestCount(), GUEST_DAILY_LIMIT);
    }

    private long computeSecondsUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(TIMEZONE);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(TIMEZONE);
        return Math.max(1, Duration.between(now, midnight).getSeconds());
    }
}
