package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Edits name, description and category of a product (T043, US4, FR-005/011/012/024/026).
 *
 * <ul>
 *   <li>Only these three fields: sizes, SKUs, prices and quantities have their own commands, and the DTO
 *       has no field for them (unknown fields are refused by the strict JSON reader).</li>
 *   <li>A category change locks source and target categories in ascending ID order; the target must be
 *       active. Staying in the current category is allowed even if it was deactivated meanwhile.</li>
 *   <li>Before the first publication a real change withdraws the current review (T032); afterwards the edit
 *       is direct, also while the product is hidden. Accepted holds and orders keep their own snapshot.</li>
 *   <li>Saving the same content is a no-op ({@code changed=false}) after the version check.</li>
 * </ul>
 * The caller authorizes the actor first (T007).
 */
@Service
public class UpdateProductService {

    public static final String ACTION = "product.edit";

    public record UpdateCommand(String name, String description, String categoryId) {
    }

    private final CatalogueLockCoordinator locks;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final ProductReviewInvalidationService invalidation;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public UpdateProductService(CatalogueLockCoordinator locks, ProductRepository products,
                                ProductCategoryRepository categories, ProductReviewInvalidationService invalidation,
                                CatalogueAuditWriter audit,
                                CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.products = products;
        this.categories = categories;
        this.invalidation = invalidation;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome update(String actorId, UUID commandId, String productId,
                                                   long expectedVersion, UpdateCommand command) {
        long id = CatalogueValues.parseId("productId", productId);
        long categoryId = CatalogueValues.parseId("categoryId", command.categoryId());
        String name = CatalogueValues.productName("name", command.name());
        String description = CatalogueValues.productDescription("description", command.description());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("name", name);
        request.put("description", description);
        request.put("categoryId", categoryId);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            var locked = locks.lockProductWithTarget(id, categoryId);
            Product product = locked.product();
            product.requireNotDeleted();
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            boolean moving = !product.getCategoryId().equals(categoryId);
            if (moving) {
                // Already locked together with the source category above.
                ProductCategory target = categories.findById(categoryId)
                        .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.CATEGORY_NOT_FOUND));
                if (!target.isActive()) {
                    throw new CatalogueException(CatalogueErrorCode.CATEGORY_NOT_FOUND);
                }
            }
            List<String> changed = new ArrayList<>();
            if (!product.getName().equals(name)) {
                changed.add("name");
            }
            if (!Objects.equals(product.getDescription(), description)) {
                changed.add("description");
            }
            if (moving) {
                changed.add("categoryId");
            }
            if (!product.updateContent(name, description, categoryId)) {
                return result(commandId, product, false);
            }
            invalidation.invalidateBeforeFirstPublication(product, locked.variants(), Map.of());
            Product saved = products.saveAndFlush(product);
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("contentVersion", saved.getContentVersion());
            changes.put("categoryId", saved.getCategoryId().toString());
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTION, "product",
                    saved.getId().toString(), saved.getVersion(), null, changed, changes));
            return result(commandId, saved, true);
        });
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, Product product, boolean changed) {
        ActionResult body = new ActionResult(commandId, product.getId().toString(), "product", ACTION,
                product.isPublished() ? "PUBLISHED" : "UNPUBLISHED", product.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "product", product.getId().toString(),
                product.getVersion(), body);
    }
}
