package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AcceptedSaleResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchAcceptedSaleResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchStockFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException.Reason;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleVariantFact;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of {@link CatalogueSalesFacade} for FE-08 (T048/T062, FR-008/020/023–025).
 *
 * <ul>
 *   <li>Locks categories → products → all sizes of those products in ascending ID order through
 *       {@link CatalogueLockCoordinator}; IDs are read with scalar queries first and re-checked after locking.</li>
 *   <li>A new-sale context requires public eligibility and a sale-enabled size; a historical context only
 *       requires the sizes to exist (hidden, disabled and deleted products are allowed).</li>
 *   <li>{@code recordAcceptedSale} is allowed only for a size locked by an enclosing context of this thread,
 *       is idempotent per operation reference through {@code catalogue_command_receipts} (actor
 *       {@value #CONSUMER}, action {@value #SALE_ACTION}) and writes a business audit event.</li>
 *   <li>Internal errors are translated to {@link CatalogueSalesException}; exceptions thrown by the caller's
 *       callback pass through unchanged.</li>
 * </ul>
 * Creating, settling or releasing holds, orders and payments stays in FE-08.
 */
@Service
public class CatalogueSalesService implements CatalogueSalesFacade {

    public static final String CONSUMER = "readyringsales";
    public static final String SALE_ACTION = "sale.accepted";
    static final int MAX_OWNER_REF_LENGTH = 128;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Deque<Set<Long>>> LOCKED_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    private final CatalogueLockCoordinator locks;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CatalogueMediaRepository media;
    private final PublicCatalogueQueryRepository publicQueries;
    private final CatalogueAvailabilityService availability;
    private final MediaReferenceService mediaReferences;
    private final CatalogueCommandReceiptRepository receipts;
    private final CatalogueAuditWriter audit;
    private final ProductBranchStockService branchStock;

    public CatalogueSalesService(CatalogueLockCoordinator locks, ProductRepository products,
                                 ProductVariantRepository variants, ProductImageRepository images,
                                 CatalogueMediaRepository media, PublicCatalogueQueryRepository publicQueries,
                                 CatalogueAvailabilityService availability, MediaReferenceService mediaReferences,
                                 CatalogueCommandReceiptRepository receipts, CatalogueAuditWriter audit,
                                 ProductBranchStockService branchStock) {
        this.branchStock = branchStock;
        this.locks = locks;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.media = media;
        this.publicQueries = publicQueries;
        this.availability = availability;
        this.mediaReferences = mediaReferences;
        this.receipts = receipts;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartSaleFact> readSaleFacts(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty() || variantIds.stream().anyMatch(Objects::isNull)) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "At least one size is required.");
        }
        return translated(() -> {
            Set<Long> ids = new TreeSet<>(variantIds);
            Map<Long, ProductVariant> sizes = variants.findAllById(ids).stream()
                    .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
            List<Long> missing = ids.stream().filter(id -> !sizes.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new CatalogueSalesException(Reason.NOT_FOUND, "Some sizes do not exist.", missing);
            }
            Set<Long> productIds = sizes.values().stream().map(ProductVariant::getProductId)
                    .collect(Collectors.toCollection(TreeSet::new));
            Map<Long, Product> owners = products.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            Set<Long> eligibleProducts = new HashSet<>(publicQueries.findEligibleIds(productIds));
            List<Long> eligible = ids.stream()
                    .filter(id -> eligibleProducts.contains(sizes.get(id).getProductId()) && sizes.get(id).isSaleEnabled())
                    .toList();
            Map<Long, HeldSnapshot> held = availability.readSnapshots(eligible, now());

            List<CartSaleFact> facts = new ArrayList<>();
            for (Long id : ids) {
                ProductVariant size = sizes.get(id);
                Product product = owners.get(size.getProductId());
                boolean saleEligible = held.containsKey(id);
                BigInteger available = saleEligible
                        ? size.getRemainingUnsold().subtract(held.get(id).heldQuantity()).max(BigInteger.ZERO)
                        : BigInteger.ZERO;
                facts.add(new CartSaleFact(id, product.getId(), product.getName(), size.getSku(), size.getRingSize(),
                        size.getAppliedPrice(), SaleVariantFact.CURRENCY, saleEligible, available));
            }
            return List.copyOf(facts);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withLockedSaleContext(Collection<Long> variantIds, Function<SaleContext, T> work) {
        Objects.requireNonNull(work, "work");
        SaleContext context = translated(() -> {
            SaleContext locked = lockContext(variantIds, null);
            List<Long> notForSale = locked.variants().stream()
                    .filter(fact -> fact.productDeleted() || !fact.publiclyEligible() || !fact.saleEnabled())
                    .map(SaleVariantFact::variantId)
                    .toList();
            if (!notForSale.isEmpty()) {
                throw new CatalogueSalesException(Reason.NOT_FOR_SALE, "Some sizes are not open for sale.", notForSale);
            }
            return locked;
        });
        return runInScope(context, work);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withLockedHistoricalContext(String acceptedTransactionRef, Collection<Long> variantIds,
                                             Function<SaleContext, T> work) {
        Objects.requireNonNull(work, "work");
        if (acceptedTransactionRef == null || acceptedTransactionRef.isBlank()) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "An accepted transaction reference is required.");
        }
        SaleContext context = translated(() -> lockContext(variantIds, acceptedTransactionRef));
        return runInScope(context, work);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AcceptedSaleResult recordAcceptedSale(UUID operationRef, long variantId, BigInteger quantity) {
        if (operationRef == null || quantity == null || quantity.signum() <= 0 || quantity.toString().length() > 19) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST,
                    "An operation reference and a positive quantity are required.");
        }
        if (LOCKED_SCOPES.get().stream().noneMatch(scope -> scope.contains(variantId))) {
            throw new CatalogueSalesException(Reason.NO_LOCKED_CONTEXT,
                    "The size must be locked by a sale context first.", List.of(variantId));
        }
        return translated(() -> record(operationRef, variantId, quantity));
    }

    // ---- FR-053: sale per size × branch ----

    @Override
    @Transactional(readOnly = true)
    public List<BranchCartSaleFact> readBranchSaleFacts(Collection<BranchVariant> keys) {
        List<BranchVariant> ordered = requireKeys(keys);
        return translated(() -> {
            Set<Long> ids = sizesOf(ordered);
            Map<Long, ProductVariant> sizes = variants.findAllById(ids).stream()
                    .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
            List<Long> missing = ids.stream().filter(id -> !sizes.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new CatalogueSalesException(Reason.NOT_FOUND, "Some sizes do not exist.", missing);
            }
            Set<Long> productIds = sizes.values().stream().map(ProductVariant::getProductId)
                    .collect(Collectors.toCollection(TreeSet::new));
            Map<Long, Product> owners = products.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            Set<Long> eligibleProducts = new HashSet<>(publicQueries.findEligibleIds(productIds));
            Set<Long> activeBranches = branchStock.activeBranches().keySet();
            Map<BranchVariant, BranchStockFact> stock = branchFacts(ordered, states(sizes), activeBranches);

            List<BranchVariant> eligible = ordered.stream()
                    .filter(key -> eligibleProducts.contains(sizes.get(key.variantId()).getProductId())
                            && sizes.get(key.variantId()).isSaleEnabled() && sells(stock.get(key)))
                    .toList();
            Instant asOf = now();
            Map<Long, HeldSnapshot> heldBySize = availability.readSnapshots(sizesOf(eligible), asOf);
            Map<BranchVariant, HeldSnapshot> heldByBranch = availability.readBranchSnapshots(eligible, asOf);

            List<BranchCartSaleFact> facts = new ArrayList<>();
            for (BranchVariant key : ordered) {
                ProductVariant size = sizes.get(key.variantId());
                Product product = owners.get(size.getProductId());
                BranchStockFact fact = stock.get(key);
                boolean saleEligible = heldByBranch.containsKey(key);
                BigInteger available = BigInteger.ZERO;
                if (saleEligible) {
                    BigInteger atBranch = fact.remainingUnsold().subtract(heldByBranch.get(key).heldQuantity());
                    BigInteger ofSize = size.getRemainingUnsold().subtract(heldBySize.get(size.getId()).heldQuantity());
                    available = atBranch.min(ofSize).max(BigInteger.ZERO);
                }
                facts.add(new BranchCartSaleFact(size.getId(), key.locationId(), product.getId(), product.getName(),
                        size.getSku(), size.getRingSize(), size.getAppliedPrice(), SaleVariantFact.CURRENCY,
                        saleEligible, available, fact.assignedToBranches()));
            }
            return List.copyOf(facts);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withLockedBranchSaleContext(Collection<BranchVariant> keys, Function<SaleContext, T> work) {
        Objects.requireNonNull(work, "work");
        List<BranchVariant> ordered = requireKeys(keys);
        SaleContext context = translated(() -> {
            SaleContext locked = lockContext(sizesOf(ordered), null);
            Set<Long> notForSale = new TreeSet<>();
            locked.variants().stream()
                    .filter(fact -> fact.productDeleted() || !fact.publiclyEligible() || !fact.saleEnabled())
                    .forEach(fact -> notForSale.add(fact.variantId()));
            List<BranchStockFact> stock = List.copyOf(branchFacts(ordered, sizesById(locked),
                    branchStock.activeBranches().keySet()).values());
            stock.stream().filter(fact -> !sells(fact)).forEach(fact -> notForSale.add(fact.variantId()));
            if (!notForSale.isEmpty()) {
                throw new CatalogueSalesException(Reason.NOT_FOR_SALE, "Some sizes are not open for sale at this branch.",
                        List.copyOf(notForSale));
            }
            return new SaleContext(locked.asOf(), null, locked.variants(), stock);
        });
        return runInScope(context, work);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withLockedHistoricalBranchContext(String acceptedTransactionRef, Collection<BranchVariant> keys,
                                                   Function<SaleContext, T> work) {
        Objects.requireNonNull(work, "work");
        if (acceptedTransactionRef == null || acceptedTransactionRef.isBlank()) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "An accepted transaction reference is required.");
        }
        List<BranchVariant> ordered = requireKeys(keys);
        SaleContext context = translated(() -> {
            SaleContext locked = lockContext(sizesOf(ordered), acceptedTransactionRef);
            // Settling an accepted sale must not depend on the branch list being reachable: unknown = not active.
            List<BranchStockFact> stock = List.copyOf(branchFacts(ordered, sizesById(locked),
                    branchStock.activeBranchesOrEmpty().keySet()).values());
            return new SaleContext(locked.asOf(), acceptedTransactionRef, locked.variants(), stock);
        });
        return runInScope(context, work);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BranchAcceptedSaleResult recordAcceptedSale(UUID operationRef, long variantId, long locationId,
                                                       BigInteger quantity) {
        if (operationRef == null || quantity == null || quantity.signum() <= 0 || quantity.toString().length() > 19
                || locationId <= 0) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST,
                    "An operation reference, a branch and a positive quantity are required.");
        }
        if (LOCKED_SCOPES.get().stream().noneMatch(scope -> scope.contains(variantId))) {
            throw new CatalogueSalesException(Reason.NO_LOCKED_CONTEXT,
                    "The size must be locked by a sale context first.", List.of(variantId));
        }
        return translated(() -> recordAtBranch(operationRef, variantId, locationId, quantity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void retainMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds) {
        String ref = requireOwnerRef(ownerRecordRef);
        translated(() -> {
            mediaReferences.retain(CONSUMER, ref, requireMediaIds(mediaIds), now());
            return null;
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds) {
        String ref = requireOwnerRef(ownerRecordRef);
        translated(() -> {
            mediaReferences.release(CONSUMER, ref, requireMediaIds(mediaIds), now());
            return null;
        });
    }

    // ---- locking ----

    private SaleContext lockContext(Collection<Long> requestedIds, String acceptedTransactionRef) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(Objects::isNull)) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "At least one size is required.");
        }
        Set<Long> variantIds = new TreeSet<>(requestedIds);
        Map<Long, Long> productOfVariant = new TreeMap<>();
        for (Long id : variantIds) {
            productOfVariant.put(id, variants.findProductIdById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND)));
        }
        Set<Long> productIds = new TreeSet<>(productOfVariant.values());
        Map<Long, Long> categoryOfProduct = new TreeMap<>();
        for (Long id : productIds) {
            categoryOfProduct.put(id, products.findCategoryIdById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PRODUCT_NOT_FOUND)));
        }
        CatalogueLockCoordinator.Locked locked = locks.lock(new CatalogueLockCoordinator.LockRequest(
                new TreeSet<>(categoryOfProduct.values()), productIds, Set.of()));
        for (Product product : locked.products().values()) {
            if (!categoryOfProduct.get(product.getId()).equals(product.getCategoryId())) {
                throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
            }
        }
        Map<Long, ProductVariant> sizes = variants.lockAllByProductIdIn(List.copyOf(productIds)).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (Long id : variantIds) {
            ProductVariant size = sizes.get(id);
            if (size == null || !size.getProductId().equals(productOfVariant.get(id))) {
                throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
            }
        }

        Set<Long> eligible = new HashSet<>(publicQueries.findEligibleIds(productIds));
        Map<Long, List<UUID>> readyImages = readyImages(productIds);
        List<SaleVariantFact> facts = new ArrayList<>();
        for (Long id : variantIds) {
            ProductVariant size = sizes.get(id);
            Product product = locked.products().get(size.getProductId());
            ProductCategory category = locked.categories().get(product.getCategoryId());
            boolean publiclyEligible = eligible.contains(product.getId()) && product.isPublished()
                    && !product.isDeleted() && category.isActive() && size.hasAppliedPrice();
            facts.add(new SaleVariantFact(product.getId(), product.getName(), size.getId(), size.getSku(),
                    size.getRingSize(), size.getAppliedPrice(), SaleVariantFact.CURRENCY, size.getRemainingUnsold(),
                    size.isSaleEnabled(), product.isDeleted(), publiclyEligible, product.getVersion(),
                    product.getContentVersion(), size.getVersion(), size.getAppliedSequence(),
                    readyImages.getOrDefault(product.getId(), List.of())));
        }
        return new SaleContext(now(), acceptedTransactionRef, facts);
    }

    private Map<Long, List<UUID>> readyImages(Collection<Long> productIds) {
        List<ProductImage> all = images.findByProductIdInOrderByProductIdAscDisplayPositionAsc(productIds);
        Set<UUID> ready = media.findAllById(all.stream().map(ProductImage::getMediaObjectId).toList()).stream()
                .filter(object -> object.getState() == CatalogueMediaObject.State.READY)
                .map(CatalogueMediaObject::getId)
                .collect(Collectors.toSet());
        return all.stream()
                .filter(image -> ready.contains(image.getMediaObjectId()))
                .collect(Collectors.groupingBy(ProductImage::getProductId, LinkedHashMap::new,
                        Collectors.mapping(ProductImage::getMediaObjectId, Collectors.toList())));
    }

    private static <T> T runInScope(SaleContext context, Function<SaleContext, T> work) {
        Deque<Set<Long>> scopes = LOCKED_SCOPES.get();
        scopes.push(context.variants().stream().map(SaleVariantFact::variantId).collect(Collectors.toUnmodifiableSet()));
        try {
            return work.apply(context);
        } finally {
            scopes.pop();
            if (scopes.isEmpty()) {
                LOCKED_SCOPES.remove();
            }
        }
    }

    // ---- accepted sale ----

    private AcceptedSaleResult record(UUID operationRef, long variantId, BigInteger quantity) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", variantId);
        request.put("quantity", quantity.toString());
        String digest = CatalogueCommandExecutor.digest(request);
        var previous = receipts.findByActorIdAndActionAndCommandId(CONSUMER, SALE_ACTION, operationRef);
        if (previous.isPresent()) {
            return replay(previous.get(), operationRef, variantId, quantity, digest);
        }

        ProductVariant size = variants.lockAllByIdIn(List.of(variantId)).stream().findFirst()
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
        HeldSnapshot held = availability.readSnapshots(List.of(variantId), now()).get(variantId);
        BigInteger before = size.getRemainingUnsold();
        BigInteger after = size.recordAcceptedSale(quantity, held.heldQuantity());
        ProductVariant saved = variants.saveAndFlush(size);
        // US10 interim (FR-053 pending): FE-08 does not send the branch yet, keep the branch rows summing to the total.
        List<String> branches = branchStock.deductSaleWithoutBranch(variantId, quantity);

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!branches.isEmpty()) {
            changes.put("branchesWithoutKnownSaleBranch", branches);
        }
        changes.put("operationRef", operationRef.toString());
        changes.put("sku", saved.getSku());
        changes.put("quantity", quantity.toString());
        changes.put("oldQuantity", before.toString());
        changes.put("newQuantity", after.toString());
        audit.recordSuccess(new CatalogueAuditWriter.Entry(CONSUMER, SALE_ACTION, "variant", saved.getId().toString(),
                saved.getVersion(), null, List.of("remainingUnsold"), changes));
        receipts.saveAndFlush(new CatalogueCommandReceipt(CONSUMER, SALE_ACTION, operationRef, digest, "variant",
                saved.getId().toString(), 200, saved.getVersion(), safeResult(after), now()));
        return new AcceptedSaleResult(operationRef, variantId, quantity, after, saved.getVersion(), false);
    }

    /** FR-053 deduction at a branch; the size is locked by the enclosing context. */
    private BranchAcceptedSaleResult recordAtBranch(UUID operationRef, long variantId, long locationId,
                                                    BigInteger quantity) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", variantId);
        request.put("locationId", locationId);
        request.put("quantity", quantity.toString());
        String digest = CatalogueCommandExecutor.digest(request);
        var previous = receipts.findByActorIdAndActionAndCommandId(CONSUMER, SALE_ACTION, operationRef);
        if (previous.isPresent()) {
            return replayAtBranch(previous.get(), operationRef, variantId, locationId, quantity, digest);
        }

        ProductVariant size = variants.lockAllByIdIn(List.of(variantId)).stream().findFirst()
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
        Instant asOf = now();
        HeldSnapshot heldOfSize = availability.readSnapshots(List.of(variantId), asOf).get(variantId);
        Map<Long, VariantBranchStock> rows = branchStock.stocksBySize(List.of(variantId)).getOrDefault(variantId, Map.of());
        VariantBranchStock row = null;
        BigInteger branchBefore = null;
        if (!rows.isEmpty()) {
            row = rows.get(locationId);
            if (row == null) {
                throw new CatalogueSalesException(Reason.NOT_FOR_SALE, "The size is not sold at this branch.",
                        List.of(variantId));
            }
            branchBefore = row.getRemainingUnsold();
            BigInteger branchAfter = branchBefore.subtract(quantity);
            BigInteger heldAtBranch = BigInteger.ZERO;
            if (availability.tracksHoldsPerBranch()) {
                BranchVariant key = new BranchVariant(variantId, locationId);
                heldAtBranch = availability.readBranchSnapshots(List.of(key), asOf).get(key).heldQuantity();
            }
            if (branchAfter.signum() < 0 || branchAfter.compareTo(heldAtBranch) < 0) {
                throw new CatalogueException(CatalogueErrorCode.QUANTITY_BELOW_HELD);
            }
        }
        BigInteger before = size.getRemainingUnsold();
        BigInteger after = size.recordAcceptedSale(quantity, heldOfSize.heldQuantity());
        ProductVariant saved = variants.saveAndFlush(size);
        BigInteger branchAfter = after;
        if (row != null) {
            row.setRemainingUnsold(branchBefore.subtract(quantity));
            branchAfter = branchStock.saveSaleDeduction(row).getRemainingUnsold();
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("operationRef", operationRef.toString());
        changes.put("sku", saved.getSku());
        changes.put("locationId", Long.toString(locationId));
        changes.put("assignedToBranches", row != null);
        changes.put("quantity", quantity.toString());
        changes.put("oldQuantity", before.toString());
        changes.put("newQuantity", after.toString());
        if (row != null) {
            changes.put("oldBranchQuantity", branchBefore.toString());
            changes.put("newBranchQuantity", branchAfter.toString());
        }
        audit.recordSuccess(new CatalogueAuditWriter.Entry(CONSUMER, SALE_ACTION, "variant", saved.getId().toString(),
                saved.getVersion(), null, List.of("remainingUnsold", "branchRemainingUnsold"), changes));
        receipts.saveAndFlush(new CatalogueCommandReceipt(CONSUMER, SALE_ACTION, operationRef, digest, "variant",
                saved.getId().toString(), 200, saved.getVersion(), safeResult(after, branchAfter), now()));
        return new BranchAcceptedSaleResult(operationRef, variantId, locationId, quantity, branchAfter, after,
                saved.getVersion(), false);
    }

    private static BranchAcceptedSaleResult replayAtBranch(CatalogueCommandReceipt receipt, UUID operationRef,
                                                           long variantId, long locationId, BigInteger quantity,
                                                           String digest) {
        if (!receipt.getRequestDigest().equals(digest)) {
            throw new CatalogueSalesException(Reason.OPERATION_CONFLICT,
                    "The operation reference was already used for another sale.", List.of(variantId));
        }
        try {
            JsonNode body = JSON.readTree(receipt.getSafeResult());
            BigInteger sizeAfter = new BigInteger(body.get("remainingAfter").asText());
            JsonNode branch = body.get("branchRemainingAfter");
            return new BranchAcceptedSaleResult(operationRef, variantId, locationId, quantity,
                    branch == null ? sizeAfter : new BigInteger(branch.asText()), sizeAfter,
                    receipt.getResultVersion(), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored sale result is unreadable", e);
        }
    }

    private static String safeResult(BigInteger remainingAfter, BigInteger branchRemainingAfter) {
        return JSON.createObjectNode().put("remainingAfter", remainingAfter.toString())
                .put("branchRemainingAfter", branchRemainingAfter.toString()).toString();
    }

    // ---- branch facts ----

    /** Size total and sale flag, from the entity (reads) or from a locked context. */
    private record SizeState(BigInteger remainingUnsold, boolean saleEnabled) {
    }

    private static Map<Long, SizeState> states(Map<Long, ProductVariant> sizes) {
        Map<Long, SizeState> states = new LinkedHashMap<>();
        sizes.forEach((id, size) -> states.put(id, new SizeState(size.getRemainingUnsold(), size.isSaleEnabled())));
        return states;
    }

    private static Map<Long, SizeState> sizesById(SaleContext context) {
        Map<Long, SizeState> states = new LinkedHashMap<>();
        context.variants().forEach(fact -> states.put(fact.variantId(),
                new SizeState(fact.remainingUnsold(), fact.saleEnabled())));
        return states;
    }

    /**
     * One fact per key (ascending). A size with branch rows uses the row of the key's branch (no row = not sold there);
     * a size without rows (not yet assigned) is offered at every active branch from its total.
     */
    private Map<BranchVariant, BranchStockFact> branchFacts(List<BranchVariant> keys, Map<Long, SizeState> sizes,
                                                            Set<Long> activeBranches) {
        Map<Long, Map<Long, VariantBranchStock>> stocks = branchStock.stocksBySize(sizesOf(keys));
        Map<BranchVariant, BranchStockFact> facts = new LinkedHashMap<>();
        for (BranchVariant key : keys) {
            SizeState size = sizes.get(key.variantId());
            Map<Long, VariantBranchStock> rows = stocks.getOrDefault(key.variantId(), Map.of());
            boolean assigned = !rows.isEmpty();
            VariantBranchStock row = rows.get(key.locationId());
            BigInteger remaining = assigned ? (row == null ? BigInteger.ZERO : row.getRemainingUnsold()) : size.remainingUnsold();
            boolean saleEnabled = assigned ? row != null && row.isSaleEnabled() : size.saleEnabled();
            facts.put(key, new BranchStockFact(key.variantId(), key.locationId(), remaining, saleEnabled,
                    activeBranches.contains(key.locationId()), assigned));
        }
        return facts;
    }

    /** The branch is active and sells the size there (the size-level checks are separate). */
    private static boolean sells(BranchStockFact fact) {
        return fact.branchActive() && fact.saleEnabled();
    }

    private static List<BranchVariant> requireKeys(Collection<BranchVariant> keys) {
        if (keys == null || keys.isEmpty() || keys.stream().anyMatch(Objects::isNull)) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "At least one size and branch is required.");
        }
        return List.copyOf(new TreeSet<>(keys));
    }

    private static Set<Long> sizesOf(Collection<BranchVariant> keys) {
        Set<Long> ids = new TreeSet<>();
        keys.forEach(key -> ids.add(key.variantId()));
        return ids;
    }

    private static AcceptedSaleResult replay(CatalogueCommandReceipt receipt, UUID operationRef, long variantId,
                                             BigInteger quantity, String digest) {
        if (!receipt.getRequestDigest().equals(digest)) {
            throw new CatalogueSalesException(Reason.OPERATION_CONFLICT,
                    "The operation reference was already used for another sale.", List.of(variantId));
        }
        try {
            JsonNode body = JSON.readTree(receipt.getSafeResult());
            return new AcceptedSaleResult(operationRef, variantId, quantity,
                    new BigInteger(body.get("remainingAfter").asText()), receipt.getResultVersion(), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored sale result is unreadable", e);
        }
    }

    private static String safeResult(BigInteger remainingAfter) {
        return JSON.createObjectNode().put("remainingAfter", remainingAfter.toString()).toString();
    }

    // ---- helpers ----

    private static String requireOwnerRef(String ownerRecordRef) {
        if (ownerRecordRef == null || ownerRecordRef.isBlank() || ownerRecordRef.length() > MAX_OWNER_REF_LENGTH) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST,
                    "The owner record reference must have 1–128 characters.");
        }
        return ownerRecordRef;
    }

    private static Collection<UUID> requireMediaIds(Collection<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.stream().anyMatch(Objects::isNull)) {
            throw new CatalogueSalesException(Reason.INVALID_REQUEST, "Media IDs must not be null.");
        }
        return mediaIds;
    }

    private static Instant now() {
        return CatalogueValues.toStoredInstant(Instant.now());
    }

    /** Runs catalogue code and maps its internal errors to the facade's typed rejection. */
    private static <T> T translated(Supplier<T> work) {
        try {
            return work.get();
        } catch (CatalogueSalesException e) {
            throw e;
        } catch (CatalogueException e) {
            throw new CatalogueSalesException(reasonOf(e.code()), e.code().defaultMessage());
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new CatalogueSalesException(Reason.BUSY, CatalogueErrorCode.CATALOGUE_BUSY.defaultMessage());
        }
    }

    static Reason reasonOf(CatalogueErrorCode code) {
        return switch (code) {
            case VARIANT_NOT_FOUND, PRODUCT_NOT_FOUND, CATEGORY_NOT_FOUND -> Reason.NOT_FOUND;
            case MEDIA_NOT_FOUND -> Reason.MEDIA_NOT_AVAILABLE;
            case QUANTITY_BELOW_HELD -> Reason.INSUFFICIENT_QUANTITY;
            case AVAILABILITY_DEPENDENCY_UNAVAILABLE, LOCATION_DEPENDENCY_UNAVAILABLE -> Reason.DEPENDENCY_UNAVAILABLE;
            case BRANCH_STOCK_NOT_FOUND, BRANCH_NOT_FOUND -> Reason.NOT_FOR_SALE;
            case STALE_VERSION, CATALOGUE_BUSY -> Reason.BUSY;
            case PRODUCT_DELETED, PRICE_NOT_APPROVED, PUBLICATION_PREREQUISITES_MISSING -> Reason.NOT_FOR_SALE;
            default -> Reason.INVALID_REQUEST;
        };
    }
}
