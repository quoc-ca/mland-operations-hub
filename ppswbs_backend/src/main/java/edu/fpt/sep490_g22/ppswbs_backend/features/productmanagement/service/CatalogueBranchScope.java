package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Branch scope of the current actor (FR-050, Q-B3, Session 2026-11-08).
 *
 * <ul>
 *   <li>A Manager acts at every branch.</li>
 *   <li>A Staff member acts only at the branches assigned to the account, read from the 001 identity facade on every
 *       call (never from what the client sends). No assigned branch → {@code BRANCH_SCOPE_EMPTY}; a branch outside
 *       the scope → {@code BRANCH_OUT_OF_SCOPE}.</li>
 *   <li>Content, images and proposed prices are shared by every branch, so a Staff member may change them only for a
 *       product sold at one of their branches (Q-B3). A product not yet assigned to any branch (data from before
 *       Session 2026-11-08) is not restricted.</li>
 * </ul>
 * Call after {@link CatalogueAuthorization#require}, which already checked role and status.
 */
@Component
public class CatalogueBranchScope {

    /** Every branch (Manager), or exactly these branches (Staff). */
    public record Scope(boolean all, Set<Long> locationIds) {
        public boolean allows(long locationId) {
            return all || locationIds.contains(locationId);
        }
    }

    private final AccountFacade accounts;
    private final VariantBranchStockRepository stocks;
    private final ProductVariantRepository variants;
    private final PriceProposalRepository proposals;

    public CatalogueBranchScope(AccountFacade accounts, VariantBranchStockRepository stocks,
                                ProductVariantRepository variants, PriceProposalRepository proposals) {
        this.accounts = accounts;
        this.stocks = stocks;
        this.variants = variants;
        this.proposals = proposals;
    }

    /**
     * Without a current actor the call is internal (a service or job inside the backend): every HTTP command passes
     * {@link CatalogueAuthorization#require} first, which rejects a request without an actor, so no client reaches
     * this unrestricted path.
     */
    public Scope current() {
        CurrentAccountActor actor = ActorContext.getCurrentActor().orElse(null);
        if (actor == null || actor.role() == ActorRole.MANAGER) {
            return new Scope(true, Set.of());
        }
        List<Long> assigned = accounts.getLocationScope(actor.accountId());
        return new Scope(false, assigned == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(assigned)));
    }

    /** Every given branch must be in the actor's scope. */
    public void requireBranches(Collection<Long> locationIds) {
        Scope scope = current();
        if (scope.all()) {
            return;
        }
        if (scope.locationIds().isEmpty()) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_SCOPE_EMPTY);
        }
        for (Long locationId : locationIds) {
            if (locationId == null || !scope.allows(locationId)) {
                throw new CatalogueException(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE);
            }
        }
    }

    /** Q-B3: shared content of the product may be changed by this actor. */
    public void requireProduct(long productId) {
        Scope scope = current();
        if (scope.all()) {
            return;
        }
        List<Long> sold = stocks.findLocationIdsByProductId(productId);
        if (sold.isEmpty()) {
            return;
        }
        if (scope.locationIds().isEmpty()) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_SCOPE_EMPTY);
        }
        if (sold.stream().noneMatch(scope::allows)) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE);
        }
    }

    /** {@link #requireProduct} for a command addressed by size. */
    public void requireProductOfVariant(String variantId) {
        requireProductOfVariant(CatalogueValues.parseId("variantId", variantId));
    }

    private void requireProductOfVariant(long variantId) {
        if (current().all()) {
            return;
        }
        long productId = variants.findProductIdById(variantId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
        requireProduct(productId);
    }

    /** {@link #requireProduct} for a command addressed by price proposal. */
    public void requireProductOfProposal(String proposalId) {
        if (current().all()) {
            return;
        }
        long id = CatalogueValues.parseId("proposalId", proposalId);
        long variantId = proposals.findVariantIdById(id)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));
        requireProductOfVariant(variantId);
    }

    /** {@link #requireProduct} for a command addressed by product number. */
    public void requireProduct(String productId) {
        requireProduct(CatalogueValues.parseId("productId", productId));
    }
}
