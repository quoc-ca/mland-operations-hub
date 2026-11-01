package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VerifiedTerminalPaymentOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentIngressOutcomeQueryAdapter implements PaymentIngressOutcomeQueryPort {
    private final PaymentIngressReceiptRepository receipts;

    @Override
    public boolean hasVerifiedTerminalOutcome(String merchantReference) {
        return receipts.existsByMerchantReferenceAndTerminalOutcomeTrue(merchantReference);
    }

    @Override
    public java.util.Optional<VerifiedTerminalPaymentOutcome> findVerifiedTerminalOutcome(String merchantReference) {
        return receipts.findFirstByMerchantReferenceAndTerminalOutcomeTrueOrderByIdDesc(merchantReference)
                .map(row -> new VerifiedTerminalPaymentOutcome(row.getMerchantReference(), row.getAmountVnd(),
                        row.getCurrencyCode(), row.getResponseCode(), row.getTransactionStatus(),
                        "IPN-" + row.getId()));
    }
}
