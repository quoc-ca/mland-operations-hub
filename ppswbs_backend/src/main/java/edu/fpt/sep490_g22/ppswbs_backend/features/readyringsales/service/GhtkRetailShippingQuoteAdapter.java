package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShippingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShippingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Non-local shipping quote adapter backed by the real GHTK fee GET only.
 * The HTTP call runs outside any database transaction (NOT_SUPPORTED suspends a
 * caller transaction); the persisted quote is written afterwards by the
 * {@link DurableShippingQuoteWriter} in its own short transaction. No carrier
 * shipment is created and no mock fallback exists: an unavailable GHTK fee fails closed.
 */
@Service
@org.springframework.context.annotation.Profile("!local")
@RequiredArgsConstructor
public class GhtkRetailShippingQuoteAdapter implements RetailShippingQuotePort {
    static final int GRAMS_PER_RING = 50;
    static final long QUOTE_TTL_MINUTES = 15;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999999");

    private final RetailCartRepository carts;
    private final RetailShippingQuoteRepository storedQuotes;
    private final DurableShippingQuoteWriter writer;
    private final GhtkQuoteProperties properties;
    private final GhtkFeeTransport ghtk;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public RetailCheckoutPorts.ShippingQuote quote(Long memberAccountId, Long locationId,
                                                   List<RetailCheckoutPorts.PricedLine> lines,
                                                   RetailCheckoutPorts.RecipientAddress recipient) {
        requireValidInput(memberAccountId, locationId, lines, recipient);
        RetailCart cart = carts.findByMemberAccountId(memberAccountId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        String digest = bindingDigest(cart, locationId, lines, recipient);
        FeeResult fee = requestFee(lines, recipient);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = now.plus(QUOTE_TTL_MINUTES, ChronoUnit.MINUTES);
        if (!fee.deliveryAvailable()) {
            return new RetailCheckoutPorts.ShippingQuote(null, locationId, BigDecimal.ZERO, expiresAt, false,
                "GHTK does not currently support delivery to this address");
        }
        BigDecimal charge = integralFee(fee.charge());
        RetailShippingQuote stored = writer.store(new RetailShippingQuote(memberAccountId, cart.getId(),
            cart.getVersion(), locationId, digest, charge, now, expiresAt));
        return new RetailCheckoutPorts.ShippingQuote(stored.getId(), locationId, charge, stored.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public RetailCheckoutPorts.ShippingQuote requireValidQuote(Long memberAccountId, Long locationId, String quoteId,
                                                              List<RetailCheckoutPorts.PricedLine> currentLines,
                                                              RetailCheckoutPorts.RecipientAddress recipient) {
        requireValidInput(memberAccountId, locationId, currentLines, recipient);
        if (quoteId == null || quoteId.isBlank()) {
            throw new RetailConflictException("GHTK requires a server-issued shipping quote");
        }
        RetailShippingQuote stored = storedQuotes.findByIdAndMemberAccountId(quoteId, memberAccountId)
            .orElseThrow(() -> new RetailConflictException("Shipping quote is no longer valid for this branch"));
        RetailCart cart = carts.findByMemberAccountId(memberAccountId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        if (!Objects.equals(stored.getCartId(), cart.getId()) || stored.getCartVersion() != cart.getVersion()
            || !stored.getLocationId().equals(locationId) || !Instant.now().isBefore(stored.getExpiresAt())
            || !Objects.equals(stored.getBindingDigest(), bindingDigest(cart, locationId, currentLines, recipient))) {
            throw new RetailConflictException("Shipping quote is no longer valid for this branch");
        }
        return new RetailCheckoutPorts.ShippingQuote(stored.getId(), stored.getLocationId(), stored.getCharge(),
            stored.getExpiresAt());
    }

    private void requireValidInput(Long memberAccountId, Long locationId, List<RetailCheckoutPorts.PricedLine> lines,
                                   RetailCheckoutPorts.RecipientAddress recipient) {
        if (memberAccountId == null || locationId == null || lines == null || lines.isEmpty()
            || lines.stream().anyMatch(line -> line == null || !locationId.equals(line.locationId())
                || line.quantity() <= 0 || line.unitPrice() == null || line.unitPrice().signum() < 0)) {
            throw new RetailConflictException("Shipping quote requires valid items for this branch");
        }
        if (recipient == null || !hasText(recipient.address()) || !hasText(recipient.province())
            || !hasText(recipient.district())) {
            throw new RetailConflictException("GHTK shipping quote requires a complete recipient address");
        }
    }

    private FeeResult requestFee(List<RetailCheckoutPorts.PricedLine> lines, RetailCheckoutPorts.RecipientAddress recipient) {
        if (!properties.isEnabled() || !properties.hasRequiredConfiguration()) {
            throw new RetailIntegrationUnavailableException("GHTK fee quote is not configured");
        }
        int ringCount = lines.stream().mapToInt(RetailCheckoutPorts.PricedLine::quantity).sum();
        if (ringCount <= 0) throw new RetailConflictException("Shipping quote requires at least one ring");
        int weight = Math.multiplyExact(ringCount, GRAMS_PER_RING);
        BigDecimal declaredValue = lines.stream()
            .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        GhtkFeeTransport.FeeRequest request = new GhtkFeeTransport.FeeRequest(
            properties.getPickupAddress(), properties.getPickupProvince(), properties.getPickupDistrict(),
            properties.getPickupWard(), recipient.address(), recipient.province(), recipient.district(),
            recipient.ward(), weight, declaredValue);
        GhtkFeeTransport.FeeResponse response = ghtk.quote(request);
        // GHTK's administrative directory can lag behind the address directory used by the member
        // profile. Preserve the full address first, then retry without the optional ward query
        // parameter only when GHTK explicitly says the route is unavailable.
        if (response.success() && !response.deliveryAvailable() && recipient.ward() != null && !recipient.ward().isBlank()) {
            response = ghtk.quote(new GhtkFeeTransport.FeeRequest(
                request.pickupAddress(), request.pickupProvince(), request.pickupDistrict(), request.pickupWard(),
                request.address(), request.province(), request.district(), null, request.weightGrams(),
                request.declaredValue()));
        }
        if (!response.success()) {
            throw new RetailIntegrationUnavailableException("GHTK shipping quote is unavailable");
        }
        if (!response.deliveryAvailable()) return new FeeResult(null, false);
        if (response.charge() == null || response.charge().signum() < 0) {
            throw new RetailIntegrationUnavailableException("GHTK returned an invalid shipping fee");
        }
        return new FeeResult(response.charge(), true);
    }

    private BigDecimal integralFee(BigDecimal charge) {
        try {
            BigDecimal integral = charge.stripTrailingZeros();
            if (integral.scale() > 0 || integral.compareTo(MAX_AMOUNT) > 0) throw new ArithmeticException("not an integral VND amount");
            return new BigDecimal(integral.toBigIntegerExact());
        } catch (ArithmeticException exception) {
            throw new RetailIntegrationUnavailableException("GHTK returned an invalid shipping fee");
        }
    }

    /** Binds the quote to owner, cart version, branch items/prices and the exact recipient. */
    private String bindingDigest(RetailCart cart, Long locationId, List<RetailCheckoutPorts.PricedLine> lines,
                                 RetailCheckoutPorts.RecipientAddress recipient) {
        StringBuilder payload = new StringBuilder()
            .append(text(cart.getId())).append('|').append(cart.getVersion()).append('|').append(locationId).append('|')
            .append(text(recipient.address())).append('|').append(text(recipient.province())).append('|')
            .append(text(recipient.district())).append('|').append(text(recipient.ward())).append('|')
            .append(text(recipient.recipientName())).append('|').append(text(recipient.recipientPhone()));
        lines.stream().map(line -> line.variantId() + "x" + line.quantity() + "@" + line.unitPrice().stripTrailingZeros().toPlainString())
            .sorted().forEach(line -> payload.append('|').append(line));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String text(String value) { return value == null ? "" : value; }
    private record FeeResult(BigDecimal charge, boolean deliveryAvailable) { }
}
