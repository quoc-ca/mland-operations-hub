package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiGuestSession;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiGuestSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGuestSessionService {

    private final AiGuestSessionRepository sessionRepository;

    @Value("${ai.support.guest-session-ttl:PT24H}")
    private Duration sessionTtl = Duration.ofHours(24);

    public record GuestSessionResult(
            String rawSessionToken,
            String sessionDigest,
            boolean isNew
    ) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GuestSessionResult createGuestSession() {
        String rawToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String digest = hashSessionToken(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionTtl);

        AiGuestSession session = AiGuestSession.builder()
                .sessionDigest(digest)
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();

        sessionRepository.save(session);
        log.info("Created new AI Guest session with digest prefix: {}...", digest.substring(0, Math.min(10, digest.length())));

        return new GuestSessionResult(rawToken, digest, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<GuestSessionResult> validateAndTouch(String rawToken) {
        if (rawToken == null || rawToken.trim().isBlank()) {
            return Optional.empty();
        }

        String digest = hashSessionToken(rawToken.trim());
        Optional<AiGuestSession> sessionOpt = sessionRepository.findBySessionDigest(digest);

        if (sessionOpt.isEmpty()) {
            log.warn("AI guest session not found for digest prefix: {}...", digest.substring(0, Math.min(10, digest.length())));
            return Optional.empty();
        }

        AiGuestSession session = sessionOpt.get();
        if (!session.isValid()) {
            log.warn("AI guest session expired or inactive: digest prefix={}...", digest.substring(0, Math.min(10, digest.length())));
            return Optional.empty();
        }

        session.setLastSeenAt(Instant.now());
        sessionRepository.save(session);

        return Optional.of(new GuestSessionResult(rawToken.trim(), digest, false));
    }

    public String hashSessionToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
