package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsultationRateLimiter {

    private static final int MAX_MESSAGES_PER_MINUTE = 30;
    private static final long WINDOW_SECONDS = 60;

    private final Map<Long, Deque<Instant>> memberTimestamps = new ConcurrentHashMap<>();

    public synchronized void checkAndConsume(Long memberAccountId) {
        if (memberAccountId == null) return;

        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> timestamps = memberTimestamps.computeIfAbsent(memberAccountId, k -> new ArrayDeque<>());

        // Evict expired timestamps
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_MESSAGES_PER_MINUTE) {
            Instant oldest = timestamps.peekFirst();
            long retryAfterSeconds = oldest != null ? Math.max(1, WINDOW_SECONDS - (now.getEpochSecond() - oldest.getEpochSecond())) : WINDOW_SECONDS;
            throw ConsultationException.rateLimited(
                    "CHAT_RATE_LIMITED",
                    "Bạn đã gửi quá nhiều tin nhắn (tối đa 30 tin nhắn/phút). Vui lòng thử lại sau.",
                    (int) retryAfterSeconds
            );
        }

        timestamps.addLast(now);
    }
}
