package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Price proposals for one size (T053/T054, US5, FR-011/013/014/016).
 *
 * <ul>
 *   <li><b>Create / revise</b> adds a new DRAFT row that supersedes the size's latest effective revision;
 *       history and recorded decisions are never rewritten. A size with a current PENDING proposal cannot
 *       get an independent one ({@code PENDING_PRICE_PROPOSAL_EXISTS}); revising that pending revision
 *       withdraws it and clears the pointer. Drafts never change the applied price or publication.</li>
 *   <li>Before the first publication the change goes through the initial review (T032): it withdraws a
 *       submitted or decided review and starts a new DRAFT revision; an old approval is never inherited.</li>
 *   <li><b>Submit</b> moves an independent DRAFT to PENDING under the size lock, one pending per size.</li>
 *   <li><b>Decide</b> is for the current independent PENDING revision only. Proposals of the initial review
 *       are decided as a whole through {@link ProductReviewService}. Nothing is applied or published here.</li>
 * </ul>
 * {@code expectedVersion} is the variant version for create and the proposal version otherwise.
 * The caller authorizes the actor first (T007): STAFF/MANAGER to propose and submit, MANAGER only to decide.
 */
@Service
public class PriceProposalService {

    public static final String CREATE_ACTION = "price.propose";
    public static final String REVISE_ACTION = "price.revise";
    public static final String SUBMIT_ACTION = "price.submit";
    public static final String DECIDE_ACTION = "price.decide";

    public enum Decision {
        APPROVE,
        REJECT
    }

    public record DecisionCommand(Long expectedVersion, Decision decision, String approvedPrice, String reason) {
    }

    private final CatalogueLockCoordinator locks;
    private final PriceProposalRepository proposals;
    private final ProductVariantRepository variants;
    private final ProductReviewInvalidationService invalidation;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public PriceProposalService(CatalogueLockCoordinator locks, PriceProposalRepository proposals,
                                ProductVariantRepository variants, ProductReviewInvalidationService invalidation,
                                CatalogueAuditWriter audit, CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.proposals = proposals;
        this.variants = variants;
        this.invalidation = invalidation;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome create(String actorId, UUID commandId, String variantId,
                                                   long expectedVersion, String proposedPrice) {
        long id = CatalogueValues.parseId("variantId", variantId);
        BigInteger amount = CatalogueValues.parseAmount("proposedPrice", proposedPrice);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("variantId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("proposedPrice", amount.toString());
        var key = new CatalogueCommandExecutor.CommandKey(actorId, CREATE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            SizeLock size = lockSize(id);
            CatalogueLockCoordinator.requireVersion(size.variant().getVersion(), expectedVersion);
            if (size.variant().getPendingProposalId() != null) {
                throw new CatalogueException(CatalogueErrorCode.PENDING_PRICE_PROPOSAL_EXISTS);
            }
            PriceProposal latest = proposals.findFirstByVariantIdOrderByRevisionDescIdDesc(id).orElse(null);
            PriceProposal created = newRevision(size, latest, amount);
            return recorded(actorId, commandId, CREATE_ACTION, created, 201);
        });
    }

    public CatalogueCommandExecutor.Outcome revise(String actorId, UUID commandId, String proposalId,
                                                   long expectedVersion, String proposedPrice) {
        long id = CatalogueValues.parseId("proposalId", proposalId);
        BigInteger amount = CatalogueValues.parseAmount("proposedPrice", proposedPrice);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("proposalId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("proposedPrice", amount.toString());
        var key = new CatalogueCommandExecutor.CommandKey(actorId, REVISE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            SizeLock size = lockSize(variantOf(id));
            PriceProposal proposal = requireLatestEffective(id, size.variant());
            CatalogueLockCoordinator.requireVersion(proposal.getVersion(), expectedVersion);
            PriceProposal created = newRevision(size, proposal, amount);
            return recorded(actorId, commandId, REVISE_ACTION, created, 201);
        });
    }

    public CatalogueCommandExecutor.Outcome submit(String actorId, UUID commandId, String proposalId,
                                                   long expectedVersion) {
        long id = CatalogueValues.parseId("proposalId", proposalId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("proposalId", id);
        request.put("expectedVersion", expectedVersion);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, SUBMIT_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            SizeLock size = lockSize(variantOf(id));
            PriceProposal proposal = requireLatestEffective(id, size.variant());
            requireIndependent(proposal);
            CatalogueLockCoordinator.requireVersion(proposal.getVersion(), expectedVersion);
            if (size.variant().getPendingProposalId() != null) {
                throw new CatalogueException(CatalogueErrorCode.PENDING_PRICE_PROPOSAL_EXISTS);
            }
            if (proposal.getState() != ReviewState.DRAFT) {
                throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
            }
            requireSameTerms(size.variant(), proposal);
            proposal.submit(actorId, CatalogueValues.toStoredInstant(Instant.now()));
            size.variant().pointPendingProposal(proposal.getId());
            return recorded(actorId, commandId, SUBMIT_ACTION, proposals.saveAndFlush(proposal), 200);
        });
    }

