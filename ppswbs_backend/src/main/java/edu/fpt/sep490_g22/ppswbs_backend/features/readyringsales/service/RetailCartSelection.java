package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the cart lines a Member ticked for checkout. Only the selected lines are priced,
 * held and ordered; unselected lines stay in the cart untouched.
 */
final class RetailCartSelection {
    static final int MAX_SELECTED_LINES = 50;

    private RetailCartSelection() { }

    /**
     * @param cartLineIds null keeps the legacy whole-cart checkout; otherwise every id must be a
     *                    distinct line of this cart and at least one id is required.
     */
    static List<CartLine> select(RetailCart cart, List<String> cartLineIds) {
        if (cartLineIds == null) return List.copyOf(cart.getLines());
        if (cartLineIds.isEmpty()) throw new RetailConflictException("Select at least one cart line to check out");
        if (cartLineIds.size() > MAX_SELECTED_LINES) throw new RetailConflictException("Too many cart lines selected");
        Set<String> requested = new HashSet<>();
        for (String id : cartLineIds) {
            if (id == null || id.isBlank() || !requested.add(id)) throw new RetailConflictException("Selected cart lines are invalid");
        }
        List<CartLine> selected = cart.getLines().stream().filter(line -> requested.contains(line.getId())).toList();
        if (selected.size() != requested.size()) {
            throw new RetailConflictException("Selected cart lines changed; reload the cart");
        }
        return selected;
    }

    /** Removes exactly the given lines (by id) after they became an accepted checkout. */
    static void removeCheckedOut(RetailCart cart, List<CartLine> checkedOut) {
        Set<String> ids = new HashSet<>();
        checkedOut.forEach(line -> ids.add(line.getId()));
        cart.getLines().removeIf(line -> ids.contains(line.getId()));
        cart.touch();
    }
}
