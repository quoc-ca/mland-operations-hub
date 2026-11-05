package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sets the system-wide remaining unsold quantity of one size (T046, US4, FR-019/020/024/026).
 *
 * <ul>
 *   <li>One global quantity per size, not per branch. Checked against the current variant version.</li>
 *   <li>The new value may not be below the quantity held by valid FE-08 holds ({@code QUANTITY_BELOW_HELD});
 *       a hold is never cancelled or released to make it fit.</li>
 *   <li>A product that was never published cannot have a hold, so its held quantity is a proven zero.
 *       Otherwise the FE-08 source is required: without it the command fails with
 *       {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE} and nothing changes. The held quantity is read and the
 *       update saved while the FE-08 source guard of the size is held (AvailabilityGuardPort, T019); the
 *       guard stays held until commit, so no hold can be created or settled in between.</li>
 *   <li>Quantity is not reviewable content and never invalidates a review or a price decision.</li>
 * </ul>
 * The caller authorizes the actor first (T007).
 */
@Service
public class VariantQuantityService {

    public static final String ACTION = "variant.quantity";

    private final CatalogueLockCoordinator locks;
    private final ProductVariantRepository variants;
    private final CatalogueAvailabilityService availability;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public VariantQuantityService(CatalogueLockCoordinator locks, ProductVariantRepository variants,
                                  CatalogueAvailabilityService availability, CatalogueAuditWriter audit,
                                  CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.variants = variants;
        this.availability = availability;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome setQuantity(String actorId, UUID commandId, String variantId,
                                                        long expectedVersion, String quantity) {
        long id = CatalogueValues.parseId("variantId", variantId);
        BigInteger value = CatalogueValues.parseQuantity("quantity", quantity);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("quantity", value.toString());
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
            CatalogueLockCoordinator.requireVersion(variant.getVersion(), expectedVersion);
            if (variant.getRemainingUnsold().equals(value)) {
                return result(commandId, variant, false);
            }
            if (!product.hasEverBeenPublished()) {
                return update(actorId, commandId, variant, value, BigInteger.ZERO);
            }
            return availability.withGuard(List.of(id), Instant.now(),
                    snapshots -> update(actorId, commandId, variant, value, snapshots.get(id).heldQuantity()));
        });
    }

    private CatalogueCommandExecutor.CommandResult update(String actorId, UUID commandId, ProductVariant variant,
                                                          BigInteger value, BigInteger validHeld) {
        BigInteger old = variant.getRemainingUnsold();
        variant.updateRemainingUnsold(value, validHeld);
        ProductVariant saved = variants.saveAndFlush(variant);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("sku", saved.getSku());
        changes.put("oldQuantity", old.toString());
        changes.put("newQuantity", value.toString());
        audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTION, "variant", saved.getId().toString(),
                saved.getVersion(), null, List.of("remainingUnsold"), changes));
        return result(commandId, saved, true);
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, ProductVariant variant,
                                                                 boolean changed) {
        ActionResult body = new ActionResult(commandId, variant.getId().toString(), "variant", ACTION,
                "UPDATED", variant.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "variant", variant.getId().toString(),
                variant.getVersion(), body);
    }
}
