package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestDesignSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public interface GuestDesignSessionService {

    GuestDesignSession issueGuestSession(String correlationId);

    GuestDesignSession resolveAndTouchSession(String sessionReference);

    void bindDesign(String sessionReference, String designReference);

    Optional<GuestDesignSession> findByReference(String sessionReference);

    void expireStaleSessions();

    static String computeDigest(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
