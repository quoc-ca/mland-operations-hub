package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque keyset cursor of the Settlement audit review list (003.2 FR-050): only the id of the last row of the
 * previous page. Deliberately its own small class so 003.3 shares nothing with Operations (no module cycle, B5).
 */
@Component
public class SettlementAuditCursorCodec {

    private static final String PREFIX = "s1:";
    static final int MAX_LENGTH = 64;

    public String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + lastId).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the id of the last row already seen, or {@code null} for the first page (no cursor) */
    public Long decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.length() > MAX_LENGTH) {
            throw invalid();
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (!text.startsWith(PREFIX)) {
                throw invalid();
            }
            long id = Long.parseLong(text.substring(PREFIX.length()));
            if (id <= 0) {
                throw invalid();
            }
            return id;
        } catch (IllegalArgumentException ex) { // bad base64 or bad number
            throw invalid();
        }
    }

    private static SettlementAuditQueryException invalid() {
        return new SettlementAuditQueryException("cursor", "cursor is not valid");
    }
}
