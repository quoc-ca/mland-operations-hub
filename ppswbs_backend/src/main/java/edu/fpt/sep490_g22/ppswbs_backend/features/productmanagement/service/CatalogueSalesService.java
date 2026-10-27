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
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AcceptedSaleResult;
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

    public CatalogueSalesService(CatalogueLockCoordinator locks, ProductRepository products,
                                 ProductVariantRepository variants, ProductImageRepository images,
                                 CatalogueMediaRepository media, PublicCatalogueQueryRepository publicQueries,
                                 CatalogueAvailabilityService availability, MediaReferenceService mediaReferences,
                                 CatalogueCommandReceiptRepository receipts, CatalogueAuditWriter audit) {
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

        Map<String, Object> changes = new LinkedHashMap<>();
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
            case AVAILABILITY_DEPENDENCY_UNAVAILABLE -> Reason.DEPENDENCY_UNAVAILABLE;
            case STALE_VERSION, CATALOGUE_BUSY -> Reason.BUSY;
            case PRODUCT_DELETED, PRICE_NOT_APPROVED, PUBLICATION_PREREQUISITES_MISSING -> Reason.NOT_FOR_SALE;
            default -> Reason.INVALID_REQUEST;
        };
    }
}
