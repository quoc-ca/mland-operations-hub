package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigInteger;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_CART_LINES = 50;
    private static final int MAX_VARIANT_QUANTITY = 99;

    private final RetailCartRepository cartRepository;
    private final RetailCataloguePort cataloguePort;

    @Transactional(readOnly = true)
    public CartDtos.RetailCartView getCart(Long memberAccountId) {
        return cartRepository.findByMemberAccountId(memberAccountId)
            .map(this::toView)
            .orElseGet(() -> new CartDtos.RetailCartView(0L, List.of()));
    }

    @Transactional
    public CartDtos.RetailCartView addLine(Long memberAccountId, CartDtos.AddCartLineRequest request) {
        ensureEligible(request.variantId(), request.locationId());
        RetailCart cart = cartRepository.findByMemberAccountId(memberAccountId)
            .orElseGet(() -> new RetailCart(memberAccountId));

        CartLine existingLine = cart.getLines().stream()
            .filter(line -> line.getVariantId().equals(request.variantId()) && line.getLocationId().equals(request.locationId()))
            .findFirst()
            .orElse(null);

        int existingVariantTotal = totalForVariantAtLocation(cart, request.variantId(), request.locationId());
        int requestedTotal = existingVariantTotal + request.quantity();
        if (requestedTotal > MAX_VARIANT_QUANTITY) {
            throw new RetailConflictException("Variant quantity cannot exceed 99 for the selected location");
        }
        ensureAvailable(request.variantId(), request.locationId(), requestedTotal);

        if (existingLine == null) {
            if (cart.getLines().size() >= MAX_CART_LINES) {
                throw new RetailConflictException("Cart cannot contain more than 50 lines");
            }
            cart.addLine(request.variantId(), request.locationId(), request.quantity());
        } else {
            existingLine.changeQuantity(existingLine.getQuantity() + request.quantity());
            cart.touch();
        }
        return toView(cartRepository.save(cart));
    }

    @Transactional
    public CartDtos.RetailCartView updateLine(Long memberAccountId, String lineId, CartDtos.UpdateCartLineRequest request) {
        RetailCart cart = requireCart(memberAccountId);
        CartLine line = findOwnedLine(cart, lineId);
        int totalAfterUpdate = totalForVariantAtLocation(cart, line.getVariantId(), line.getLocationId())
            - line.getQuantity() + request.quantity();
        if (totalAfterUpdate > MAX_VARIANT_QUANTITY) {
            throw new RetailConflictException("Variant quantity cannot exceed 99 for the selected location");
        }
        ensureAvailable(line.getVariantId(), line.getLocationId(), totalAfterUpdate);
        line.changeQuantity(request.quantity());
        cart.touch();
        return toView(cartRepository.save(cart));
    }

    @Transactional
    public void removeLine(Long memberAccountId, String lineId) {
        RetailCart cart = requireCart(memberAccountId);
        cart.removeLine(findOwnedLine(cart, lineId));
        cartRepository.save(cart);
    }

    private RetailCart requireCart(Long memberAccountId) {
        return cartRepository.findByMemberAccountId(memberAccountId)
            .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
    }

    private CartLine findOwnedLine(RetailCart cart, String lineId) {
        return cart.getLines().stream()
            .filter(line -> line.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new RetailNotFoundException("Cart line does not exist"));
    }

    private int totalForVariantAtLocation(RetailCart cart, Long variantId, Long locationId) {
        return cart.getLines().stream()
            .filter(line -> line.getVariantId().equals(variantId) && line.getLocationId().equals(locationId))
            .mapToInt(CartLine::getQuantity)
            .sum();
    }

    private void ensureEligible(Long variantId, Long locationId) {
        if (!cataloguePort.isEligibleForCart(variantId, locationId)) {
            throw new RetailConflictException("Variant is not eligible for the selected location");
        }
    }

    private void ensureAvailable(Long variantId, Long locationId, int totalQuantity) {
        var fact = cataloguePort.readCartProduct(variantId, locationId);
        if (fact == null) {
            throw new RetailIntegrationUnavailableException("Catalogue returned no variant facts");
        }
        if (!fact.saleEligible()) {
            throw new RetailConflictException("Variant is no longer available for sale");
        }
        try {
            if (fact.availableQuantity() == null) {
                throw new RetailIntegrationUnavailableException("Catalogue quantity is unavailable");
            }
            BigInteger available = new BigInteger(fact.availableQuantity());
            if (available.signum() < 0) {
                throw new RetailIntegrationUnavailableException("Catalogue quantity is invalid");
            }
            if (BigInteger.valueOf(totalQuantity).compareTo(available) > 0) {
                throw new RetailConflictException("Requested quantity exceeds current availability");
            }
        } catch (NumberFormatException exception) {
            throw new RetailIntegrationUnavailableException("Catalogue quantity is invalid");
        }
    }

    private CartDtos.RetailCartView toView(RetailCart cart) {
        var facts = new java.util.HashMap<VariantLocationKey, RetailCataloguePort.CartProductFact>();
        List<CartDtos.CartLineView> lines = cart.getLines().stream()
            .map(line -> {
                var key = new VariantLocationKey(line.getVariantId(), line.getLocationId());
                var fact = facts.computeIfAbsent(key, selected -> cataloguePort.readCartProduct(selected.variantId(), selected.locationId()));
                if (fact == null) {
                    throw new RetailIntegrationUnavailableException("Catalogue returned no variant facts");
                }
                return new CartDtos.CartLineView(line.getId(), line.getVariantId(), line.getLocationId(), line.getQuantity(),
                    fact.productName(), fact.sku(), fact.size(), fact.indicativePrice(), fact.currency(),
                    fact.availableQuantity(), fact.saleEligible());
            })
            .toList();
        return new CartDtos.RetailCartView(cart.getVersion() == null ? 0L : cart.getVersion(), lines);
    }

    private record VariantLocationKey(Long variantId, Long locationId) {}
}
