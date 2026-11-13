package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailCartSelectionTest {

    @Test
    void nullSelectionKeepsWholeCart() {
        var cart = cart();
        assertThat(RetailCartSelection.select(cart, null)).hasSize(3);
    }

    @Test
    void selectsOnlyTickedLinesInCartOrder() {
        var cart = cart();
        var selected = RetailCartSelection.select(cart, List.of("line-3", "line-1"));
        assertThat(selected).extracting("id").containsExactly("line-1", "line-3");
    }

    @Test
    void rejectsEmptyDuplicateAndUnknownSelections() {
        var cart = cart();
        assertThatThrownBy(() -> RetailCartSelection.select(cart, List.of())).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> RetailCartSelection.select(cart, List.of("line-1", "line-1"))).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> RetailCartSelection.select(cart, List.of("line-9"))).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void removesOnlyCheckedOutLines() {
        var cart = cart();
        var selected = RetailCartSelection.select(cart, List.of("line-2"));
        RetailCartSelection.removeCheckedOut(cart, selected);
        assertThat(cart.getLines()).extracting("id").containsExactly("line-1", "line-3");
    }

    private RetailCart cart() {
        var cart = new RetailCart(1L);
        cart.addLine(101L, 10L, 1);
        cart.addLine(102L, 10L, 2);
        cart.addLine(201L, 20L, 1);
        for (int index = 0; index < cart.getLines().size(); index++) {
            ReflectionTestUtils.setField(cart.getLines().get(index), "id", "line-" + (index + 1));
        }
        return cart;
    }
}
