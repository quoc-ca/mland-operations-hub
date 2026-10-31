package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailCheckout.Accepted;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T058 on MySQL 8.4 with FE-08's REAL checkout (develop b367505): applying a new price only affects new sale
 * contexts. A checkout accepted at the old price keeps its unit price and payable amount and still settles; a quote
 * taken before the change cannot be accepted at the new price; a new checkout uses the new price (FR-016, FR-024).
 * The retention of receipts and business audit (5 years) is CatalogueRetentionIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({CatalogueTestSupport.MYSQL_PROFILE, RealRetailCheckout.PROFILE})
@Import({CatalogueFlowFixture.class, RealRetailCheckout.class})
class CatalogueAcceptedPriceIT {

    private static final BigInteger OLD_PRICE = BigInteger.valueOf(150_000);
    private static final BigInteger NEW_PRICE = BigInteger.valueOf(170_000);

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private RealRetailCheckout retail;
    @Autowired private CartService carts;
    @Autowired private VariantQuantityService quantities;
    @Autowired private PriceProposalService proposals;
    @Autowired private PriceApplicationService applications;
    @Autowired private ProductVariantRepository variants;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aNewPriceLeavesAcceptedCheckoutsAtTheirOldTermsAndAppliesToNewOnes() {
        long sizeId = sellableSize(5);
        Accepted old = retail.accept(sizeId, 1);
        long stalePreviewMember = retail.newMember();
        carts.addLine(stalePreviewMember, new CartDtos.AddCartLineRequest(sizeId, RealRetailCheckout.LOCATION, 1));
        CheckoutDtos.BenefitPreviewView stalePreview = retail.preview(stalePreviewMember);

        applyPrice(sizeId, "170000");
        assertThat(size(sizeId).getAppliedPrice()).isEqualTo(NEW_PRICE);

        // The accepted checkout keeps its unit price and amount, and settles at them.
        assertThat(retail.unitPrice(old, sizeId)).isEqualTo(OLD_PRICE);
        assertThat(old.payable()).isEqualByComparingTo("150000");
        retail.pay(old, "proof-" + UUID.randomUUID());
        assertThat(retail.paymentState(old)).isEqualTo("PAID");
        assertThat(retail.unitPrice(old, sizeId)).isEqualTo(OLD_PRICE);

        // A quote taken before the change is not accepted at the old price any more.
        assertThatThrownBy(() -> retail.acceptPreview(stalePreviewMember, stalePreview))
                .isInstanceOf(RetailConflictException.class);

        // A new checkout uses the new applied price.
        Accepted fresh = retail.accept(sizeId, 1);
        assertThat(fresh.payable()).isEqualByComparingTo("170000");
        assertThat(retail.unitPrice(fresh, sizeId)).isEqualTo(NEW_PRICE);
    }

    // ---- helpers ----

    private long sellableSize(long quantity) {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0);
        quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), Long.toString(quantity));
        return size.getId();
    }

    /** US5: propose → submit → approve → apply a new price for a published size. */
    private void applyPrice(long sizeId, String amount) {
        long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), Long.toString(sizeId),
                size(sizeId).getVersion(), amount).resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.APPROVE, amount, null));
        applications.apply(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
    }

    private ProductVariant size(long sizeId) {
        return transaction.execute(status -> variants.findById(sizeId).orElseThrow());
    }
}
