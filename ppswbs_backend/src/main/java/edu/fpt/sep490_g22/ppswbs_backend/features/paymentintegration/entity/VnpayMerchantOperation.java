package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "vnpay_merchant_operations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VnpayMerchantOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_reference", nullable = false, unique = true, length = 128)
    private String operationReference;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "operation_kind", nullable = false, length = 16)
    private String operationKind;

    @Column(name = "provider_request_id", nullable = false, unique = true, length = 32)
    private String providerRequestId;

    @Column(name = "merchant_reference", nullable = false, length = 128)
    private String merchantReference;

    @Column(name = "operation_status", nullable = false, length = 16)
    private String operationStatus;

    @Column(name = "provider_response_code", length = 2)
    private String providerResponseCode;

    @Column(name = "provider_transaction_type", length = 2)
    private String providerTransactionType;

    @Column(name = "provider_transaction_status", length = 2)
    private String providerTransactionStatus;

    @Column(name = "provider_transaction_number", length = 15)
    private String providerTransactionNumber;

    @Column(name = "amount_vnd")
    private Long amountVnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
