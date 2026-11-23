package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class CartAvailabilityMessageTest {

    @Test
    void explainsWhatIsAlreadyInTheCart() {
        assertThat(CartService.availabilityMessage(BigInteger.TWO, 2))
            .isEqualTo("Bạn đã có 2 sản phẩm này trong giỏ hàng. Không thể thêm số lượng đã chọn vào giỏ hàng "
                + "vì sẽ vượt quá số lượng còn tại chi nhánh (2).");
    }

    @Test
    void statesBranchStockWhenCartIsEmpty() {
        assertThat(CartService.availabilityMessage(BigInteger.ONE, 0))
            .isEqualTo("Chi nhánh chỉ còn 1 sản phẩm này.");
    }
}
