package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiPromptQuotaUsage;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiPromptQuotaUsageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPromptQuotaService {

    private final AiPromptQuotaUsageRepository quotaRepository;

    @Value("${ai.support.quota.guest-daily:3}")
    private int guestDailyLimit = 3;

    @Value("${ai.support.quota.member-daily:10}")
    private int memberDailyLimit = 10;

    @Value("${ai.support.quota.time-zone:Asia/Ho_Chi_Minh}")
    private String timeZone = "Asia/Ho_Chi_Minh";

    @Value("${ai.support.quota.hmac-secret:}")
    private String hmacSecret = "";

    public record QuotaResult(
            String actor,
            int limit,
            int remaining,
            String resetAt,
            long retryAfterSeconds
    ) {
        public DesignReviewDtos.AiQuotaDto toDto() {
            return new DesignReviewDtos.AiQuotaDto(actor, limit, remaining, resetAt);
        }
    }

    /**
     * Quota accounting must commit independently from the provider call. A
     * provider timeout or policy rejection is still a consumed prompt and
     * must not roll the counter back with the outer AI interaction transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuotaResult consumeQuota(String actorScope, String rawIdentifier, String correlationId) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = LocalDate.now(zone);
        ZonedDateTime nextMidnight = today.plusDays(1).atStartOfDay(zone);
        String resetAt = nextMidnight.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        long retryAfterSeconds = Math.max(1, Duration.between(ZonedDateTime.now(zone), nextMidnight).toSeconds());

        int limit = "MEMBER".equalsIgnoreCase(actorScope) ? memberDailyLimit : guestDailyLimit;
        String keyDigest = computeKeyDigest(actorScope, rawIdentifier);

        // INSERT IGNORE is safe for concurrent first requests because the
        // unique key is followed by the atomic conditional UPDATE.
        quotaRepository.insertIfAbsent(actorScope, keyDigest, today, limit);
        int incremented = quotaRepository.incrementIfAvailable(actorScope, keyDigest, today);
        AiPromptQuotaUsage usage = quotaRepository
                .findByQuotaScopeAndQuotaKeyDigestAndQuotaDate(actorScope, keyDigest, today)
                .orElseThrow(() -> new IllegalStateException("AI quota row disappeared after atomic update"));

        if (incremented == 0) {
            log.warn("AI daily prompt quota exceeded: actor={}, keyDigestPrefix={}...",
                    actorScope, keyDigest.substring(0, Math.min(10, keyDigest.length())));
            DesignReviewDtos.AiQuotaDto quotaDto = new DesignReviewDtos.AiQuotaDto(
                    actorScope, usage.getLimitCount(), 0, resetAt);
            throw DesignReviewException.quotaExceeded("Bạn đã sử dụng hết số lượt hỏi AI trong ngày.",
                    retryAfterSeconds, quotaDto, correlationId);
        }

        return new QuotaResult(actorScope, usage.getLimitCount(),
                Math.max(0, usage.getLimitCount() - usage.getUsedCount()), resetAt, retryAfterSeconds);
    }

    @Transactional(readOnly = true)
    public DesignReviewDtos.AiQuotaDto peekQuota(String actorScope, String rawIdentifier) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = LocalDate.now(zone);
        ZonedDateTime nextMidnight = today.plusDays(1).atStartOfDay(zone);
        String resetAt = nextMidnight.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        int limit = "MEMBER".equalsIgnoreCase(actorScope) ? memberDailyLimit : guestDailyLimit;

        String keyDigest = computeKeyDigest(actorScope, rawIdentifier);
        Optional<AiPromptQuotaUsage> opt = quotaRepository.findByQuotaScopeAndQuotaKeyDigestAndQuotaDate(actorScope, keyDigest, today);
        int remaining = opt.map(usage -> Math.max(0, usage.getLimitCount() - usage.getUsedCount())).orElse(limit);

        return new DesignReviewDtos.AiQuotaDto(actorScope, limit, remaining, resetAt);
    }

    public String computeKeyDigest(String scope, String identifier) {
        String data = scope + ":" + (identifier != null ? identifier.trim() : "anonymous");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] keyBytes = (hmacSecret != null && !hmacSecret.isBlank())
                    ? hmacSecret.getBytes(StandardCharsets.UTF_8)
                    : "mland-default-dev-hmac-secret-ai-quota-2026".getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC SHA-256 for quota key", e);
        }
    }
}
