package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_evidence_conclusions") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementEvidenceConclusion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="evidence_reference",nullable=false,unique=true,length=128) private String evidenceReference;
 @Column(name="booking_reference",nullable=false,length=128) private String bookingReference;
 @Column(name="provider_transaction_reference",length=255) private String providerTransactionReference;
 @Column(name="conclusion",nullable=false,length=32) private String conclusion;
 @Column(name="amount_vnd") private Long amountVnd;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(name="operation_reference",nullable=false,unique=true,length=128) private String operationReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
}
