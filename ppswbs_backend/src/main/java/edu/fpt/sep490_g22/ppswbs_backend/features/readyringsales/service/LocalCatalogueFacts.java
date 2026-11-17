package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Local profile only: reads the real FE-02 branch sale fact (name, size, applied price, stock) so the cart and
 * checkout show catalogue prices. A size the catalogue does not know falls back to the deterministic demo price.
 * The read runs in its own read-only transaction, so a "not found" answer never marks the caller's transaction
 * for rollback.
 */
@Component
@Profile("local")
class LocalCatalogueFacts {
    private final CatalogueSalesFacade catalogue;
    private final TransactionTemplate isolatedRead;

    LocalCatalogueFacts(CatalogueSalesFacade catalogue, PlatformTransactionManager transactions) {
        this.catalogue = catalogue;
        this.isolatedRead = new TransactionTemplate(transactions);
        this.isolatedRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolatedRead.setReadOnly(true);
    }

    Optional<BranchCartSaleFact> read(Long variantId, Long locationId) {
        if (variantId == null || variantId <= 0 || locationId == null || locationId <= 0) return Optional.empty();
        try {
            List<BranchCartSaleFact> facts = isolatedRead.execute(status ->
                catalogue.readBranchSaleFacts(List.of(new BranchVariant(variantId, locationId))));
            return facts != null && facts.size() == 1 ? Optional.ofNullable(facts.getFirst()) : Optional.empty();
        } catch (RuntimeException unknownOrUnavailable) {
            return Optional.empty();
        }
    }

    static BigInteger demoPrice(Long variantId) {
        return BigInteger.valueOf(500_000L + Math.floorMod(variantId, 500_000L));
    }
}
