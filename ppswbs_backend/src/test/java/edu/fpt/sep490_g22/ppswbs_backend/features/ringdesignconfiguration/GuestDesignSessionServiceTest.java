package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.GuestDesignSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestDesignSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestSessionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.GuestDesignSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestDesignSessionServiceTest {

    @Mock
    private GuestDesignSessionRepository sessionRepository;

    @InjectMocks
    private GuestDesignSessionService sessionService;

    @BeforeEach
    void setUp() {
        lenient().when(sessionRepository.save(any(GuestDesignSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("issueGuestSession should generate opaque token, SHA-256 digest, and 20-min expiry")
    void issueGuestSession_Success() {
        GuestDesignSession session = sessionService.issueGuestSession("corr-123");

        assertThat(session.getSessionReference()).startsWith("GDS-");
        assertThat(session.getSessionDigest()).hasSize(64); // SHA-256 hex length
        assertThat(session.getStatus()).isEqualTo(GuestSessionStatus.ACTIVE);
        assertThat(session.getExpiresAt()).isAfter(session.getIssuedAt());
        assertThat(session.getCorrelationId()).isEqualTo("corr-123");
        verify(sessionRepository, times(1)).save(any(GuestDesignSession.class));
    }

    @Test
    @DisplayName("resolveAndTouchSession should update lastActivityAt and extend expiry for active session")
    void resolveAndTouchSession_Active_Success() {
        String token = "GDS-test-valid-token";
        Instant now = Instant.now();
        GuestDesignSession session = GuestDesignSession.builder()
                .sessionReference(token)
                .sessionDigest("digest-123")
                .status(GuestSessionStatus.ACTIVE)
                .issuedAt(now.minusSeconds(300))
                .lastActivityAt(now.minusSeconds(300))
                .expiresAt(now.plusSeconds(900))
                .build();

        when(sessionRepository.findBySessionReference(token)).thenReturn(Optional.of(session));

        GuestDesignSession touched = sessionService.resolveAndTouchSession(token);

        assertThat(touched.getLastActivityAt()).isAfter(now.minusSeconds(300));
        assertThat(touched.getExpiresAt()).isAfter(now);
        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    @DisplayName("resolveAndTouchSession should throw 401 when session has been inactive > 20 minutes")
    void resolveAndTouchSession_Expired_Throws401() {
        String token = "GDS-test-expired-token";
        Instant now = Instant.now();
        GuestDesignSession session = GuestDesignSession.builder()
                .sessionReference(token)
                .sessionDigest("digest-123")
                .status(GuestSessionStatus.ACTIVE)
                .issuedAt(now.minusSeconds(2000))
                .lastActivityAt(now.minusSeconds(1500))
                .expiresAt(now.minusSeconds(300)) // expired 5 minutes ago
                .build();

        when(sessionRepository.findBySessionReference(token)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.resolveAndTouchSession(token))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rde.getCode()).isEqualTo("DESIGN_SESSION_EXPIRED");
                });

        assertThat(session.getStatus()).isEqualTo(GuestSessionStatus.EXPIRED);
        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    @DisplayName("resolveAndTouchSession should throw 401 when token is missing or unknown")
    void resolveAndTouchSession_Unknown_Throws401() {
        when(sessionRepository.findBySessionReference("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.resolveAndTouchSession("unknown-token"))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rde.getCode()).isEqualTo("DESIGN_SESSION_EXPIRED");
                });
    }

    @Test
    @DisplayName("bindDesign should associate design reference with active guest session")
    void bindDesign_Success() {
        String token = "GDS-token-bind";
        GuestDesignSession session = GuestDesignSession.builder()
                .sessionReference(token)
                .sessionDigest("digest-bind")
                .status(GuestSessionStatus.ACTIVE)
                .issuedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1200))
                .build();

        when(sessionRepository.findBySessionReference(token)).thenReturn(Optional.of(session));

        sessionService.bindDesign(token, "DSG-bound-123");

        assertThat(session.getDesignReference()).isEqualTo("DSG-bound-123");
        verify(sessionRepository, times(2)).save(session);
    }
}

