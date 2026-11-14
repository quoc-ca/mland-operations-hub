package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuote;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailCheckoutQuoteTest {
    private static final Instant CREATED = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void acceptsOnlyAnOpenUnexpiredQuoteOnce() {
        RetailCheckoutQuote quote = quote();
        quote.accept("checkout-1", CREATED.plusSeconds(10));
        assertThat(quote.getStatus()).isEqualTo("ACCEPTED");
        assertThat(quote.getAcceptedCheckoutId()).isEqualTo("checkout-1");
        assertThatThrownBy(() -> quote.accept("checkout-2", CREATED.plusSeconds(11)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnExpiredQuoteAndDoesNotChangeItsState() {
        RetailCheckoutQuote quote = quote();
        assertThatThrownBy(() -> quote.accept("checkout-1", CREATED.plusSeconds(60)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(quote.getStatus()).isEqualTo("OPEN");
        assertThat(quote.getAcceptedCheckoutId()).isNull();
    }

    private RetailCheckoutQuote quote() {
        return new RetailCheckoutQuote(1L, "cart-1", 0, "NONE", null, BigInteger.ZERO,
            "digest", "VND", BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.TEN, CREATED, CREATED.plusSeconds(60));
    }
}
