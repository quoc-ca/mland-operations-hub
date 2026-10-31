package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingPricingService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPricingIT {
    private final BookingPricingService pricing = new BookingPricingService();

    @Test
    void freezesWholeVndAmountsAndRoundsOddDepositUp() {
        var amounts = pricing.freeze(100_001, 3, 300_001);
        assertThat(amounts.packageTotal()).isEqualTo(300_003);
        assertThat(amounts.depositAmount()).isEqualTo(150_001);
        assertThat(amounts.remainingAmount()).isEqualTo(150_000);
    }

    @Test
    void rejectsOverflowAndPostPromotionAbovePackageTotal() {
        assertThatThrownBy(() -> pricing.freeze(Long.MAX_VALUE, 2, Long.MAX_VALUE))
                .isInstanceOf(BookingValidationException.class);
        assertThatThrownBy(() -> pricing.freeze(100, 2, 201))
                .isInstanceOf(BookingValidationException.class);
    }
}
