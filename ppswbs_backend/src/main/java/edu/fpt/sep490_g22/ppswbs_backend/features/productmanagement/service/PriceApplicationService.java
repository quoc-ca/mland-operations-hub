package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceApplicationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceApplication;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies exactly the amount a Manager approved for one size (T037, FR-015/016/024). A separate command
 * from the decision: the client sends no amount, only the proposal and its version.
 *
 * <ul>
 *   <li>The proposal must be APPROVED and still effective, for the same variant/size/currency terms.</li>
 *   <li>For a never-published product its initial review must still be the effective approval.</li>
 *   <li>A decision based on an older applied price ({@code baseAppliedSequence}) cannot overwrite a newer
 *       one ({@code PRICE_APPLICATION_STALE}); applying the same proposal again is a no-op.</li>
 *   <li>Old and new amounts, actor and time are recorded. Accepted orders and holds keep their own price
 *       snapshot (FE-08); nothing here touches them, and publication stays a separate step.</li>
 * </ul>
 * The caller authorizes the actor first (T007).
 */
@Service
public class PriceApplicationService {

    public static final String APPLY_ACTION = "price.apply";

    private final CatalogueLockCoordinator locks;
    private final PriceProposalRepository proposals;
    private final ProductVariantRepository variants;
    private final ProductReviewRepository reviews;
    private final PriceApplicationRepository applications;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public PriceApplicationService(CatalogueLockCoordinator locks, PriceProposalRepository proposals,
                                   ProductVariantRepository variants, ProductReviewRepository reviews,
                                   PriceApplicationRepository applications, CatalogueAuditWriter audit,
                                   CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.proposals = proposals;
        this.variants = variants;
        this.reviews = reviews;
        this.applications = applications;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome apply(String actorId, UUID commandId, String proposalId,
                                                  long expectedVersion) {
        long id = CatalogueValues.parseId("proposalId", proposalId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("proposalId", id);
        request.put("expectedVersion", expectedVersion);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, APPLY_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            Long variantId = proposals.findVariantIdById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));
            Long productId = variants.findProductIdById(variantId)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            var locked = locks.lockProduct(productId);
            Product product = locked.product();
            product.requireNotDeleted();
            ProductVariant variant = locked.variants().stream()
                    .filter(candidate -> candidate.getId().equals(variantId)).findFirst()
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
            PriceProposal proposal = proposals.findById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));

            if (applications.findByProposalId(proposal.getId()).isPresent()) {
                return result(commandId, proposal, false);
            }
            CatalogueLockCoordinator.requireVersion(proposal.getVersion(), expectedVersion);
            if (!proposal.isEffective() || proposal.getState() != ReviewState.APPROVED) {
                throw new CatalogueException(CatalogueErrorCode.PRICE_NOT_APPROVED);
            }
            if (!ProposalTerms.digest(variant, proposal.getReviewId()).equals(proposal.getTermsDigest())) {
                throw new CatalogueException(CatalogueErrorCode.PRICE_TERMS_CHANGED);
            }
            if (!product.hasEverBeenPublished()) {
                boolean reviewStillApproved = proposal.getReviewId() != null && reviews.findById(proposal.getReviewId())
                        .map(review -> review.isEffectiveApproval()
                                && review.getContentVersion() == product.getContentVersion())
                        .orElse(false);
                if (!reviewStillApproved) {
                    throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
                }
            }

            BigInteger oldAmount = variant.getAppliedPrice();
            variant.applyApprovedPrice(proposal.getId(), proposal.getApprovedAmount(), proposal.getBaseAppliedSequence());
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            applications.saveAndFlush(new PriceApplication(variant.getId(), proposal.getId(),
                    variant.getAppliedSequence(), oldAmount, proposal.getApprovedAmount(), actorId, now));

            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("variantId", variant.getId().toString());
            changes.put("oldAmount", oldAmount == null ? null : oldAmount.toString());
            changes.put("newAmount", proposal.getApprovedAmount().toString());
            changes.put("appliedSequence", variant.getAppliedSequence());
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, APPLY_ACTION, "price-proposal",
                    proposal.getId().toString(), proposal.getVersion(), proposal.getRevision(),
                    List.of("variant.appliedPrice"), changes));
            return result(commandId, proposal, true);
        });
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, PriceProposal proposal,
                                                                 boolean changed) {
        ActionResult body = new ActionResult(commandId, proposal.getId().toString(), "price-proposal", APPLY_ACTION,
                "APPLIED", null, changed);
        return new CatalogueCommandExecutor.CommandResult(200, "price-proposal", proposal.getId().toString(),
                null, body);
    }
}
