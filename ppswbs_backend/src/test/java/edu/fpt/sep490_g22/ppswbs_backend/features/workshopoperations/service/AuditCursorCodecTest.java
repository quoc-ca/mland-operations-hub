package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T170 (b), FR-050: opaque id cursor; a malformed token is the existing validation error. */
class AuditCursorCodecTest {

    private final AuditCursorCodec codec = new AuditCursorCodec();

    @Test
    void roundTripsAnId() {
        assertThat(codec.decode(codec.encode(1L))).isEqualTo(1L);
        assertThat(codec.decode(codec.encode(9_876_543_210L))).isEqualTo(9_876_543_210L);
    }

    @Test
    void tokenIsOpaqueAndWithinTheDocumentedLength() {
        String token = codec.encode(Long.MAX_VALUE);

        assertThat(token).doesNotContain("123").hasSizeLessThanOrEqualTo(AuditCursorCodec.MAX_LENGTH);
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void absentCursorMeansFirstPage() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("   ")).isNull();
    }

    @Test
    void malformedTokensAreValidationErrors() {
        for (String bad : new String[] {"!!!", "not-base64-$$", encodeRaw("12"), encodeRaw("a1:abc"),
                encodeRaw("a1:"), encodeRaw("a1:0"), encodeRaw("a1:-5"), encodeRaw("b9:7"),
                "x".repeat(AuditCursorCodec.MAX_LENGTH + 1)}) {
            assertThatThrownBy(() -> codec.decode(bad))
                    .as("token %s", bad)
                    .isInstanceOf(WorkshopValidationException.class);
        }
    }

    private static String encodeRaw(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
