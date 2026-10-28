package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPricingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Local-only FE-02 price and stock substitute. It is deliberately deterministic, not catalogue data. */
@Service
@Profile("local")
public class LocalRetailCheckoutPricingAdapter implements RetailCheckoutPricingPort {
    @Override
    public List<RetailCheckoutPorts.PricedLine> price(List<CartLine> lines) {
        return lines.stream().map(line -> new RetailCheckoutPorts.PricedLine(
            line.getVariantId(), line.getLocationId(), line.getQuantity(),
            BigDecimal.valueOf(500_000L + Math.floorMod(line.getVariantId().hashCode(), 500_000))
        )).toList();
    }
}
