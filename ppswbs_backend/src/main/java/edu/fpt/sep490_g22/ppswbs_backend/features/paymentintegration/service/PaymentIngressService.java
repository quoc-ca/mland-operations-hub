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
        // VNPay PAY/IPN callbacks do not include vnp_CurrCode; VND is the
        // provider contract for this merchant. An explicitly supplied
        // non-VND value remains a hard mismatch.
        String providerCurrency = params.getOrDefault("vnp_CurrCode", "VND");
        if (amount != owner.getAmountVnd() || !owner.getCurrencyCode().equals(providerCurrency)) return Outcome.reject("AMOUNT_CURRENCY_MISMATCH");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (!isProviderCode(responseCode) || !isProviderCode(transactionStatus)) {
            return Outcome.reject("UNVERIFIED_OUTCOME");
        }
        boolean terminal = !"01".equals(transactionStatus);
        boolean verifiedSuccess = "00".equals(responseCode) && "00".equals(transactionStatus);
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", "");
        String delivery = merchant + "|" + providerTransaction + "|" + responseCode + "|" + transactionStatus;
        if (receipts.existsByDeliveryReference(delivery)) {
            // A prior delivery may have committed the shared receipt before a
            // downstream consumer completed its own idempotent projection. Re-
            // dispatching the redacted evidence is safe: Booking/Settlement
            // facades own the business idempotency boundary and this path does
            // not create another payment receipt or provider settlement.
            if (verifiedSuccess) dispatchVerifiedSuccess(owner, merchant, amount, params);
            return Outcome.duplicate();
        }
        try {
            receipts.saveAndFlush(PaymentIngressReceipt.builder().deliveryReference(delivery)
                    .merchantReference(merchant).responseCode(responseCode).transactionStatus(transactionStatus)
                    .terminalOutcome(terminal).amountVnd(amount).currencyCode(providerCurrency)
                    .providerTransactionNumber(providerTransaction.isBlank() ? null : providerTransaction)
                    .createdAt(Instant.now()).build());
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            return Outcome.duplicate();
        }
        if (verifiedSuccess) dispatchVerifiedSuccess(owner, merchant, amount, params);
        return Outcome.accept();
    }

    private void dispatchVerifiedSuccess(PaymentOwnerRegistration owner, String merchant, long amount,
                                         Map<String, String> params) {
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", merchant);
        String evidence = "VNPAY-" + providerTransaction;
        String operation = evidence;
        String correlation = evidence;
        if (owner.getPaymentKind() == PaymentOwnerKind.DEPOSIT) {
            var outcome = booking.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(
                    owner.getBookingReference(), evidence, merchant, amount, "VND", operation, correlation));
            if (outcome != null && outcome.status() == BookingOutcomeStatus.REJECTED
                    && (outcome.reason() == BookingRejectionReason.HOLD_MISSING_OR_EXPIRED
                    || outcome.reason() == BookingRejectionReason.INVALID_STATE)) {
                booking.recordLateDepositEvidence(new RecordLateDepositEvidenceCommand(owner.getBookingReference(),
                        merchant, evidence, providerTransaction, amount, "VND"));
            }
        } else {
            dispatch(owner, merchant, amount, params, evidence, operation, correlation);
        }
    }

    private void dispatch(PaymentOwnerRegistration owner, String merchant, long amount, Map<String, String> params) {
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", merchant);
        String evidence = "VNPAY-" + providerTransaction;
        dispatch(owner, merchant, amount, params, evidence, evidence, evidence);
    }

    private void dispatch(PaymentOwnerRegistration owner, String merchant, long amount, Map<String, String> params,
                          String evidence, String operation, String correlation) {
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", merchant);
        if (owner.getPaymentKind() == PaymentOwnerKind.DEPOSIT) {
            booking.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(owner.getBookingReference(), evidence, merchant, amount, "VND", operation, correlation));
        } else {
            if (owner.getPaymentKind() == PaymentOwnerKind.FINAL_PAYMENT
                    && (owner.getProviderCreateDate() == null || !owner.getProviderCreateDate().matches("[0-9]{14}"))) {
                throw new IllegalStateException("FINAL_PAYMENT_ORIGINAL_CREATE_DATE_MISSING");
            }
            SettlementPaymentEvidenceOutcome outcome = settlement.applyVerifiedSettlementPaymentEvidence(
                    new ApplyVerifiedSettlementPaymentEvidenceCommand(owner.getBookingReference(), evidence, merchant,
                            owner.getPaymentKind().name(), amount, "VND", providerTransaction,
                            owner.getProviderCreateDate(), operation, correlation));
            if (outcome == null || outcome.status() != SettlementPaymentEvidenceOutcome.Status.ACCEPTED) {
                throw new IllegalStateException("Verified Settlement evidence was rejected; ingress receipt must not commit");
            }
        }
    }
    private long parseAmount(String raw) { try { long value=Long.parseLong(raw); if(value<=0 || value%100!=0) return -1; return value/100; } catch(Exception e){return -1;} }
    private boolean isProviderCode(String value) { return value != null && value.matches("[0-9]{2}"); }
    public record Outcome(String rspCode, String message) {
        static Outcome accept(){return new Outcome("00","OK");} static Outcome duplicate(){return new Outcome("00","Already received");} static Outcome reject(String reason){return new Outcome("00", reason);}
    }
}
