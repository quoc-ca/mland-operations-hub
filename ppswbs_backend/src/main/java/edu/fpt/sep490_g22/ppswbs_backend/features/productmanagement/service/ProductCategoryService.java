package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CategoryDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Category management (T076/T077, US7, FR-029/030).
 *
 * <ul>
 *   <li>Names are trimmed, 1–200 code points, unique system-wide (inactive categories included) by the
 *       NFC/lower-case key; the display name keeps case and accents. A concurrent duplicate is caught by the
 *       unique key and reported as {@code DUPLICATE_CATEGORY_NAME}. A new category is active.</li>
 *   <li>A category is never deleted: a referenced one is deactivated instead.</li>
 *   <li>Impact lists the affected products with the current category version; activation must be confirmed
 *       against that version. The activation takes the category lock, which every product update,
 *       publication and new hold takes first, so they are serialized. Deactivation removes the products from
 *       public listing and new sale without changing {@code product.published}, history or accepted holds;
 *       reactivation makes only products that are still published and eligible visible again.</li>
 * </ul>
 * The caller authorizes the actor first (T007): STAFF or MANAGER with {@code category.manage}.
 */
@Service
public class ProductCategoryService {

    public static final String CREATE_ACTION = "category.create";
    public static final String UPDATE_ACTION = "category.update";
    public static final String ACTIVATION_ACTION = "category.activation";

    private final ProductCategoryRepository categories;
    private final CatalogueLockCoordinator locks;
    private final ManagementProductQueryService productQueries;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public ProductCategoryService(ProductCategoryRepository categories, CatalogueLockCoordinator locks,
                                  ManagementProductQueryService productQueries, CatalogueAuditWriter audit,
                                  CatalogueCommandExecutor executor) {
        this.categories = categories;
        this.locks = locks;
        this.productQueries = productQueries;
        this.audit = audit;
        this.executor = executor;
    }

    @Transactional(readOnly = true)
    public CategoryDtos.CategoryPage list(Integer page) {
        int pageNumber = requirePage(page);
        Page<ProductCategory> found = categories.findAllByOrderByNameAscIdAsc(
                PageRequest.of(pageNumber, ManagementProductDtos.PAGE_SIZE));
        return new CategoryDtos.CategoryPage(found.getContent().stream().map(ProductCategoryService::view).toList(),
                pageNumber, ManagementProductDtos.PAGE_SIZE, Long.toString(found.getTotalElements()));
    }

    public CatalogueCommandExecutor.Outcome create(String actorId, UUID commandId, String name, String description) {
        ProductCategory candidate = ProductCategory.create(name, description);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", candidate.getName());
        request.put("description", candidate.getDescription());
        var key = new CatalogueCommandExecutor.CommandKey(actorId, CREATE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            if (categories.existsByNameKey(candidate.getNameKey())) {
                throw new CatalogueException(CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
            }
            ProductCategory saved = saveUnique(ProductCategory.create(name, description));
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CREATE_ACTION, "category",
                    saved.getId().toString(), saved.getVersion(), null, List.of("name", "description", "active"),
                    Map.of("name", saved.getName(), "active", true)));
            return result(commandId, saved, CREATE_ACTION, 201, true);
        });
    }

    public CatalogueCommandExecutor.Outcome update(String actorId, UUID commandId, String categoryId,
                                                   long expectedVersion, String name, String description) {
        long id = CatalogueValues.parseId("categoryId", categoryId);
        ProductCategory candidate = ProductCategory.create(name, description);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("categoryId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("name", candidate.getName());
        request.put("description", candidate.getDescription());
        var key = new CatalogueCommandExecutor.CommandKey(actorId, UPDATE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            ProductCategory category = lockCategory(id);
            CatalogueLockCoordinator.requireVersion(category.getVersion(), expectedVersion);
            boolean renamed = !Arrays.equals(category.getNameKey(), candidate.getNameKey());
            if (renamed && categories.existsByNameKey(candidate.getNameKey())) {
                throw new CatalogueException(CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
            }
            List<String> changed = new ArrayList<>();
            if (!category.getName().equals(candidate.getName())) {
                changed.add("name");
            }
            if (!Objects.equals(category.getDescription(), candidate.getDescription())) {
                changed.add("description");
            }
            if (changed.isEmpty()) {
                return result(commandId, category, UPDATE_ACTION, 200, false);
            }
            category.rename(name);
            category.describe(description);
            ProductCategory saved = saveUnique(category);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, UPDATE_ACTION, "category",
                    saved.getId().toString(), saved.getVersion(), null, changed, Map.of("name", saved.getName())));
            return result(commandId, saved, UPDATE_ACTION, 200, true);
        });
    }

    /** Read before confirming an activation change: affected products and the current version. */
    @Transactional(readOnly = true)
    public CategoryDtos.CategoryImpact impact(String categoryId, Integer page) {
        long id = CatalogueValues.parseId("categoryId", categoryId);
        ProductCategory category = categories.findById(id)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.CATEGORY_NOT_FOUND));
        var affected = productQueries.list(null, Long.toString(id), null, requirePage(page));
        return new CategoryDtos.CategoryImpact(category.getId().toString(), category.getVersion(), category.isActive(),
                affected);
    }

    public CatalogueCommandExecutor.Outcome setActive(String actorId, UUID commandId, String categoryId,
                                                      long expectedVersion, boolean active) {
        long id = CatalogueValues.parseId("categoryId", categoryId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("categoryId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("active", active);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ACTIVATION_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            ProductCategory category = lockCategory(id);
            if (category.isActive() == active) {
                return result(commandId, category, ACTIVATION_ACTION, 200, false);
            }
            CatalogueLockCoordinator.requireVersion(category.getVersion(), expectedVersion);
            category.setActive(active);
            ProductCategory saved = categories.saveAndFlush(category);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTIVATION_ACTION, "category",
                    saved.getId().toString(), saved.getVersion(), null, List.of("active"), Map.of("active", active)));
            return result(commandId, saved, ACTIVATION_ACTION, 200, true);
        });
    }

    private ProductCategory lockCategory(long id) {
        return locks.lock(new CatalogueLockCoordinator.LockRequest(Set.of(id), Set.of(), Set.of()))
                .categories().get(id);
    }

    /** The unique name key also catches a duplicate created concurrently after the existence check. */
    private ProductCategory saveUnique(ProductCategory category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new CatalogueException(CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
        }
    }

    private static int requirePage(Integer page) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        return pageNumber;
    }

    static ManagementProductDtos.Category view(ProductCategory category) {
        return new ManagementProductDtos.Category(category.getId().toString(), category.getName(),
                category.getDescription(), category.isActive(), category.getVersion());
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, ProductCategory category,
                                                                 String action, int status, boolean changed) {
        ActionResult body = new ActionResult(commandId, category.getId().toString(), "category", action,
                category.isActive() ? "ACTIVE" : "INACTIVE", category.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(status, "category", category.getId().toString(),
                category.getVersion(), body);
    }
}
