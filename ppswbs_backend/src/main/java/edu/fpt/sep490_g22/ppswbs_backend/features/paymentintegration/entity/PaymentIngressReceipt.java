package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "payment_ingress_receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentIngressReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="delivery_reference", nullable=false, unique=true, length=256) private String deliveryReference;
    @Column(name="merchant_reference", nullable=false, length=128) private String merchantReference;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=Instant.now(); }
}
