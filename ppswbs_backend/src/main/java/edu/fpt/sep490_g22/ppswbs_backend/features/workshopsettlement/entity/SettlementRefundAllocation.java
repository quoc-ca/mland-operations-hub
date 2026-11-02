package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_refund_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRefundAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_reference", nullable = false, unique = true, length = 128)
    private String allocationReference;

    @Column(name = "case_reference", nullable = false, length = 128)
    private String caseReference;

    @Column(name = "original_payment_reference", nullable = false, length = 128)
    private String originalPaymentReference;

    @Column(name = "original_provider_transaction_number", nullable = false, length = 15)
    private String originalProviderTransactionNumber;

    @Column(name = "original_provider_create_date", nullable = false, length = 14)
    private String originalProviderCreateDate;

    @Column(name = "original_amount_vnd", nullable = false)
    private Long originalAmountVnd;

    @Column(name = "allocated_amount_vnd", nullable = false)
    private Long allocatedAmountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "operation_reference", nullable = false, unique = true, length = 128)
    private String operationReference;

    @Column(name = "provider_refund_transaction_number", length = 15)
    private String providerRefundTransactionNumber;

    @Column(name = "lifecycle_status", nullable = false, length = 24)
    private String lifecycleStatus;

    @Column(name = "provider_response_code", length = 8)
    private String providerResponseCode;

    @Column(name = "provider_transaction_type", length = 4)
    private String providerTransactionType;

    @Column(name = "provider_transaction_status", length = 4)
    private String providerTransactionStatus;

    @Column(name = "provider_accepted_at")
    private Instant providerAcceptedAt;

    @Column(name = "last_query_at")
    private Instant lastQueryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
