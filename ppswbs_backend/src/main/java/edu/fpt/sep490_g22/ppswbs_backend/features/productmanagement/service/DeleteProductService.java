package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Confirmed soft delete (T061, US6, FR-022–025): any not-deleted product becomes DELETED.
 *
 * <ul>
 *   <li>Sets {@code deletedAt/deletedBy} and makes publication ineffective; identity, SKUs, foreign keys,
 *       reviews, proposals, applications, images and media references are kept. There is no restore.</li>
 *   <li>Every later product command (edit, submit, decide, apply, publish, sale enable, quantity) is refused
 *       with {@code PRODUCT_DELETED}: each takes the product lock and checks it first, and the same lock
 *       serializes delete with those commands and with new holds (FR-025).</li>
 *   <li>Quantity is not deducted or restored and accepted holds/orders are not touched (FR-024).</li>
 *   <li>Deleting again is a no-op ({@code changed=false}) without version check; a cancelled confirmation
 *       sends no command at all.</li>
 * </ul>
 * The caller authorizes the actor first (T007).
 */
@Service
public class DeleteProductService {

    public static final String ACTION = "product.delete";

    private final CatalogueLockCoordinator locks;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public DeleteProductService(CatalogueLockCoordinator locks, CatalogueAuditWriter audit,
                                CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome delete(String actorId, UUID commandId, String productId,
                                                   long expectedVersion) {
        long id = CatalogueValues.parseId("productId", productId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            Product product = locks.lockProduct(id).product();
            if (product.isDeleted()) {
                return result(commandId, product, false);
            }
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            boolean wasPublished = product.isPublished();
            product.softDelete(actorId, CatalogueValues.toStoredInstant(Instant.now()));
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTION, "product",
                    product.getId().toString(), product.getVersion(), null, List.of("deletedAt", "published"),
                    Map.of("wasPublished", wasPublished)));
            return result(commandId, product, true);
        });
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, Product product, boolean changed) {
        ActionResult body = new ActionResult(commandId, product.getId().toString(), "product", ACTION,
                "DELETED", null, changed);
        return new CatalogueCommandExecutor.CommandResult(200, "product", product.getId().toString(), null, body);
    }
}