    public CatalogueCommandExecutor.Outcome decide(String managerId, UUID commandId, String proposalId,
                                                   DecisionCommand command) {
        long id = CatalogueValues.parseId("proposalId", proposalId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("proposalId", id);
        request.put("command", command);
        var key = new CatalogueCommandExecutor.CommandKey(managerId, DECIDE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            if (command.decision() == null || command.expectedVersion() == null) {
                throw CatalogueException.invalidField("decision", "Decision and expected version are required.");
            }
            SizeLock size = lockSize(variantOf(id));
            PriceProposal proposal = proposals.findById(id)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));
            requireIndependent(proposal);
            CatalogueLockCoordinator.requireVersion(proposal.getVersion(), command.expectedVersion());
            if (!proposal.isCurrentPending() || !Objects.equals(size.variant().getPendingProposalId(), proposal.getId())) {
                throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
            }
            requireSameTerms(size.variant(), proposal);
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            if (command.decision() == Decision.APPROVE) {
                if (command.reason() != null) {
                    throw CatalogueException.invalidField("reason", "An approval carries no reason.");
                }
                proposal.approve(managerId, now, CatalogueValues.parseAmount("approvedPrice", command.approvedPrice()));
            } else {
                if (command.approvedPrice() != null) {
                    throw CatalogueException.invalidField("approvedPrice", "A rejection carries no amount.");
                }
                proposal.reject(managerId, now, command.reason());
            }
            size.variant().clearPendingProposal();
            return recorded(managerId, commandId, DECIDE_ACTION, proposals.saveAndFlush(proposal), 200);
        });
    }

    private record SizeLock(Product product, ProductVariant variant, List<ProductVariant> allSizes) {
    }

    private SizeLock lockSize(long variantId) {
        Long productId = variants.findProductIdById(variantId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
        var locked = locks.lockProduct(productId);
        locked.product().requireNotDeleted();
        ProductVariant variant = locked.variants().stream()
                .filter(candidate -> candidate.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.VARIANT_NOT_FOUND));
        return new SizeLock(locked.product(), variant, locked.variants());
    }

    private long variantOf(long proposalId) {
        return proposals.findVariantIdById(proposalId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));
    }

    /** Only the size's latest revision can be revised or submitted; older ones are history. */
    private PriceProposal requireLatestEffective(long proposalId, ProductVariant variant) {
        PriceProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PROPOSAL_NOT_FOUND));
        boolean latest = proposals.findFirstByVariantIdOrderByRevisionDescIdDesc(variant.getId())
                .map(found -> found.getId().equals(proposal.getId()))
                .orElse(false);
        if (!latest || !proposal.isEffective()) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
        return proposal;
    }

    /** Initial-review proposals are submitted and decided only as part of the whole review. */
    private static void requireIndependent(PriceProposal proposal) {
        if (proposal.getReviewId() != null) {
            throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
        }
    }

    private static void requireSameTerms(ProductVariant variant, PriceProposal proposal) {
        if (!ProposalTerms.digest(variant, null).equals(proposal.getTermsDigest())) {
            throw new CatalogueException(CatalogueErrorCode.PRICE_TERMS_CHANGED);
        }
    }

    /**
     * New DRAFT revision for the size. Before the first publication it belongs to the initial review;
     * afterwards it is independent and supersedes the previous revision's effect (an applied price stays
     * applied until a newer one replaces it).
     */
    private PriceProposal newRevision(SizeLock size, PriceProposal previous, BigInteger amount) {
        ProductVariant variant = size.variant();
        if (!size.product().hasEverBeenPublished()) {
            ProductReview draft = invalidation.invalidateBeforeFirstPublication(size.product(), size.allSizes(),
                    Map.of(variant.getId(), amount));
            return proposals.findFirstByVariantIdOrderByRevisionDescIdDesc(variant.getId())
                    .filter(found -> Objects.equals(found.getReviewId(), draft.getId()))
                    .orElseThrow(() -> new IllegalStateException("The review draft has no proposal for the size"));
        }
        if (previous != null && previous.isEffective() && !previous.getId().equals(variant.getAppliedProposalId())) {
            previous.makeIneffective();
        }
        if (variant.getPendingProposalId() != null) {
            variant.clearPendingProposal();
        }
        return proposals.saveAndFlush(PriceProposal.draft(variant.getId(), null,
                previous == null ? 1 : previous.getRevision() + 1, amount, variant.getAppliedSequence(),
                ProposalTerms.digest(variant, null), previous == null ? null : previous.getId()));
    }

    private CatalogueCommandExecutor.CommandResult recorded(String actorId, UUID commandId, String action,
                                                            PriceProposal proposal, int status) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("variantId", proposal.getVariantId().toString());
        changes.put("state", proposal.getState().name());
        changes.put("proposedAmount", proposal.getProposedAmount().toString());
        changes.put("approvedAmount", proposal.getApprovedAmount() == null ? null : proposal.getApprovedAmount().toString());
        changes.put("supersedesId", proposal.getSupersedesId() == null ? null : proposal.getSupersedesId().toString());
        audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, action, "price-proposal",
                proposal.getId().toString(), proposal.getVersion(), proposal.getRevision(),
                List.of("proposal.state"), changes));
        ActionResult body = new ActionResult(commandId, proposal.getId().toString(), "price-proposal", action,
                proposal.getState().name(), proposal.getVersion(), true);
        return new CatalogueCommandExecutor.CommandResult(status, "price-proposal", proposal.getId().toString(),
                proposal.getVersion(), body);
    }
}
