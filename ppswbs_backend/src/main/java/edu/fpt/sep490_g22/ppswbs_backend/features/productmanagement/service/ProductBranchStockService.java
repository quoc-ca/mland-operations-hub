package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quantity of each size per branch (US10, FR-047–FR-050, FR-055; Session 2026-11-08).
 *
 * <p>Invariant kept here: when a size has branch rows, {@code catalogue_variants.remaining_unsold} equals their sum.
 * FE-08 still reads and deducts that total and holds per size (not per branch) until it sends the branch (FR-053), so
 * a new total may not go below the size's valid held quantity, read under the FE-08 guard like the old quantity command.
 * A size without rows (data from before the migration on a database without the default branch) keeps its old total
 * until a branch is added.
 */
@Service
public class ProductBranchStockService {

    public static final String ADD_ACTION = "product.branch.add";
    public static final String QUANTITY_ACTION = "variant.branch.quantity";
    public static final String SALE_ACTION = "product.branch.sale";

    /** Quantity of one size at a branch, as sent by the client. */
    public record SizeQuantity(Integer size, String quantity) {
    }

    /** One branch of a create or add-branch command. */
    public record BranchInput(String locationId, List<SizeQuantity> quantities) {
        public BranchInput {
            quantities = quantities == null ? List.of() : List.copyOf(quantities);
        }
    }

    /** Validated branch input: branch id and quantity per size. */
    public record ValidBranch(long locationId, Map<Integer, BigInteger> quantities) {
    }

    private final VariantBranchStockRepository stocks;
    private final ProductVariantRepository variants;
    private final CatalogueLockCoordinator locks;
    private final CatalogueAvailabilityService availability;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;
    private final CatalogueBranchScope scope;
    private final ObjectProvider<SellingLocationPort> locations;

    public ProductBranchStockService(VariantBranchStockRepository stocks, ProductVariantRepository variants,
                                     CatalogueLockCoordinator locks, CatalogueAvailabilityService availability,
                                     CatalogueAuditWriter audit, CatalogueCommandExecutor executor,
                                     CatalogueBranchScope scope, ObjectProvider<SellingLocationPort> locations) {
        this.stocks = stocks;
        this.variants = variants;
        this.locks = locks;
        this.availability = availability;
        this.audit = audit;
        this.executor = executor;
        this.scope = scope;
        this.locations = locations;
    }

    // ---- branches of the canonical list ----

    /** Active branches by id; a missing or failing source is {@code LOCATION_DEPENDENCY_UNAVAILABLE}. */
    public Map<Long, SellingLocation> activeBranches() {
        SellingLocationPort source = locations.getIfAvailable();
        if (source == null) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        List<SellingLocation> list;
        try {
            list = source.activeSellingLocations();
        } catch (RuntimeException e) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        if (list == null) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        Map<Long, SellingLocation> byId = new LinkedHashMap<>();
        list.forEach(location -> byId.put(location.id(), location));
        return byId;
    }

    /** Names of active branches, or an empty map when the source is unavailable (reads must keep working). */
    public Map<Long, SellingLocation> activeBranchesOrEmpty() {
        try {
            return activeBranches();
        } catch (CatalogueException e) {
            return Map.of();
        }
    }

