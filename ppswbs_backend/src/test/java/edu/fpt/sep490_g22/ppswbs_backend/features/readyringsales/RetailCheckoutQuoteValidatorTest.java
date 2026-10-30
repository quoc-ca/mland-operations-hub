package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleVariantFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutQuoteValidator;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RetailCheckoutQuoteValidatorTest {
    private final RetailCheckoutQuoteValidator validator = new RetailCheckoutQuoteValidator();
    private final Instant now = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void acceptsMatchingOwnedQuoteCartAndLockedPrice() {
        var quote = quote();
        assertThatCode(() -> validator.validate(quote, cart(), request(quote), context(100), now)).doesNotThrowAnyException();
    }

    @Test
    void rejectsChangedPriceWithoutAcceptingQuote() {
        var quote = quote();
        assertThatThrownBy(() -> validator.validate(quote, cart(), request(quote), context(101), now))
            .isInstanceOf(RetailConflictException.class);
        assertThat(quote.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void rejectsExpiredQuoteAndUnacknowledgedTerms() {
        var quote = quote();
        assertThatThrownBy(() -> validator.validate(quote, cart(), request(quote), context(100), now.plusSeconds(900)))
            .isInstanceOf(RetailConflictException.class);
        var noAcknowledgement = new CheckoutDtos.CreateGroupedCheckoutRequest(
            List.of(new CheckoutDtos.BranchOrderCheckoutInput(10L, "PICKUP", null)), quote.getId(),
            new CheckoutDtos.BenefitSelection("NONE", null, "0"), false);
        assertThatThrownBy(() -> validator.validate(quote, cart(), noAcknowledgement, context(100), now))
            .isInstanceOf(RetailConflictException.class);
    }

    @Test
    void rejectsChangedCartQuantityEvenWhenVersionAppearsUnchanged() {
        var quote = quote();
        var changed = cart();
        changed.getLines().getFirst().changeQuantity(2);
        assertThatThrownBy(() -> validator.validate(quote, changed, request(quote), context(100), now))
            .isInstanceOf(RetailConflictException.class);
    }

    private RetailCart cart() {
        var cart = new RetailCart(1L);
        ReflectionTestUtils.setField(cart, "id", "cart-1");
        ReflectionTestUtils.setField(cart, "version", 2L);
        cart.addLine(101L, 10L, 1);
        return cart;
    }

    private RetailCheckoutQuote quote() {
        var quote = new RetailCheckoutQuote(1L, "cart-1", 2L, "NONE", null, BigInteger.ZERO, "digest", "VND",
            BigInteger.valueOf(100), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(100), now, now.plusSeconds(900));
        var branch = new RetailCheckoutQuoteBranch(quote, 10L, "PICKUP", null, BigInteger.ZERO, BigInteger.valueOf(100));
        branch.addItem(new RetailCheckoutQuoteItem(branch, 5L, 101L, 1, BigInteger.valueOf(100)));
        quote.addBranch(branch);
        return quote;
    }

    private CheckoutDtos.CreateGroupedCheckoutRequest request(RetailCheckoutQuote quote) {
        return new CheckoutDtos.CreateGroupedCheckoutRequest(List.of(new CheckoutDtos.BranchOrderCheckoutInput(10L, "PICKUP", null)),
            quote.getId(), new CheckoutDtos.BenefitSelection("NONE", null, "0"), true);
    }

    private SaleContext context(long price) {
        return new SaleContext(now, null, List.of(new SaleVariantFact(5L, "Ring", 101L, "SKU", 10,
            BigInteger.valueOf(price), "VND", BigInteger.TEN, true, false, true, 1, 1, 1, 1, List.of())));
    }
}
