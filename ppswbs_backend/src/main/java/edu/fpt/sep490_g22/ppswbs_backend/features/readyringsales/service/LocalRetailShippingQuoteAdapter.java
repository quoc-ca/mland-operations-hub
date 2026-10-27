package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local-only GHTK quote substitute; it creates no carrier request or label. */
@Service
@Profile("local")
public class LocalRetailShippingQuoteAdapter implements RetailShippingQuotePort {
    private final Map<String, StoredQuote> quotes = new ConcurrentHashMap<>();
    @Override public RetailCheckoutPorts.ShippingQuote quote(Long memberAccountId, Long locationId, List<RetailCheckoutPorts.PricedLine> lines) {
        String id = "local-ghtk-" + UUID.randomUUID();
        RetailCheckoutPorts.ShippingQuote quote = new RetailCheckoutPorts.ShippingQuote(
            id, locationId, BigDecimal.valueOf(30_000L + Math.floorMod(locationId, 3) * 5_000L), Instant.now().plus(15, ChronoUnit.MINUTES));
        quotes.put(id, new StoredQuote(memberAccountId, quote));
        return quote;
    }
    @Override public RetailCheckoutPorts.ShippingQuote requireValidQuote(Long memberAccountId, Long locationId, String quoteId) {
        StoredQuote stored = quotes.get(quoteId);
        if (stored == null || !stored.memberAccountId.equals(memberAccountId) || !stored.quote.locationId().equals(locationId)
            || stored.quote.expiresAt().isBefore(Instant.now())) {
            throw new RetailConflictException("Shipping quote is no longer valid for this branch");
        }
        return stored.quote;
    }
    private record StoredQuote(Long memberAccountId, RetailCheckoutPorts.ShippingQuote quote) { }
}
