package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentOwnerRegistrationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class PaymentOwnerRegistryImpl implements PaymentOwnerRegistry {
    private final PaymentOwnerRegistrationRepository repository;
    @Override @Transactional
    public PaymentOwnerRegistration register(String merchantReference, String bookingReference, PaymentOwnerKind kind,
                                             long amountVnd, String currencyCode) {
        if (merchantReference == null || merchantReference.isBlank() || bookingReference == null || bookingReference.isBlank())
            throw new IllegalArgumentException("payment references are required");
        if (kind == null || amountVnd <= 0 || !"VND".equals(currencyCode)) throw new IllegalArgumentException("invalid payment owner registration");
        var existing = repository.findByMerchantReference(merchantReference);
        if (existing.isPresent()) {
            var row = existing.get();
            if (!row.getBookingReference().equals(bookingReference) || row.getPaymentKind() != kind
                    || !row.getAmountVnd().equals(amountVnd) || !row.getCurrencyCode().equals(currencyCode))
                throw new IllegalStateException("MERCHANT_REFERENCE_CONFLICT");
            return row;
        }
        return repository.saveAndFlush(PaymentOwnerRegistration.builder().merchantReference(merchantReference)
                .bookingReference(bookingReference).paymentKind(kind).amountVnd(amountVnd).currencyCode(currencyCode).build());
    }
    @Override @Transactional(readOnly = true)
    public Optional<PaymentOwnerRegistration> resolve(String merchantReference) { return repository.findByMerchantReference(merchantReference); }
}
