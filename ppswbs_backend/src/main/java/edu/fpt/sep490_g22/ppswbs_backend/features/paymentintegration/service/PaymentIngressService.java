package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

@Service @RequiredArgsConstructor
public class PaymentIngressService {
    private final VnpayChecksumService checksum;
    private final PaymentOwnerRegistry registry;
    private final PaymentIngressReceiptRepository receipts;
    private final WorkshopBookingFacade booking;
    private final WorkshopSettlementFacade settlement;

    @Transactional
    public Outcome receive(Map<String,String> params) {
        String hash = params.get("vnp_SecureHash");
        if (!checksum.valid(params, hash)) return Outcome.reject("INVALID_SIGNATURE");
        String merchant = params.get("vnp_TxnRef");
        if (merchant == null || merchant.isBlank()) return Outcome.reject("UNKNOWN_REFERENCE");
        var owner = registry.resolve(merchant).orElse(null);
        if (owner == null) return Outcome.reject("UNKNOWN_REFERENCE");
        long amount = parseAmount(params.get("vnp_Amount"));
        if (amount != owner.getAmountVnd() || !"VND".equals(owner.getCurrencyCode())) return Outcome.reject("AMOUNT_CURRENCY_MISMATCH");
        if (!"00".equals(params.get("vnp_ResponseCode"))) return Outcome.accept();
        String delivery = merchant + "|" + params.getOrDefault("vnp_TransactionNo", "");
        if (receipts.existsByDeliveryReference(delivery)) return Outcome.duplicate();
        try {
            receipts.saveAndFlush(PaymentIngressReceipt.builder().deliveryReference(delivery).merchantReference(merchant).createdAt(Instant.now()).build());
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            return Outcome.duplicate();
        }
        String evidence = "VNPAY-" + params.getOrDefault("vnp_TransactionNo", merchant);
        String operation = "VNPAY-" + delivery;
        if (owner.getPaymentKind() == PaymentOwnerKind.DEPOSIT) {
            booking.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(owner.getBookingReference(), evidence, merchant, amount, "VND", operation, operation));
        } else {
            settlement.applyVerifiedSettlementPaymentEvidence(new ApplyVerifiedSettlementPaymentEvidenceCommand(owner.getBookingReference(), evidence, merchant, owner.getPaymentKind().name(), amount, "VND", operation, operation));
        }
        return Outcome.accept();
    }
    private long parseAmount(String raw) { try { long value=Long.parseLong(raw); if(value<=0 || value%100!=0) return -1; return value/100; } catch(Exception e){return -1;} }
    public record Outcome(String rspCode, String message) {
        static Outcome accept(){return new Outcome("00","OK");} static Outcome duplicate(){return new Outcome("00","Already received");} static Outcome reject(String reason){return new Outcome("00", reason);}
    }
}