    /**
     * Validates the branches of a create or add-branch command for the given sizes: at least one, each once, active,
     * in the actor's scope, a quantity for each size (missing = 0) and no unknown size.
     */
    public List<ValidBranch> validate(List<BranchInput> inputs, Set<Integer> sizes) {
        if (inputs == null || inputs.isEmpty()) {
            throw CatalogueException.invalidField("branches", "Choose at least one branch.");
        }
        Map<Long, SellingLocation> active = activeBranches();
        List<ValidBranch> valid = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            BranchInput input = inputs.get(i);
            String field = "branches[" + i + "]";
            long locationId = CatalogueValues.parseId(field + ".locationId", input.locationId());
            if (!seen.add(locationId)) {
                throw CatalogueException.invalidField(field + ".locationId", "Each branch may be chosen once.");
            }
            if (!active.containsKey(locationId)) {
                throw new CatalogueException(CatalogueErrorCode.BRANCH_NOT_FOUND);
            }
            Map<Integer, BigInteger> quantities = new HashMap<>();
            sizes.forEach(size -> quantities.put(size, BigInteger.ZERO));
            for (int j = 0; j < input.quantities().size(); j++) {
                SizeQuantity entry = input.quantities().get(j);
                String entryField = field + ".quantities[" + j + "]";
                if (entry.size() == null || !sizes.contains(entry.size())) {
                    throw CatalogueException.invalidField(entryField + ".size", "Not a size of this product.");
                }
                quantities.put(entry.size(), CatalogueValues.parseQuantity(entryField + ".quantity", entry.quantity()));
            }
            valid.add(new ValidBranch(locationId, quantities));
        }
        scope.requireBranches(valid.stream().map(ValidBranch::locationId).toList());
        return valid;
    }

    /** Total quantity per size of validated branches (the size's initial total at creation). */
    public static Map<Integer, BigInteger> totals(List<ValidBranch> branches) {
        Map<Integer, BigInteger> totals = new HashMap<>();
        branches.forEach(branch -> branch.quantities().forEach((size, quantity) -> totals.merge(size, quantity, BigInteger::add)));
        return totals;
    }

    /** Rows of a product being created; runs in the creating transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createRows(List<ProductVariant> savedVariants, List<ValidBranch> branches) {
        List<VariantBranchStock> rows = new ArrayList<>();
        for (ValidBranch branch : branches) {
            for (ProductVariant variant : savedVariants) {
                rows.add(VariantBranchStock.create(variant.getId(), branch.locationId(),
                        branch.quantities().getOrDefault(variant.getRingSize(), BigInteger.ZERO)));
            }
        }
        stocks.saveAllAndFlush(rows);
    }

    // ---- commands ----

    /**
     * FR-049: start selling the product at another branch with a quantity per size. A size that had no branch yet
     * (before the migration) brings its old total into this first branch when no quantity is sent for it.
     */
    public CatalogueCommandExecutor.Outcome addBranch(String actorId, UUID commandId, String productId,
                                                      long expectedVersion, BranchInput input) {
        long id = CatalogueValues.parseId("productId", productId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("branch", input);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ADD_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            List<ProductVariant> sizes = locked.variants();
            Set<Integer> sizeSet = sizes.stream().map(ProductVariant::getRingSize).collect(Collectors.toSet());
            List<VariantBranchStock> existing = stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(ids(sizes));
            boolean firstBranch = existing.isEmpty();
            BranchInput effective = firstBranch ? withOldTotals(input, sizes) : input;
            ValidBranch branch = validate(List.of(effective), sizeSet).getFirst();
            if (existing.stream().anyMatch(row -> row.getLocationId() == branch.locationId())) {
                throw new CatalogueException(CatalogueErrorCode.BRANCH_ALREADY_ADDED);
            }
            return guarded(product, sizes, held -> {
                List<VariantBranchStock> added = new ArrayList<>();
                for (ProductVariant variant : sizes) {
                    BigInteger quantity = branch.quantities().getOrDefault(variant.getRingSize(), BigInteger.ZERO);
                    added.add(VariantBranchStock.create(variant.getId(), branch.locationId(), quantity));
                    BigInteger newTotal = firstBranch ? quantity : variant.getRemainingUnsold().add(quantity);
                    variant.updateRemainingUnsold(newTotal, held.getOrDefault(variant.getId(), BigInteger.ZERO));
                }
                stocks.saveAllAndFlush(added);
                variants.saveAllAndFlush(sizes);
                Map<String, Object> changes = new LinkedHashMap<>();
                changes.put("locationId", Long.toString(branch.locationId()));
                changes.put("firstBranch", firstBranch);
                changes.put("quantities", quantitiesBySize(branch));
                audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ADD_ACTION, "product", Long.toString(id),
                        product.getVersion(), null, List.of("branches"), changes));
                return productResult(commandId, ADD_ACTION, product, "ADDED", true);
            });
        });
    }

    /** FR-049: quantity of one size at one branch; the size's total follows. */
    public CatalogueCommandExecutor.Outcome setQuantity(String actorId, UUID commandId, String variantId,
                                                        String locationId, long expectedVersion, String quantity) {
        long id = CatalogueValues.parseId("variantId", variantId);
        long location = CatalogueValues.parseId("locationId", locationId);
        BigInteger value = CatalogueValues.parseQuantity("quantity", quantity);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", id);
        request.put("locationId", location);
        request.put("expectedVersion", expectedVersion);
        request.put("quantity", value.toString());
        var key = new CatalogueCommandExecutor.CommandKey(actorId, QUANTITY_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            scope.requireBranches(List.of(location));
            Long productId = variants.findProductIdById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            var locked = locks.lockProduct(productId);
            Product product = locked.product();
            product.requireNotDeleted();
            ProductVariant variant = locked.variants().stream().filter(v -> v.getId() == id).findFirst()
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            VariantBranchStock row = VariantBranchStock.require(stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(id))
                    .stream().filter(candidate -> candidate.getLocationId() == location).findFirst().orElse(null));
            CatalogueLockCoordinator.requireVersion(row.getVersion(), expectedVersion);
            if (row.getRemainingUnsold().equals(value)) {
                return stockResult(commandId, row, false);
            }
            if (product.hasEverBeenPublished() && availability.tracksHoldsPerBranch()) {
                // FR-053: holds are per branch, so the new branch quantity may not go below the holds at this branch.
                BranchVariant stockKey = new BranchVariant(id, location);
                Instant asOf = Instant.now();
                return availability.withBranchGuard(List.of(stockKey), asOf, snapshots -> {
                    if (value.compareTo(snapshots.get(stockKey).heldQuantity()) < 0) {
                        throw new CatalogueException(CatalogueErrorCode.QUANTITY_BELOW_HELD);
                    }
                    BigInteger heldOfSize = availability.readSnapshots(List.of(id), asOf).get(id).heldQuantity();
                    return applyQuantity(actorId, commandId, variant, row, value, heldOfSize);
                });
            }
            return guarded(product, List.of(variant), held -> applyQuantity(actorId, commandId, variant, row, value,
                    held.getOrDefault(id, BigInteger.ZERO)));
        });
    }

    /**
     * The old per-size quantity command, kept for clients that do not send a branch: allowed while the size is sold at
     * no branch (old total) or exactly one branch (that branch changes); several branches → {@code BRANCH_REQUIRED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean legacyQuantityTouchesBranch(ProductVariant variant, BigInteger newTotal) {
        List<VariantBranchStock> rows = stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variant.getId()));
        if (rows.isEmpty()) {
            return false;
        }
        if (rows.size() > 1) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_REQUIRED);
        }
        scope.requireBranches(List.of(rows.getFirst().getLocationId()));
        rows.getFirst().setRemainingUnsold(newTotal);
        stocks.saveAndFlush(rows.getFirst());
        return true;
    }

    /** FR-049: stop or resume selling every size of the product at one branch. */
    public CatalogueCommandExecutor.Outcome setSaleEnabled(String actorId, UUID commandId, String productId,
                                                           String locationId, boolean enabled) {
        long id = CatalogueValues.parseId("productId", productId);
        long location = CatalogueValues.parseId("locationId", locationId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("locationId", location);
        request.put("saleEnabled", enabled);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, SALE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            scope.requireBranches(List.of(location));
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            List<VariantBranchStock> rows = stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(ids(locked.variants()))
                    .stream().filter(row -> row.getLocationId() == location).toList();
            if (rows.isEmpty()) {
                throw new CatalogueException(CatalogueErrorCode.BRANCH_STOCK_NOT_FOUND);
            }
            boolean changed = false;
            for (VariantBranchStock row : rows) {
                changed |= row.setSaleEnabled(enabled);
            }
            if (changed) {
                stocks.saveAllAndFlush(rows);
                Map<String, Object> changes = new LinkedHashMap<>();
                changes.put("locationId", Long.toString(location));
                changes.put("saleEnabled", enabled);
                audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, SALE_ACTION, "product", Long.toString(id),
                        product.getVersion(), null, List.of("branchSaleEnabled"), changes));
            }
            return productResult(commandId, SALE_ACTION, product, enabled ? "SALE_ENABLED" : "SALE_DISABLED", changed);
        });
    }

    /**
     * An accepted FE-08 sale deducted from the size's total: until FE-08 sends the branch (FR-053) the same quantity
     * is taken from the size's branch rows in ascending branch id order, so the total stays the sum of the rows. The
     * caller holds the size's lock. Returns the branch ids touched (for the audit).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> deductSaleWithoutBranch(long variantId, BigInteger quantity) {
        List<VariantBranchStock> rows = stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variantId));
        List<String> touched = new ArrayList<>();
        BigInteger left = quantity;
        for (VariantBranchStock row : rows) {
            if (left.signum() <= 0) {
                break;
            }
            BigInteger taken = row.takeUpTo(left);
            if (taken.signum() > 0) {
                left = left.subtract(taken);
                touched.add(row.getLocationId() + ":" + taken);
            }
        }
        if (!rows.isEmpty()) {
            stocks.saveAllAndFlush(rows);
        }
        return touched;
    }

    // ---- FR-053 support for the sales facade ----

    /** Branch rows of the sizes: size id → (branch id → row). A size without rows is absent (not yet assigned). */
    public Map<Long, Map<Long, VariantBranchStock>> stocksBySize(Collection<Long> variantIds) {
        Map<Long, Map<Long, VariantBranchStock>> result = new HashMap<>();
        if (variantIds.isEmpty()) {
            return result;
        }
        for (VariantBranchStock row : stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(variantIds)) {
            result.computeIfAbsent(row.getVariantId(), ignored -> new LinkedHashMap<>()).put(row.getLocationId(), row);
        }
        return result;
    }

    /** Saves a branch row changed by an accepted sale; the caller holds the size lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public VariantBranchStock saveSaleDeduction(VariantBranchStock row) {
        return stocks.saveAndFlush(row);
    }

    // ---- helpers ----

    private CatalogueCommandExecutor.CommandResult applyQuantity(String actorId, UUID commandId, ProductVariant variant,
                                                                 VariantBranchStock row, BigInteger value, BigInteger held) {
        BigInteger old = row.getRemainingUnsold();
        BigInteger newTotal = variant.getRemainingUnsold().subtract(old).add(value);
        variant.updateRemainingUnsold(newTotal, held);
        row.setRemainingUnsold(value);
        VariantBranchStock saved = stocks.saveAndFlush(row);
        variants.saveAndFlush(variant);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("sku", variant.getSku());
        changes.put("locationId", Long.toString(row.getLocationId()));
        changes.put("oldQuantity", old.toString());
        changes.put("newQuantity", value.toString());
        changes.put("newTotal", newTotal.toString());
        audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, QUANTITY_ACTION, "variant", variant.getId().toString(),
                variant.getVersion(), null, List.of("branchRemainingUnsold"), changes));
        return stockResult(commandId, saved, true);
    }

    /** Runs {@code work} with the valid held quantity per size: a proven zero before publication, else under the guard. */
    private CatalogueCommandExecutor.CommandResult guarded(Product product, List<ProductVariant> sizes,
                                                           java.util.function.Function<Map<Long, BigInteger>, CatalogueCommandExecutor.CommandResult> work) {
        if (!product.hasEverBeenPublished()) {
            return work.apply(Map.of());
        }
        List<Long> ids = ids(sizes);
        return availability.withGuard(ids, Instant.now(), snapshots -> {
            Map<Long, BigInteger> held = new HashMap<>();
            snapshots.forEach((variantId, snapshot) -> held.put(variantId, snapshot.heldQuantity()));
            return work.apply(held);
        });
    }

    private static BranchInput withOldTotals(BranchInput input, List<ProductVariant> sizes) {
        Set<Integer> sent = input.quantities().stream().map(SizeQuantity::size).collect(Collectors.toSet());
        List<SizeQuantity> quantities = new ArrayList<>(input.quantities());
        sizes.stream().filter(variant -> !sent.contains(variant.getRingSize()))
                .forEach(variant -> quantities.add(new SizeQuantity(variant.getRingSize(), variant.getRemainingUnsold().toString())));
        return new BranchInput(input.locationId(), quantities);
    }

    private static Map<String, String> quantitiesBySize(ValidBranch branch) {
        Map<String, String> bySize = new LinkedHashMap<>();
        branch.quantities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> bySize.put(entry.getKey().toString(), entry.getValue().toString()));
        return bySize;
    }

    private static List<Long> ids(List<ProductVariant> sizes) {
        return sizes.stream().map(ProductVariant::getId).sorted(Comparator.naturalOrder()).toList();
    }

    private static CatalogueCommandExecutor.CommandResult productResult(UUID commandId, String action, Product product,
                                                                        String status, boolean changed) {
        ActionResult body = new ActionResult(commandId, product.getId().toString(), "product", action, status,
                product.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "product", product.getId().toString(),
                product.getVersion(), body);
    }

    private static CatalogueCommandExecutor.CommandResult stockResult(UUID commandId, VariantBranchStock row, boolean changed) {
        ActionResult body = new ActionResult(commandId, row.getVariantId().toString(), "variant", QUANTITY_ACTION,
                "UPDATED", row.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "variant", row.getVariantId().toString(),
                row.getVersion(), body);
    }
}
