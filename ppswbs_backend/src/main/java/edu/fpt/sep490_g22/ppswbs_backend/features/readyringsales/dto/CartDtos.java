package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record RetailCartView(long version, List<CartLineView> lines) {
    }

    public record CartLineView(String id, Long variantId, Long locationId, int quantity,
                               String productName, String sku, Integer size, String indicativePrice,
                               String currency, String availableQuantity, boolean saleEligible) {
        public CartLineView(String id, Long variantId, Long locationId, int quantity) {
            this(id, variantId, locationId, quantity, null, null, null, null, null, null, false);
        }
    }

    public record AddCartLineRequest(
        @NotNull @Positive Long variantId,
        @NotNull @Positive Long locationId,
        @NotNull @Min(1) @Max(99) Integer quantity
    ) {
    }

    public record UpdateCartLineRequest(@NotNull @Min(1) @Max(99) Integer quantity) {
    }
}
