package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "payment_owner_registrations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOwnerRegistration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="merchant_reference", nullable=false, unique=true, length=128) private String merchantReference;
    @Column(name="booking_reference", nullable=false, length=128) private String bookingReference;
    @Enumerated(EnumType.STRING) @Column(name="payment_kind", nullable=false, length=24) private PaymentOwnerKind paymentKind;
    @Column(name="amount_vnd", nullable=false) private Long amountVnd;
    @Column(name="currency_code", nullable=false, length=3) private String currencyCode;
    @Column(name="provider_create_date", length=14) private String providerCreateDate;
    @Column(name="lifecycle_status", nullable=false, length=24) @Builder.Default private String lifecycleStatus = "OPEN";
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=Instant.now(); }
}
