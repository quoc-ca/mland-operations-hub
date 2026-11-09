package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Branch-aware order keys used at FE-02 inventory boundaries. */
final class RetailBranchOrderKeys {
    private RetailBranchOrderKeys() { }

    static Map<BranchVariant, Integer> quantities(GroupedCheckout checkout) {
        Map<BranchVariant, Integer> quantities = new HashMap<>();
        checkout.getOrders().forEach(order -> order.getItems().forEach(item -> quantities.merge(
            new BranchVariant(item.getVariantId(), order.getLocationId()), item.getQuantity(), Integer::sum)));
        return quantities;
    }

    static List<BranchVariant> keys(GroupedCheckout checkout) {
        return quantities(checkout).keySet().stream().sorted().toList();
    }

    static List<Long> variantIds(Map<BranchVariant, Integer> quantities) {
        return quantities.keySet().stream().map(BranchVariant::variantId).distinct().sorted().toList();
    }
}
