package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ota_inbound_events", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_inbound_connection_dedupe", columnNames = {"connection_id", "dedupe_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaInboundEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private OtaChannelConnection connection;

    @Column(name = "external_event_reference", length = 255)
    private String externalEventReference;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Column(name = "dedupe_key", nullable = false, length = 64)
    private String dedupeKey;

    @Column(name = "event_kind", nullable = false, length = 32)
    private String eventKind;

    @Column(name = "authentication_result", nullable = false, length = 24)
    private String authenticationResult;

    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;

    @Column(name = "outcome_reference", length = 128)
    private String outcomeReference;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
