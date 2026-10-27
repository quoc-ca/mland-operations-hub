package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record RetailCartView(long version, List<CartLineView> lines) {
    }

    public record CartLineView(String id, String variantId, Long locationId, int quantity) {
    }

    public record AddCartLineRequest(
        @NotBlank @Size(max = 128) String variantId,
        @NotNull @Positive Long locationId,
        @NotNull @Min(1) @Max(99) Integer quantity
    ) {
    }

    public record UpdateCartLineRequest(@NotNull @Min(1) @Max(99) Integer quantity) {
    }
}
