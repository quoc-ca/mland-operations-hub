package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.util.List;

public interface RetailShippingQuotePort {
    RetailCheckoutPorts.ShippingQuote quote(Long memberAccountId, Long locationId, List<RetailCheckoutPorts.PricedLine> lines,
                                             RetailCheckoutPorts.RecipientAddress recipient);
    RetailCheckoutPorts.ShippingQuote requireValidQuote(Long memberAccountId, Long locationId, String quoteId);
}
