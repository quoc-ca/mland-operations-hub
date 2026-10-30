package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_business_audits") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementBusinessAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="audit_reference",nullable=false,unique=true,length=128) private String auditReference;
 @Column(name="aggregate_type",nullable=false,length=64) private String aggregateType;
 @Column(name="aggregate_reference",nullable=false,length=128) private String aggregateReference;
 @Column(name="action_code",nullable=false,length=64) private String actionCode;
 @Column(name="actor_reference",length=128) private String actorReference;
 @Column(name="correlation_reference",nullable=false,length=128) private String correlationReference;
 @Column(name="evidence_reference",length=128) private String evidenceReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
}
