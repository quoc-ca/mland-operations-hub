package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import java.util.List;

public interface RetailCheckoutPricingPort {
    List<RetailCheckoutPorts.PricedLine> price(List<CartLine> lines);
}
