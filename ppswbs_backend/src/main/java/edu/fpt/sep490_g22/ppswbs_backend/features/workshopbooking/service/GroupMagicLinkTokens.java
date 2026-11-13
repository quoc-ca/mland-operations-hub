package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hashing and URL form of the group Magic Link (003.1 FR-042, FR-048). Only the hash is ever stored. */
public final class GroupMagicLinkTokens {

    /** The React route that reads the token from the URL fragment, so it never reaches a server log. */
    public static final String ACCESS_PATH = "/workshop-booking/access#";

    private GroupMagicLinkTokens() {
    }

    public static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static String accessLink(String rawToken) {
        return ACCESS_PATH + rawToken;
    }

    /** The raw token inside an access link, or null when the link is not one of ours. */
    public static String tokenOf(String accessLink) {
        if (accessLink == null) return null;
        int at = accessLink.indexOf('#');
        if (at < 0 || at == accessLink.length() - 1) return null;
        return accessLink.substring(at + 1);
    }
}
