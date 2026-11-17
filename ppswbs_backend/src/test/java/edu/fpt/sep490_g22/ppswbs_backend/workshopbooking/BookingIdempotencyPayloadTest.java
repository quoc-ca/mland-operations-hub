package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIdempotencyPayloadTest {

    @Test
    void aResponseWithoutABodyIsStoredAsNullBecauseTheColumnIsJson() {
        // Releasing a pending hold answers 204 with no body; "" is not valid JSON for MySQL and failed the whole command.
        assertThat(BookingIdempotencyService.jsonColumnValue("")).isNull();
        assertThat(BookingIdempotencyService.jsonColumnValue("   ")).isNull();
        assertThat(BookingIdempotencyService.jsonColumnValue(null)).isNull();
    }

    @Test
    void aRealPayloadIsKeptAsIs() {
        assertThat(BookingIdempotencyService.jsonColumnValue("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }
}
