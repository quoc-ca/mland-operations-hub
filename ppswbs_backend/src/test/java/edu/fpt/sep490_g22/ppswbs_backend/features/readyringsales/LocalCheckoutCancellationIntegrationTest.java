package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.CheckoutHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.LocalCheckoutCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "local"})
class LocalCheckoutCancellationIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired CartService carts;
    @Autowired RetailCheckoutService checkout;
    @Autowired LocalCheckoutCancellationService cancellation;
    @Autowired CheckoutHoldRepository holds;

    @Test
    void cancelsMockUnpaidCheckoutAndRestoresItsCart() {
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("local-cancel-" + random)
            .loginEmail("local-cancel-" + random + "@example.test").build()).getId();
        carts.addLine(member, new CartDtos.AddCartLineRequest(101L, 10L, 2));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var preview = checkout.previewBenefits(member, new CheckoutDtos.BenefitPreviewRequest(selection));
        var accepted = checkout.createCheckout(member, "local-cancel-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(
                List.of(new CheckoutDtos.BranchOrderCheckoutInput(10L, "PICKUP", null)), preview.previewId(), selection, true));

        var result = cancellation.cancelAsDemoExpiry(member, accepted.id());

        assertThat(result.paymentState()).isEqualTo("EXPIRED");
        assertThat(carts.getCart(member).lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(2));
        assertThat(holds.findByGroupedCheckoutId(accepted.id()).orElseThrow().getStatus()).isEqualTo("LOCAL_EXPIRED");
    }
}
