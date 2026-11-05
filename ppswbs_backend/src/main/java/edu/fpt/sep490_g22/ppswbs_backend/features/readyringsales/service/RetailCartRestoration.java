package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Restores as much of an accepted order as fits without discarding existing cart lines. */
final class RetailCartRestoration {
    private RetailCartRestoration() { }

    static int restore(RetailCart cart, GroupedCheckout checkout) {
        Map<Long, Integer> totals = new HashMap<>();
        for (CartLine line : cart.getLines()) totals.merge(line.getVariantId(), line.getQuantity(), Integer::sum);
        int notRestored = 0;
        boolean changed = false;
        var orders = checkout.getOrders().stream().sorted(Comparator.comparing(order -> order.getLocationId())).toList();
        for (var order : orders) {
            var items = order.getItems().stream().sorted(Comparator.comparing(item -> item.getVariantId())).toList();
            for (var item : items) {
                var existing = cart.getLines().stream().filter(line -> line.getLocationId().equals(order.getLocationId())
                    && line.getVariantId().equals(item.getVariantId())).findFirst();
                int room = Math.max(0, 99 - totals.getOrDefault(item.getVariantId(), 0));
                int quantity = existing.isEmpty() && cart.getLines().size() >= 50 ? 0 : Math.min(room, item.getQuantity());
                if (quantity > 0) {
                    changed = true;
                    if (existing.isPresent()) existing.get().changeQuantity(existing.get().getQuantity() + quantity);
                    else cart.addLine(item.getVariantId(), order.getLocationId(), quantity);
                    totals.merge(item.getVariantId(), quantity, Integer::sum);
                }
                notRestored += item.getQuantity() - quantity;
            }
        }
        if (changed) cart.touch();
        return notRestored;
    }
}
