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
        return register(merchantReference, bookingReference, kind, amountVnd, currencyCode, null);
    }

    @Override @Transactional
    public PaymentOwnerRegistration register(String merchantReference, String bookingReference, PaymentOwnerKind kind,
                                             long amountVnd, String currencyCode, String providerCreateDate) {
        if (merchantReference == null || merchantReference.isBlank() || bookingReference == null || bookingReference.isBlank())
            throw new IllegalArgumentException("payment references are required");
        if (kind == null || amountVnd <= 0 || !"VND".equals(currencyCode)) throw new IllegalArgumentException("invalid payment owner registration");
        if (providerCreateDate != null && !providerCreateDate.matches("[0-9]{14}"))
            throw new IllegalArgumentException("INVALID_PROVIDER_CREATE_DATE");
        var existing = repository.findByMerchantReference(merchantReference);
        if (existing.isPresent()) {
            var row = existing.get();
            if (!row.getBookingReference().equals(bookingReference) || row.getPaymentKind() != kind
                    || !row.getAmountVnd().equals(amountVnd) || !row.getCurrencyCode().equals(currencyCode))
                throw new IllegalStateException("MERCHANT_REFERENCE_CONFLICT");
            if (providerCreateDate != null && row.getProviderCreateDate() != null
                    && !row.getProviderCreateDate().equals(providerCreateDate))
                throw new IllegalStateException("MERCHANT_REFERENCE_CONFLICT");
            if (providerCreateDate != null && row.getProviderCreateDate() == null)
                throw new IllegalStateException("ORIGINAL_PAYMENT_DATE_MISSING");
            return row;
        }
        return repository.saveAndFlush(PaymentOwnerRegistration.builder().merchantReference(merchantReference)
                .bookingReference(bookingReference).paymentKind(kind).amountVnd(amountVnd).currencyCode(currencyCode)
                .providerCreateDate(providerCreateDate).build());
    }
    @Override @Transactional(readOnly = true)
    public Optional<PaymentOwnerRegistration> resolve(String merchantReference) { return repository.findByMerchantReference(merchantReference); }
}
