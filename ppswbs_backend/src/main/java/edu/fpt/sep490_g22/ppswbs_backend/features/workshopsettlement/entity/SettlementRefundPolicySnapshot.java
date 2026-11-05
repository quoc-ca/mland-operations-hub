package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_refund_policy_snapshots") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementRefundPolicySnapshot {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="snapshot_reference",nullable=false,unique=true,length=128) private String snapshotReference;
 @Column(name="booking_reference",nullable=false,length=128) private String bookingReference;
 @Column(name="policy_revision",nullable=false,length=64) private String policyRevision;
 @Column(name="collected_amount_vnd",nullable=false) private Long collectedAmountVnd;
 @Column(name="refundable_amount_vnd",nullable=false) private Long refundableAmountVnd;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(name="captured_at",nullable=false,updatable=false) private Instant capturedAt;
 @PrePersist void onCreate(){if(capturedAt==null)capturedAt=Instant.now();}
}
