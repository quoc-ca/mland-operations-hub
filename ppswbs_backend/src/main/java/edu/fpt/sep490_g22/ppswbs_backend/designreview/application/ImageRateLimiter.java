package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ImageRateLimiter {

    private static final int MAX_ATTEMPTS = 3;
    private static final long WINDOW_SECONDS = 300L; // 5 minutes

    // Key: Member ID -> Deque of timestamps
    private final Map<Long, Deque<Instant>> memberAttempts = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {}

    public synchronized RateLimitResult tryAcquire(Long memberId) {
        if (memberId == null) {
            return new RateLimitResult(false, WINDOW_SECONDS);
        }

        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> timestamps = memberAttempts.computeIfAbsent(memberId, k -> new ArrayDeque<>());

        // Evict expired timestamps older than rolling 5-minute window
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_ATTEMPTS) {
            Instant oldest = timestamps.peekFirst();
            long retryAfter = WINDOW_SECONDS - (now.getEpochSecond() - oldest.getEpochSecond());
            if (retryAfter <= 0) {
                retryAfter = 1;
            }
            log.warn("Member {} exceeded image rate limit: {} attempts in window. Retry-After: {}s", memberId, timestamps.size(), retryAfter);
            return new RateLimitResult(false, retryAfter);
        }

        // Record attempt
        timestamps.addLast(now);
        log.info("Member {} consumed image rate limit slot. Attempts in window: {}/{}", memberId, timestamps.size(), MAX_ATTEMPTS);
        return new RateLimitResult(true, 0);
    }

    public synchronized void reset(Long memberId) {
        memberAttempts.remove(memberId);
    }
}
