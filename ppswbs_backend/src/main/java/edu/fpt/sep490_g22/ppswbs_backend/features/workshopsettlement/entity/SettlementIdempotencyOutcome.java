package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_idempotency_outcomes") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementIdempotencyOutcome {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="operation_reference",nullable=false,unique=true,length=128) private String operationReference;
 @Column(name="request_fingerprint",nullable=false,length=64) private String requestFingerprint;
 @Column(name="outcome_status",nullable=false,length=32) private String outcomeStatus;
 @Column(name="outcome_reference",length=128) private String outcomeReference;
 @Column(name="correlation_reference",nullable=false,length=128) private String correlationReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="completed_at") private Instant completedAt;
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
}
