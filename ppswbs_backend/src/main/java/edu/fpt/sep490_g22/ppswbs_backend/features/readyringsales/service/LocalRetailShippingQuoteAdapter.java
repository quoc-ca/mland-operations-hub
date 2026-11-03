package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local quote adapter: deterministic fallback by default, real fee GET only when explicitly enabled. */
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalRetailShippingQuoteAdapter implements RetailShippingQuotePort {
    private static final int GRAMS_PER_RING = 50;
    private final Map<String, StoredQuote> quotes = new ConcurrentHashMap<>();
    private final GhtkQuoteProperties properties;
    private final GhtkFeeTransport ghtk;

    @Override public RetailCheckoutPorts.ShippingQuote quote(Long memberAccountId, Long locationId,
                                                              List<RetailCheckoutPorts.PricedLine> lines,
                                                              RetailCheckoutPorts.RecipientAddress recipient) {
        if (properties.isEnabled()) return quoteLiveFee(memberAccountId, locationId, lines, recipient);
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

    private RetailCheckoutPorts.ShippingQuote quoteLiveFee(Long memberAccountId, Long locationId,
                                                            List<RetailCheckoutPorts.PricedLine> lines,
                                                            RetailCheckoutPorts.RecipientAddress recipient) {
        if (!properties.hasRequiredConfiguration() || recipient == null || !hasRecipient(recipient)) {
            throw new RetailIntegrationUnavailableException("GHTK fee quote is not configured for this local environment");
        }
        int ringCount = lines.stream().mapToInt(RetailCheckoutPorts.PricedLine::quantity).sum();
        if (ringCount <= 0) throw new RetailConflictException("Shipping quote requires at least one ring");
        int weight = Math.multiplyExact(ringCount, GRAMS_PER_RING);
        BigDecimal declaredValue = lines.stream()
            .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        GhtkFeeTransport.FeeResponse response = ghtk.quote(new GhtkFeeTransport.FeeRequest(
            properties.getPickupAddress(), properties.getPickupProvince(), properties.getPickupDistrict(), properties.getPickupWard(),
            recipient.address(), recipient.province(), recipient.district(), recipient.ward(), weight, declaredValue));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        if (!response.success()) {
            throw new RetailIntegrationUnavailableException("GHTK shipping quote is unavailable");
        }
        if (!response.deliveryAvailable()) {
            return new RetailCheckoutPorts.ShippingQuote(null, locationId, BigDecimal.ZERO, expiresAt, false,
                "GHTK does not currently support delivery to this address");
        }
        if (response.charge() == null || response.charge().signum() < 0) {
            throw new RetailIntegrationUnavailableException("GHTK returned an invalid shipping fee");
        }
        String id = "local-ghtk-" + UUID.randomUUID();
        RetailCheckoutPorts.ShippingQuote quote = new RetailCheckoutPorts.ShippingQuote(id, locationId, response.charge(), expiresAt);
        quotes.put(id, new StoredQuote(memberAccountId, quote));
        return quote;
    }

    private boolean hasRecipient(RetailCheckoutPorts.RecipientAddress recipient) {
        return hasText(recipient.address()) && hasText(recipient.province()) && hasText(recipient.district()) && hasText(recipient.ward());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record StoredQuote(Long memberAccountId, RetailCheckoutPorts.ShippingQuote quote) { }
}
