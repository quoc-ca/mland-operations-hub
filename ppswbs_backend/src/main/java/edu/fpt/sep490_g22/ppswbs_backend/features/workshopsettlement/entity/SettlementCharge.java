package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="settlement_charges") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementCharge {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="charge_reference",nullable=false,unique=true,length=128) private String chargeReference;
 @Column(name="bill_reference",nullable=false,length=128) private String billReference;
 @Column(name="source_category",nullable=false,length=32) private String sourceCategory;
 @Column(name="description",length=200) private String description;
 @Column(name="amount_vnd",nullable=false) private Long amountVnd;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(name="lifecycle_status",nullable=false,length=24) private String lifecycleStatus;
 @Column(name="reason_reference",length=255) private String reasonReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
}
