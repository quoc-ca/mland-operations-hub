package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit on the unauthenticated group recovery routes (003.1 FR-048, T110): Guest lookup, pending-hold
 * release and confirmation resend, counted per action, group code and client. Every call counts, matching or not,
 * so the limit cannot be used to tell a right proof from a wrong one. The window lives in this instance's memory,
 * which is enough for the single-node V1 deployment; no shared store is introduced.
 */
@Component
public class GroupRecoveryRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final long windowMillis;

    @org.springframework.beans.factory.annotation.Autowired
    public GroupRecoveryRateLimiter(
            @Value("${workshop.booking.recovery.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${workshop.booking.recovery.rate-limit.window-seconds:900}") long windowSeconds) {
        this(Clock.systemUTC(), maxAttempts, windowSeconds);
    }

    GroupRecoveryRateLimiter(Clock clock, int maxAttempts, long windowSeconds) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000;
    }

    /** Counts this attempt, or throws {@link BookingRateLimitException} when the window is already full. */
    public void check(String action, String groupCode, String client) {
        long now = clock.millis();
        String key = action + "|" + (groupCode == null ? "" : groupCode.trim()) + "|" + (client == null ? "" : client);
        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= now - windowMillis) window.pollFirst();
            if (window.size() >= maxAttempts) {
                throw new BookingRateLimitException("Too many attempts for this booking group. Try again later.");
            }
            window.addLast(now);
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast() <= now - windowMillis;
                }
            });
        }
    }
}
