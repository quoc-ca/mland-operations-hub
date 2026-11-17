package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_cases") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementCase {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="case_reference",nullable=false,unique=true,length=128) private String caseReference;
 @Column(name="booking_reference",nullable=false,length=128) private String bookingReference;
 @Column(name="case_type",nullable=false,length=32) private String caseType;
 @Column(name="lifecycle_status",nullable=false,length=32) private String lifecycleStatus;
 @Column(name="reason_reference",length=255) private String reasonReference;
 @Column(name="correlation_reference",nullable=false,length=128) private String correlationReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void onCreate(){requireKnownLifecycleStatus();Instant n=Instant.now();createdAt=n;updatedAt=n;}
 @PreUpdate void onUpdate(){requireKnownLifecycleStatus();updatedAt=Instant.now();}
 /** FR-022b1: a case may only hold a {@link SettlementCaseLifecycleStatus} value. */
 void requireKnownLifecycleStatus(){
  if(!SettlementCaseLifecycleStatus.isKnown(lifecycleStatus))
   throw new IllegalStateException("SETTLEMENT_CASE_STATUS_UNSUPPORTED");
 }
}
