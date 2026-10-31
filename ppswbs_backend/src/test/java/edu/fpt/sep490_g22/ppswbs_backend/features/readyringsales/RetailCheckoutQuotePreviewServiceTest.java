package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailCheckoutQuotePreviewServiceTest {
    private final RetailCartRepository carts = mock(RetailCartRepository.class);
    private final RetailCheckoutQuoteRepository quotes = mock(RetailCheckoutQuoteRepository.class);
    private final CatalogueSalesFacade catalogue = mock(CatalogueSalesFacade.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private final RetailCheckoutQuotePreviewService service = new RetailCheckoutQuotePreviewService(carts, quotes, catalogue, loyalty);

    @Test
    void persistsServerPricedPickupQuoteWithoutReservingBenefits() {
        RetailCart cart = cart();
        when(carts.findByMemberAccountId(1L)).thenReturn(Optional.of(cart));
        when(catalogue.readSaleFacts(List.of(101L))).thenReturn(List.of(
            new CartSaleFact(101L, 5L, "Ring", "SKU", 10, BigInteger.valueOf(100), "VND", true, BigInteger.TEN)));
        when(loyalty.previewBenefits(any(), any())).thenReturn(new BenefitPreview(
            BigInteger.valueOf(200), BigInteger.valueOf(200), BigInteger.TEN, BigInteger.valueOf(190),
            BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.valueOf(190), BigInteger.valueOf(190), null, null, null, 1L, true, List.of(), "digest"));
        when(quotes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.preview(1L, request("PICKUP"));

        assertThat(result.merchandiseSubtotal()).isEqualTo("200");
        assertThat(result.payableAmount()).isEqualTo("190");
        assertThat(result.shippingTotal()).isEqualTo("0");
        var captor = org.mockito.ArgumentCaptor.forClass(RetailCheckoutQuote.class);
        verify(quotes).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTermsDigest()).isEqualTo("digest");
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(captor.getValue().getCreatedAt().plusSeconds(900));
        assertThat(captor.getValue().getBranches().getFirst().getItems().getFirst().getProductId()).isEqualTo(5L);
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), any());
    }

    @Test
    void ghtkFailsClosedBeforeAQuoteIsCreated() {
        when(carts.findByMemberAccountId(1L)).thenReturn(Optional.of(cart()));
        assertThatThrownBy(() -> service.preview(1L, request("GHTK")))
            .isInstanceOf(RetailIntegrationUnavailableException.class);
        verifyNoInteractions(quotes, catalogue, loyalty);
    }

    private RetailCart cart() {
        RetailCart cart = new RetailCart(1L);
        ReflectionTestUtils.setField(cart, "id", "cart-1");
        ReflectionTestUtils.setField(cart, "version", 1L);
        cart.addLine(101L, 10L, 2);
        return cart;
    }

    private CheckoutDtos.BenefitPreviewRequest request(String method) {
        return new CheckoutDtos.BenefitPreviewRequest(new CheckoutDtos.BenefitSelection("NONE", null, "0"),
            List.of(new CheckoutDtos.BranchOrderCheckoutInput(10L, method, null)));
    }
}
