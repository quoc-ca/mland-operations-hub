package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Initial product review (T030/T031, US2, FR-009–011/013/014).
 *
 * <ul>
 *   <li><b>Submit</b> freezes one immutable snapshot (content, category, images, sizes and proposal
 *       revisions) and moves the DRAFT review and every size's DRAFT proposal to PENDING together; each
 *       size then has exactly one current pending proposal. Requires an active category, at least one
 *       usable image and a proposal for every size.</li>
 *   <li><b>Decide</b> applies to the current pending revision only. APPROVE must list every size exactly
 *       once with its proposal and a final amount (the Manager may adjust it); REJECT needs a reason and
 *       never approves part of the file. Self-approval is allowed. Nothing is applied or published.</li>
 * </ul>
 * The caller authorizes the actor first (T007): STAFF/MANAGER to submit, MANAGER only to decide.
 */
@Service
public class ProductReviewService {

    public static final String SUBMIT_ACTION = "product.review.submit";
    public static final String DECIDE_ACTION = "product.review.decide";

    public enum Decision {
        APPROVE,
        REJECT
    }

    public record DecisionPrice(String variantId, String proposalId, String approvedPrice) {
    }

    public record DecisionCommand(String reviewId, Long reviewRevision, Long contentVersion, Decision decision,
                                  String reason, List<DecisionPrice> prices) {
        public DecisionCommand {
            prices = prices == null ? List.of() : List.copyOf(prices);
        }
    }

    private final CatalogueLockCoordinator locks;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final ProductImageRepository images;
    private final CatalogueMediaRepository media;
    private final MediaReferenceService mediaReferences;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;
    private final ObjectMapper objectMapper;

