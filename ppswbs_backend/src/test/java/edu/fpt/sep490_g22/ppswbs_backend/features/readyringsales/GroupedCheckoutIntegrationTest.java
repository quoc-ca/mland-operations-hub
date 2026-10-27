package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailBenefitPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPricingPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GroupedCheckoutIntegrationTest {
    @Autowired CartService cartService;
    @Autowired RetailCheckoutService checkoutService;
    @Autowired RetailPaymentService paymentService;
    @Autowired RetailCartRepository cartRepository;
    @Autowired GroupedCheckoutRepository checkoutRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockBean edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort cataloguePort;
    @MockBean RetailCheckoutPricingPort pricingPort;
    @MockBean RetailBenefitPort benefitPort;
    @MockBean RetailShippingQuotePort shippingQuotePort;
    @MockBean RetailPaymentPort paymentPort;

    @BeforeEach
    void setUp() {
        checkoutRepository.deleteAll(); cartRepository.deleteAll(); accountRepository.deleteAll();
        when(cataloguePort.isEligibleForCart(anyString(), anyLong())).thenReturn(true);
        when(pricingPort.price(any())).thenAnswer(invocation -> ((List<edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine>) invocation.getArgument(0)).stream()
            .map(line -> new RetailCheckoutPorts.PricedLine(line.getVariantId(), line.getLocationId(), line.getQuantity(), BigDecimal.valueOf(500_000))).toList());
        when(benefitPort.preview(anyLong(), any(), any())).thenReturn(new RetailCheckoutPorts.BenefitPreview("preview-1", BigDecimal.valueOf(100_000), BigDecimal.ZERO, Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(benefitPort.requireValidPreview(anyLong(), anyString(), any(), any())).thenReturn(new RetailCheckoutPorts.BenefitPreview("preview-1", BigDecimal.valueOf(100_000), BigDecimal.ZERO, Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(shippingQuotePort.requireValidQuote(anyLong(), anyLong(), anyString())).thenReturn(new RetailCheckoutPorts.ShippingQuote("quote-1", 20L, BigDecimal.valueOf(35_000), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(paymentPort.initiate(anyString(), any())).thenAnswer(invocation -> new RetailCheckoutPorts.PaymentInitiation("https://local.mock/vnpay/" + invocation.getArgument(0), Instant.now().plus(15, ChronoUnit.MINUTES), true));
    }

    @Test
    void appliesV7AndCreatesAnIdempotentMixedBranchCheckoutThenCompletesLocalMockPayment() {
        Long memberId = createMember().getId();
        cartService.addLine(memberId, new CartDtos.AddCartLineRequest("RING-01", 10L, 1));
        cartService.addLine(memberId, new CartDtos.AddCartLineRequest("RING-02", 20L, 2));
        CheckoutDtos.CreateGroupedCheckoutRequest request = new CheckoutDtos.CreateGroupedCheckoutRequest(List.of(
            new CheckoutDtos.BranchOrderCheckoutInput(10L, "PICKUP", null),
            new CheckoutDtos.BranchOrderCheckoutInput(20L, "GHTK", "quote-1")
        ), "preview-1", new CheckoutDtos.BenefitSelection("VOUCHER", "MLAND100", "0"), true);

        CheckoutDtos.GroupedCheckoutView created = checkoutService.createCheckout(memberId, "idempotency-1", request);
        CheckoutDtos.GroupedCheckoutView replay = checkoutService.createCheckout(memberId, "idempotency-1", request);
        CheckoutDtos.PaymentRedirectView payment = paymentService.initiate(memberId, created.id());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'retail_grouped_checkouts'", Integer.class)).isEqualTo(1);
        assertThat(created.branchOrders()).hasSize(2);
        assertThat(created.combinedSubtotal()).isEqualTo("1500000");
        assertThat(created.combinedShippingCharge()).isEqualTo("35000");
        assertThat(created.combinedPayableAmount()).isEqualTo("1435000");
        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(cartService.getCart(memberId).lines()).isEmpty();
        assertThat(payment.paymentState()).isEqualTo("PAID");
        assertThat(checkoutRepository.findByIdAndMemberAccountId(created.id(), memberId).orElseThrow().getPaymentState().name()).isEqualTo("PAID");
    }

    private Account createMember() {
        return accountRepository.saveAndFlush(Account.builder().firebaseUid("checkout-" + UUID.randomUUID())
            .loginEmail("checkout-" + UUID.randomUUID() + "@example.test").build());
    }
}
