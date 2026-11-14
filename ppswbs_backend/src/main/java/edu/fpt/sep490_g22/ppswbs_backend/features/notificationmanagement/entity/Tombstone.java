package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_tombstone",
    indexes = @Index(name = "idx_nt_scrubbed", columnList = "scrubbed_at")
)
@Getter
@Setter
@NoArgsConstructor
public class Tombstone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_request_id", nullable = false, unique = true)
    private Long originalRequestId;

    @Column(name = "business_event_reference", nullable = false, length = 128)
    private String businessEventReference;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "terminal_state", nullable = false, length = 32)
    private String terminalState;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "request_created_at", nullable = false)
    private Instant requestCreatedAt;

    @Column(name = "request_terminal_at", nullable = false)
    private Instant requestTerminalAt;

    @Column(name = "scrubbed_at", nullable = false, updatable = false)
    private Instant scrubbedAt;

    @PrePersist
    protected void onCreate() {
        if (scrubbedAt == null) {
            scrubbedAt = Instant.now();
        }
    }
}

