package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;
import java.util.List;

public interface RetailBenefitPort {
    RetailCheckoutPorts.BenefitPreview preview(Long memberAccountId, List<RetailCheckoutPorts.PricedLine> lines, RetailCheckoutPorts.BenefitSelection selection);
    RetailCheckoutPorts.BenefitPreview requireValidPreview(Long memberAccountId, String previewId, RetailCheckoutPorts.BenefitSelection selection, BigDecimal subtotal);
}
