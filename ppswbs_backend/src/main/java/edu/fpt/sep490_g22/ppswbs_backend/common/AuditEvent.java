package edu.fpt.sep490_g22.ppswbs_backend.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private Long memberId;
    private String eventType;
    private Instant occurredAt;
    private String correlationId;
    private String provider;
    private String safeMetadata;
}
