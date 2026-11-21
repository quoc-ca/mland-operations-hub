package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentEvidenceFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailVerifiedPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.facade.CustomOrderPaymentFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentIngressService {
    private final VnpayChecksumService checksum;
    private final PaymentOwnerRegistry registry;
    private final PaymentIngressReceiptRepository receipts;
    private final WorkshopBookingFacade booking;
    private final WorkshopSettlementFacade settlement;
    private final RetailPaymentEvidenceFacade retail;
    private final Optional<CustomOrderPaymentFacade> customOrderPaymentFacade;

    public PaymentIngressService(
            VnpayChecksumService checksum,
            PaymentOwnerRegistry registry,
            PaymentIngressReceiptRepository receipts,
            WorkshopBookingFacade booking,
            WorkshopSettlementFacade settlement,
            RetailPaymentEvidenceFacade retail) {
        this(checksum, registry, receipts, booking, settlement, retail, Optional.empty());
    }

    @Autowired
    public PaymentIngressService(
            VnpayChecksumService checksum,
            PaymentOwnerRegistry registry,
            PaymentIngressReceiptRepository receipts,
            WorkshopBookingFacade booking,
            WorkshopSettlementFacade settlement,
            RetailPaymentEvidenceFacade retail,
            Optional<CustomOrderPaymentFacade> customOrderPaymentFacade) {
        this.checksum = checksum;
        this.registry = registry;
        this.receipts = receipts;
        this.booking = booking;
        this.settlement = settlement;
        this.retail = retail;
        this.customOrderPaymentFacade = customOrderPaymentFacade;
    }
    /** 003.1 FR-032 "Definite failure" (explicit list, decided 2026-11-19); no other code and no rule of "not 00". */
    private static final java.util.Set<String> DEFINITE_FAILURE_CODES =
            java.util.Set.of("09", "10", "11", "12", "13", "24", "51", "65", "79");

    @Transactional
    public Outcome receive(Map<String,String> params) {
        String hash = params.get("vnp_SecureHash");
        if (!checksum.valid(params, hash)) return Outcome.reject("INVALID_SIGNATURE");
        String merchant = params.get("vnp_TxnRef");
        if (merchant == null || merchant.isBlank()) return Outcome.reject("UNKNOWN_REFERENCE");
        // Lock before any ordinary read so MySQL's repeatable-read snapshot cannot
        // hide a receipt committed by a concurrent callback for the same owner.
        var owner = registry.lockOwner(merchant).orElse(null);
        if (owner == null) return Outcome.reject("UNKNOWN_REFERENCE");
        long amount = parseAmount(params.get("vnp_Amount"));
        // VNPay PAY/IPN callbacks do not include vnp_CurrCode; VND is the
        // provider contract for this merchant. An explicitly supplied
        // non-VND value remains a hard mismatch.
        String providerCurrency = params.getOrDefault("vnp_CurrCode", "VND");
        if (amount != owner.getAmountVnd() || !owner.getCurrencyCode().equals(providerCurrency)) {
            recordMismatchedGroupEvidence(owner, merchant, amount, providerCurrency, params);
            return Outcome.reject("AMOUNT_CURRENCY_MISMATCH");
        }
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (!isProviderCode(responseCode) || !isProviderCode(transactionStatus)) {
            return Outcome.reject("UNVERIFIED_OUTCOME");
        }
        boolean terminal = !"01".equals(transactionStatus);
        boolean verifiedSuccess = "00".equals(responseCode) && "00".equals(transactionStatus);
        boolean definiteFailure = DEFINITE_FAILURE_CODES.contains(responseCode) || "02".equals(transactionStatus);
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", "");
        if (owner.getPaymentKind() == PaymentOwnerKind.RETAIL && verifiedSuccess && providerTransaction.isBlank()) {
            throw new IllegalStateException("RETAIL_PROVIDER_TRANSACTION_MISSING");
        }
        String delivery = merchant + "|" + providerTransaction + "|" + responseCode + "|" + transactionStatus;
        if (receipts.existsByDeliveryReference(delivery)) {
            // A prior delivery may have committed the shared receipt before a
            // downstream consumer completed its own idempotent projection. Re-
            // dispatching the redacted evidence is safe: Booking/Settlement
            // facades own the business idempotency boundary and this path does
            // not create another payment receipt or provider settlement.
            if (verifiedSuccess) dispatchVerifiedSuccess(owner, merchant, amount, params);
            if (definiteFailure) dispatchDefiniteFailure(owner, merchant, responseCode, transactionStatus, delivery);
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
        if (definiteFailure) dispatchDefiniteFailure(owner, merchant, responseCode, transactionStatus, delivery);
        return Outcome.accept();
    }

    /**
     * FR-010f (F14): signed 00/00 evidence of a booking-group payment whose amount or currency differs is recorded
     * (this shared receipt, one audit entry and one WARN in 003.1) and never confirms the group. The IPN answer stays
     * 04. A replayed delivery finds its receipt and records nothing more. Other owners and non-success outcomes are
     * left as before.
     */
    private void recordMismatchedGroupEvidence(PaymentOwnerRegistration owner, String merchant, long amount,
                                               String currency, Map<String, String> params) {
        if (owner.getPaymentKind() != PaymentOwnerKind.DEPOSIT
                || !"00".equals(params.get("vnp_ResponseCode")) || !"00".equals(params.get("vnp_TransactionStatus"))) {
            return;
        }
        String transaction = params.getOrDefault("vnp_TransactionNo", "");
        String delivery = merchant + "|" + transaction + "|00|00";
        if (receipts.existsByDeliveryReference(delivery)) return;
        String reason = amount != owner.getAmountVnd() ? "AMOUNT_MISMATCH" : "CURRENCY_MISMATCH";
        boolean recorded = booking.recordMismatchedGroupPaymentEvidence(new RecordMismatchedGroupPaymentEvidenceCommand(
                owner.getBookingReference(), merchant, delivery, amount, currency, reason, "VNPAY-" + merchant));
        if (!recorded) return;
        receipts.saveAndFlush(PaymentIngressReceipt.builder().deliveryReference(delivery).merchantReference(merchant)
                .responseCode("00").transactionStatus("00").terminalOutcome(true).amountVnd(amount)
                .currencyCode(currency).providerTransactionNumber(transaction.isBlank() ? null : transaction)
                .createdAt(Instant.now()).build());
    }

    /**
     * FR-032: a verified IPN (signature, owner, exact amount and currency already checked) with a definite-failure code is
     * reported to Booking, which sets the matching group attempt FAILED. Only a booking-group (DEPOSIT) owner is told;
     * the return URL never reaches this method. Booking answers a repeated delivery idempotently.
     */
    private void dispatchDefiniteFailure(PaymentOwnerRegistration owner, String merchant, String responseCode,
                                         String transactionStatus, String delivery) {
        if (owner.getPaymentKind() != PaymentOwnerKind.DEPOSIT) return;
        booking.recordDefinitePaymentFailure(new RecordDefinitePaymentFailureCommand(merchant, responseCode,
                transactionStatus, delivery));
    }

    private void dispatchVerifiedSuccess(PaymentOwnerRegistration owner, String merchant, long amount,
                                         Map<String, String> params) {
        String providerTransaction = params.getOrDefault("vnp_TransactionNo", merchant);
        String evidence = "VNPAY-" + providerTransaction;
        String operation = evidence;
        String correlation = evidence;
        if (owner.getPaymentKind() == PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT) {
            customOrderPaymentFacade.ifPresent(facade -> facade.applyVerifiedFullPayment(
                    owner.getBookingReference(), merchant, providerTransaction, amount, "VND"));
        } else if (owner.getPaymentKind() == PaymentOwnerKind.RETAIL) {
            retail.applyVerifiedPayment(new RetailVerifiedPayment(owner.getBookingReference(), merchant,
                    providerTransaction, amount, "VND"));
        } else if (owner.getPaymentKind() == PaymentOwnerKind.DEPOSIT) {
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
            if ((owner.getPaymentKind() == PaymentOwnerKind.FINAL_PAYMENT || owner.getPaymentKind() == PaymentOwnerKind.SHOP_ADDON)
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
        static Outcome accept(){return new Outcome("00","OK");} static Outcome duplicate(){return new Outcome("02","Already received");} // VNPay IPN: 02 = order already confirmed
        /**
         * VNPay's documented IPN response codes: 97 invalid checksum, 01 order (merchant reference) not found,
         * 04 invalid amount, 99 any other unverifiable callback. The message keeps the stable internal reason.
         */
        static Outcome reject(String reason) {
            String code = switch (reason) {
                case "INVALID_SIGNATURE" -> "97";
                case "UNKNOWN_REFERENCE" -> "01";
                case "AMOUNT_CURRENCY_MISMATCH" -> "04";
                default -> "99";
            };
            return new Outcome(code, reason);
        }
    }
}