    public ProductReviewService(CatalogueLockCoordinator locks, ProductReviewRepository reviews,
                                PriceProposalRepository proposals, ProductImageRepository images,
                                CatalogueMediaRepository media, MediaReferenceService mediaReferences,
                                CatalogueAuditWriter audit, CatalogueCommandExecutor executor,
                                ObjectMapper objectMapper) {
        this.locks = locks;
        this.reviews = reviews;
        this.proposals = proposals;
        this.images = images;
        this.media = media;
        this.mediaReferences = mediaReferences;
        this.audit = audit;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public CatalogueCommandExecutor.Outcome submit(String actorId, UUID commandId, String productId,
                                                   long expectedVersion) {
        long id = CatalogueValues.parseId("productId", productId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, SUBMIT_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            if (product.hasEverBeenPublished()) {
                // After the first publication content is edited directly; prices use their own proposals.
                throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
            }
            ProductReview review = currentReview(product.getId());
            if (review.getState() != ReviewState.DRAFT || !review.isEffective()
                    || review.getContentVersion() != product.getContentVersion()) {
                throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
            }

            List<ErrorResponse.FieldError> missing = new ArrayList<>();
            if (!locked.category().isActive()) {
                missing.add(new ErrorResponse.FieldError("categoryId", "The category must be active."));
            }
            List<ProductImage> productImages = images.findByProductIdOrderByDisplayPositionAsc(product.getId());
            Map<UUID, CatalogueMediaObject> mediaById = media.findAllById(
                            productImages.stream().map(ProductImage::getMediaObjectId).toList()).stream()
                    .collect(Collectors.toMap(CatalogueMediaObject::getId, Function.identity()));
            boolean hasUsableImage = productImages.stream().anyMatch(image -> {
                CatalogueMediaObject object = mediaById.get(image.getMediaObjectId());
                return object != null && object.getState() == CatalogueMediaObject.State.READY;
            });
            if (!hasUsableImage) {
                missing.add(new ErrorResponse.FieldError("images", "At least one usable image is required."));
            }
            Map<Long, PriceProposal> draftByVariant = proposals.findByReviewIdOrderByIdAsc(review.getId()).stream()
                    .filter(proposal -> proposal.isEffective() && proposal.getState() == ReviewState.DRAFT)
                    .collect(Collectors.toMap(PriceProposal::getVariantId, Function.identity(), (a, b) -> b));
            for (ProductVariant variant : locked.variants()) {
                if (variant.getPendingProposalId() != null) {
                    throw new CatalogueException(CatalogueErrorCode.PENDING_PRICE_PROPOSAL_EXISTS);
                }
                if (!draftByVariant.containsKey(variant.getId())) {
                    missing.add(new ErrorResponse.FieldError("variants[" + variant.getSku() + "].proposedPrice",
                            "Every size needs a proposed price."));
                }
            }
            if (!missing.isEmpty()) {
                throw new CatalogueException(CatalogueErrorCode.VALIDATION_ERROR, missing);
            }

            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            review.submit(actorId, now, snapshot(product, locked, productImages, draftByVariant));
            for (ProductVariant variant : locked.variants()) {
                PriceProposal proposal = draftByVariant.get(variant.getId());
                proposal.submit(actorId, now);
                variant.pointPendingProposal(proposal.getId());
            }
            mediaReferences.retain(MediaReferenceService.OWNER_MODULE, "review:" + review.getId(),
                    productImages.stream().map(ProductImage::getMediaObjectId).toList(), now);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, SUBMIT_ACTION, "product",
                    Long.toString(product.getId()), product.getVersion(), review.getRevision(),
                    List.of("review.state"), Map.of("reviewId", review.getId().toString(), "state", "PENDING")));
            return result(commandId, review, SUBMIT_ACTION, product.getVersion());
        });
    }

    public CatalogueCommandExecutor.Outcome decide(String managerId, UUID commandId, String productId,
                                                   DecisionCommand command) {
        long id = CatalogueValues.parseId("productId", productId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("command", command);
        var key = new CatalogueCommandExecutor.CommandKey(managerId, DECIDE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            long reviewId = CatalogueValues.parseId("reviewId", command.reviewId());
            if (command.decision() == null || command.reviewRevision() == null || command.contentVersion() == null) {
                throw CatalogueException.invalidField("decision", "Decision, review revision and content version are required.");
            }
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            ProductReview review = reviews.findById(reviewId)
                    .filter(found -> found.getProductId().equals(product.getId()))
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT));
            if (review.getRevision() != command.reviewRevision()
                    || review.getContentVersion() != command.contentVersion()
                    || review.getContentVersion() != product.getContentVersion()
                    || review.getState() != ReviewState.PENDING || !review.isEffective()) {
                throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
            }
            List<PriceProposal> pending = proposals.findByReviewIdOrderByIdAsc(review.getId()).stream()
                    .filter(PriceProposal::isCurrentPending)
                    .toList();
            Map<Long, ProductVariant> variantById = locked.variants().stream()
                    .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
            Instant now = CatalogueValues.toStoredInstant(Instant.now());

            if (command.decision() == Decision.APPROVE) {
                Map<Long, BigInteger> finalAmounts = approvedAmounts(command.prices(), pending);
                review.approve(managerId, now);
                for (PriceProposal proposal : pending) {
                    proposal.approve(managerId, now, finalAmounts.get(proposal.getId()));
                    variantById.get(proposal.getVariantId()).clearPendingProposal();
                }
            } else {
                if (!command.prices().isEmpty()) {
                    throw CatalogueException.invalidField("prices", "A rejection carries no amounts.");
                }
                review.reject(managerId, now, command.reason());
                for (PriceProposal proposal : pending) {
                    proposal.reject(managerId, now, command.reason());
                    variantById.get(proposal.getVariantId()).clearPendingProposal();
                }
            }
            String state = review.getState().name();
            audit.recordSuccess(new CatalogueAuditWriter.Entry(managerId, DECIDE_ACTION, "product",
                    Long.toString(product.getId()), product.getVersion(), review.getRevision(),
                    List.of("review.state", "proposals.state"),
                    Map.of("reviewId", review.getId().toString(), "state", state)));
            return result(commandId, review, DECIDE_ACTION, product.getVersion());
        });
    }

    /** Every pending proposal exactly once, matching its variant, with a valid final amount. */
    private static Map<Long, BigInteger> approvedAmounts(List<DecisionPrice> prices, List<PriceProposal> pending) {
        Map<Long, PriceProposal> pendingById = new HashMap<>();
        pending.forEach(proposal -> pendingById.put(proposal.getId(), proposal));
        Map<Long, BigInteger> amounts = new HashMap<>();
        for (int i = 0; i < prices.size(); i++) {
            DecisionPrice price = prices.get(i);
            String field = "prices[" + i + "]";
            long proposalId = CatalogueValues.parseId(field + ".proposalId", price.proposalId());
            long variantId = CatalogueValues.parseId(field + ".variantId", price.variantId());
            PriceProposal proposal = pendingById.get(proposalId);
            if (proposal == null || proposal.getVariantId() != variantId || amounts.containsKey(proposalId)) {
                throw CatalogueException.invalidField(field, "Each size of the review must appear once with its current proposal.");
            }
            amounts.put(proposalId, CatalogueValues.parseAmount(field + ".approvedPrice", price.approvedPrice()));
        }
        if (amounts.size() != pending.size()) {
            throw CatalogueException.invalidField("prices", "Every size of the review must be decided.");
        }
        return amounts;
    }

    private ProductReview currentReview(long productId) {
        return reviews.findFirstByProductIdOrderByRevisionDesc(productId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT));
    }

    private String snapshot(Product product, CatalogueLockCoordinator.ProductLock locked,
                            List<ProductImage> productImages, Map<Long, PriceProposal> draftByVariant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getId().toString());
        snapshot.put("contentVersion", product.getContentVersion());
        snapshot.put("name", product.getName());
        snapshot.put("description", product.getDescription());
        snapshot.put("categoryId", locked.category().getId().toString());
        snapshot.put("categoryName", locked.category().getName());
        snapshot.put("images", productImages.stream().map(image -> Map.of(
                "mediaId", image.getMediaObjectId().toString(),
                "altText", image.getAltText(),
                "position", image.getDisplayPosition())).toList());
        snapshot.put("variants", locked.variants().stream().map(variant -> {
            PriceProposal proposal = draftByVariant.get(variant.getId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("variantId", variant.getId().toString());
            entry.put("size", variant.getRingSize());
            entry.put("sku", variant.getSku());
            entry.put("proposalId", proposal.getId().toString());
            entry.put("proposalRevision", proposal.getRevision());
            entry.put("proposedPrice", proposal.getProposedAmount().toString());
            entry.put("quantityAtSubmission", variant.getRemainingUnsold().toString());
            return entry;
        }).toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the review snapshot", e);
        }
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, ProductReview review, String action,
                                                                 long productVersion) {
        ActionResult body = new ActionResult(commandId, review.getId().toString(), "product-review", action,
                review.getState().name(), productVersion, true);
        return new CatalogueCommandExecutor.CommandResult(200, "product-review", review.getId().toString(),
                productVersion, body);
    }
}
