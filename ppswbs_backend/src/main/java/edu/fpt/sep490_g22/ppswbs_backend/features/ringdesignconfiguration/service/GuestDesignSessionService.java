package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.GuestDesignSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestDesignSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestDesignSessionService {

    private final GuestDesignSessionRepository sessionRepository;

    @Transactional
    public GuestDesignSession issueGuestSession(String correlationId) {
        String sessionReference = "GDS-" + UUID.randomUUID();
        String sessionDigest = computeDigest(sessionReference);
        Instant now = Instant.now();

        GuestDesignSession session = GuestDesignSession.builder()
                .sessionReference(sessionReference)
                .sessionDigest(sessionDigest)
                .status(GuestSessionStatus.ACTIVE)
                .issuedAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusSeconds(20 * 60))
                .correlationId(correlationId)
                .build();

        return sessionRepository.save(session);
    }

    @Transactional
    public GuestDesignSession resolveAndTouchSession(String sessionReference) {
        if (sessionReference == null || sessionReference.isBlank()) {
            throw RingDesignException.sessionExpired("X-Guest-Design-Session header không được để trống đối với khách vãng lai.");
        }

        GuestDesignSession session = sessionRepository.findBySessionReference(sessionReference.trim())
                .orElseThrow(() -> RingDesignException.sessionExpired("Guest session không tồn tại hoặc đã hết hiệu lực."));

        Instant now = Instant.now();
        if (session.isExpired(now)) {
            session.setStatus(GuestSessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw RingDesignException.sessionExpired("Phiên làm việc của khách vãng lai đã hết hạn sau 20 phút không hoạt động.");
        }

        session.touch(now);
        return sessionRepository.save(session);
    }

    @Transactional
    public void bindDesign(String sessionReference, String designReference) {
        GuestDesignSession session = resolveAndTouchSession(sessionReference);
        session.setDesignReference(designReference);
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<GuestDesignSession> findByReference(String sessionReference) {
        if (sessionReference == null || sessionReference.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findBySessionReference(sessionReference.trim());
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void expireStaleSessions() {
        Instant now = Instant.now();
        int expiredCount = sessionRepository.expireStaleSessions(GuestSessionStatus.ACTIVE, GuestSessionStatus.EXPIRED, now);
        if (expiredCount > 0) {
            log.info("Purged {} inactive guest design sessions expired before {}", expiredCount, now);
        }
    }

    public static String computeDigest(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}

