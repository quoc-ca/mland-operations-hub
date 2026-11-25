package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds operation references that fit the 128-character reference columns
 * (idempotency ledgers, outboxes). The Idempotency-Key header may be up to 128
 * characters, so "PREFIX-ticket-key" can overflow; an over-long result keeps the readable prefix
 * and replaces the key by its SHA-256 hex, which stays deterministic for replays.
 */
final class OperationReferences {

    static final int MAX_LENGTH = 128;

    private OperationReferences() {
    }

    static String of(String prefix, String key) {
        String joined = prefix + key;
        if (joined.length() <= MAX_LENGTH) {
            return joined;
        }
        return prefix + sha256Hex(key);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
