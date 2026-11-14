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
        if (memberAccountId == null || locationId == null || lines == null || lines.isEmpty()
            || lines.stream().anyMatch(line -> line == null || !locationId.equals(line.locationId())
                || line.quantity() <= 0 || line.unitPrice() == null || line.unitPrice().signum() < 0)) {
            throw new RetailConflictException("Shipping quote requires valid items for this branch");
        }
        quotes.entrySet().removeIf(entry -> !Instant.now().isBefore(entry.getValue().quote.expiresAt()));
        if (properties.isEnabled()) return quoteLiveFee(memberAccountId, locationId, lines, recipient);
        String id = "local-ghtk-" + UUID.randomUUID();
        RetailCheckoutPorts.ShippingQuote quote = new RetailCheckoutPorts.ShippingQuote(
            id, locationId, BigDecimal.valueOf(30_000L + Math.floorMod(locationId, 3) * 5_000L), Instant.now().plus(15, ChronoUnit.MINUTES));
        quotes.put(id, new StoredQuote(memberAccountId, quote, List.copyOf(lines), recipient));
        return quote;
    }
    @Override public RetailCheckoutPorts.ShippingQuote requireValidQuote(Long memberAccountId, Long locationId, String quoteId,
                                                                          List<RetailCheckoutPorts.PricedLine> currentLines,
                                                                          RetailCheckoutPorts.RecipientAddress recipient) {
        StoredQuote stored = quoteId == null ? null : quotes.get(quoteId);
        if (stored == null || !stored.memberAccountId.equals(memberAccountId) || !stored.quote.locationId().equals(locationId)
            || !Instant.now().isBefore(stored.quote.expiresAt()) || !sameLines(stored.lines, currentLines)
            || !java.util.Objects.equals(stored.recipient, recipient)) {
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
        GhtkFeeTransport.FeeRequest request = new GhtkFeeTransport.FeeRequest(
            properties.getPickupAddress(), properties.getPickupProvince(), properties.getPickupDistrict(), properties.getPickupWard(),
            recipient.address(), recipient.province(), recipient.district(), recipient.ward(), weight, declaredValue);
        GhtkFeeTransport.FeeResponse response = ghtk.quote(request);
        // An old address may still carry a pre-merger district that GHTK no longer matches with the new ward.
        // When GHTK says the route is unavailable, retry once with the two-level address (no district).
        if (response.success() && !response.deliveryAvailable() && hasText(recipient.district())) {
            response = ghtk.quote(new GhtkFeeTransport.FeeRequest(
                request.pickupAddress(), request.pickupProvince(), request.pickupDistrict(), request.pickupWard(),
                request.address(), request.province(), null, request.ward(), request.weightGrams(), request.declaredValue()));
        }
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        if (!response.success()) {
            throw new RetailIntegrationUnavailableException("GHTK shipping quote is unavailable");
        }
        if (!response.deliveryAvailable()) {
            return new RetailCheckoutPorts.ShippingQuote(null, locationId, BigDecimal.ZERO, expiresAt, false,
                "GHTK does not currently support delivery to this address");
        }
        if (response.charge() == null || response.charge().signum() < 0
            || response.charge().stripTrailingZeros().scale() > 0) {
            throw new RetailIntegrationUnavailableException("GHTK returned an invalid shipping fee");
        }
        String id = "local-ghtk-" + UUID.randomUUID();
        RetailCheckoutPorts.ShippingQuote quote = new RetailCheckoutPorts.ShippingQuote(id, locationId, response.charge(), expiresAt);
        quotes.put(id, new StoredQuote(memberAccountId, quote, List.copyOf(lines), recipient));
        return quote;
    }

    private boolean hasRecipient(RetailCheckoutPorts.RecipientAddress recipient) {
        // After the 2025 merger an address is Province -> Ward/Commune (FE-001 V150); GHTK's fee API requires
        // province and ward, and the district is only an optional hint.
        return hasText(recipient.address()) && hasText(recipient.province()) && hasText(recipient.ward());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean sameLines(List<RetailCheckoutPorts.PricedLine> original, List<RetailCheckoutPorts.PricedLine> current) {
        if (current == null || original.size() != current.size()) return false;
        var remaining = new java.util.ArrayList<>(current);
        for (var line : original) {
            int index = -1;
            for (int i = 0; i < remaining.size(); i++) {
                var other = remaining.get(i);
                if (other != null && java.util.Objects.equals(line.variantId(), other.variantId())
                    && java.util.Objects.equals(line.locationId(), other.locationId()) && line.quantity() == other.quantity()
                    && other.unitPrice() != null && line.unitPrice().compareTo(other.unitPrice()) == 0) { index = i; break; }
            }
            if (index < 0) return false;
            remaining.remove(index);
        }
        return true;
    }

    private record StoredQuote(Long memberAccountId, RetailCheckoutPorts.ShippingQuote quote,
                               List<RetailCheckoutPorts.PricedLine> lines, RetailCheckoutPorts.RecipientAddress recipient) { }
}
