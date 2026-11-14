package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;
import java.util.List;

public interface RetailBenefitPort {
    RetailCheckoutPorts.BenefitPreview preview(Long memberAccountId, List<RetailCheckoutPorts.PricedLine> lines, RetailCheckoutPorts.BenefitSelection selection);
    RetailCheckoutPorts.BenefitPreview requireValidPreview(Long memberAccountId, String previewId, RetailCheckoutPorts.BenefitSelection selection, BigDecimal subtotal);

    /**
     * At order acceptance: the preview must still hold for these priced lines now (a voucher may have ended, run
     * out or changed since the preview). Adapters that cannot re-check fall back to the stored preview.
     */
    default RetailCheckoutPorts.BenefitPreview requireValidPreview(Long memberAccountId, String previewId,
                                                                   RetailCheckoutPorts.BenefitSelection selection,
                                                                   List<RetailCheckoutPorts.PricedLine> lines) {
        BigDecimal subtotal = lines.stream().map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return requireValidPreview(memberAccountId, previewId, selection, subtotal);
    }
}
