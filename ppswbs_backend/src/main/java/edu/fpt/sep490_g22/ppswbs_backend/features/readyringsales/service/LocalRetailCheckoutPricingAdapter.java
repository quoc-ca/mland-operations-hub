package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPricingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/** Local-only FE-02 price substitute: the catalogue's applied price, or the demo price for an unknown size. */
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalRetailCheckoutPricingAdapter implements RetailCheckoutPricingPort {
    private final LocalCatalogueFacts catalogue;

    @Override
    public List<RetailCheckoutPorts.PricedLine> price(List<CartLine> lines) {
        return lines.stream().map(line -> {
            BigInteger unitPrice = catalogue.read(line.getVariantId(), line.getLocationId())
                .map(fact -> fact.appliedPrice())
                .filter(price -> price != null)
                .orElseGet(() -> LocalCatalogueFacts.demoPrice(line.getVariantId()));
            return new RetailCheckoutPorts.PricedLine(line.getVariantId(), line.getLocationId(), line.getQuantity(),
                new BigDecimal(unitPrice));
        }).toList();
    }
}
