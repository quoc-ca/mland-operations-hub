package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailBenefitPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPricingPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

final class UnavailableCheckoutBoundary {
    private UnavailableCheckoutBoundary() { }
    static RetailIntegrationUnavailableException unavailable() { return new RetailIntegrationUnavailableException("Checkout integrations are not configured"); }
}

@Service @Profile("!local")
class UnavailableRetailCheckoutPricingAdapter implements RetailCheckoutPricingPort {
    public List<RetailCheckoutPorts.PricedLine> price(List<CartLine> lines) { throw UnavailableCheckoutBoundary.unavailable(); }
}
@Service @Profile("!local")
class UnavailableRetailBenefitAdapter implements RetailBenefitPort {
    public RetailCheckoutPorts.BenefitPreview preview(Long m, List<RetailCheckoutPorts.PricedLine> l, RetailCheckoutPorts.BenefitSelection s) { throw UnavailableCheckoutBoundary.unavailable(); }
    public RetailCheckoutPorts.BenefitPreview requireValidPreview(Long m, String id, RetailCheckoutPorts.BenefitSelection s, BigDecimal subtotal) { throw UnavailableCheckoutBoundary.unavailable(); }
}
@Service @Profile("!local")
class UnavailableRetailShippingQuoteAdapter implements RetailShippingQuotePort {
    public RetailCheckoutPorts.ShippingQuote quote(Long m, Long l, List<RetailCheckoutPorts.PricedLine> lines, RetailCheckoutPorts.RecipientAddress recipient) { throw UnavailableCheckoutBoundary.unavailable(); }
    public RetailCheckoutPorts.ShippingQuote requireValidQuote(Long m, Long l, String id) { throw UnavailableCheckoutBoundary.unavailable(); }
}
@Service @Profile("!local")
class UnavailableRetailPaymentAdapter implements RetailPaymentPort {
    public RetailCheckoutPorts.PaymentInitiation initiate(String id, BigDecimal amount) { throw UnavailableCheckoutBoundary.unavailable(); }
}
