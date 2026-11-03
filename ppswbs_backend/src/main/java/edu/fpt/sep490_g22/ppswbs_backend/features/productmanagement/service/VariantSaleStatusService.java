package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stops or resumes sale of one existing size (T060, US6, FR-020/021/024).
 *
 * <p>Only {@code saleEnabled} changes: size, SKU, quantity, price, publication and the other sizes stay as
 * they are. A disabled size keeps its label and quantity and blocks new carts/holds through the sale gate;
 * accepted holds and orders are not touched. Resuming sale of a product that was published at least once
 * requires an applied approved price for the size; it never publishes the product. Asking for the state the
 * size already has is a no-op ({@code changed=false}) and does not need a matching version.
 * The caller authorizes the actor first (T007).
 */
@Service
public class VariantSaleStatusService {

    public static final String ACTION = "variant.sale-status";

    private final CatalogueLockCoordinator locks;
    private final ProductVariantRepository variants;
    private final PriceProposalRepository proposals;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public VariantSaleStatusService(CatalogueLockCoordinator locks, ProductVariantRepository variants,
                                    PriceProposalRepository proposals, CatalogueAuditWriter audit,
                                    CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.variants = variants;
        this.proposals = proposals;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome setSaleEnabled(String actorId, UUID commandId, String variantId,
                                                           long expectedVersion, boolean saleEnabled) {
        long id = CatalogueValues.parseId("variantId", variantId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("saleEnabled", saleEnabled);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            Long productId = variants.findProductIdById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            var locked = locks.lockProduct(productId);
            Product product = locked.product();
            product.requireNotDeleted();
            ProductVariant variant = locked.variants().stream()
                    .filter(candidate -> candidate.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            if (variant.isSaleEnabled() == saleEnabled) {
                return result(commandId, variant, false);
            }
            CatalogueLockCoordinator.requireVersion(variant.getVersion(), expectedVersion);
            if (saleEnabled && product.hasEverBeenPublished() && !hasAppliedApprovedPrice(variant)) {
                throw new CatalogueException(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING,
                        List.of(new ErrorResponse.FieldError("variants[" + variant.getSku() + "].appliedPrice",
                                "The size needs an applied approved price.")));
            }
            variant.setSaleEnabled(saleEnabled);
            ProductVariant saved = variants.saveAndFlush(variant);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTION, "variant",
                    saved.getId().toString(), saved.getVersion(), null, List.of("saleEnabled"),
                    Map.of("saleEnabled", saleEnabled, "sku", saved.getSku())));
            return result(commandId, saved, true);
        });
    }

    private boolean hasAppliedApprovedPrice(ProductVariant variant) {
        return variant.hasAppliedPrice() && variant.getAppliedProposalId() != null
                && proposals.findById(variant.getAppliedProposalId())
                .map(proposal -> proposal.getState() == ReviewState.APPROVED)
                .orElse(false);
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, ProductVariant variant,
                                                                 boolean changed) {
        ActionResult body = new ActionResult(commandId, variant.getId().toString(), "variant", ACTION,
                variant.isSaleEnabled() ? "ENABLED" : "DISABLED", variant.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "variant", variant.getId().toString(),
                variant.getVersion(), body);
    }
}
