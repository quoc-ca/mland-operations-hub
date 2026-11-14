package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "retail_payment_reconciliation")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetailPaymentReconciliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "checkout_id", nullable = false, length = 36) private String checkoutId;
    @Column(name = "merchant_reference", nullable = false, unique = true, length = 128) private String merchantReference;
    @Column(name = "provider_transaction", unique = true, length = 64) private String providerTransaction;
    @Column(name = "amount_vnd", nullable = false) private Long amountVnd;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(nullable = false, length = 64) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public void recordLatePayment(String transaction) {
        if (!"QUERY_UNRESOLVED".equals(reason) || providerTransaction != null) throw new IllegalStateException("RECONCILIATION_CONFLICT");
        providerTransaction = transaction;
        reason = "LATE_OR_INVALID_STATE";
    }
}
