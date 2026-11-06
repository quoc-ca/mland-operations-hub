import { newCommandId } from '../../../shared/api/http';
import { currentReview, effectiveProposal } from '../domain/productRules';

/** Approved proposals whose price is not applied yet, smallest size first. */
export function approvedUnapplied(product) {
  return (product.variants || [])
    .map(variant => ({ variant, proposal: effectiveProposal(variant) }))
    .filter(row => row.proposal?.state === 'APPROVED' && !row.proposal.applied)
    .sort((a, b) => Number(a.variant.size) - Number(b.variant.size));
}

/**
 * A Manager publishing a product that was never published, without a separate review round: submit it if it is still a
 * draft, approve the proposed prices, apply them, then publish. Every step is its own command (own record and audit), and
 * each step re-reads the product so the next one carries the current version. Whatever is already done is skipped, so
 * running it again after a failure carries on from where it stopped.
 */
export async function publishWithoutReview(api, product) {
  let current = product;
  let review = currentReview(current);
  if (review?.state === 'DRAFT') {
    await api.submitReview(newCommandId(), current.id, current.version);
    current = await api.getProduct(current.id);
    review = currentReview(current);
  }
  if (review?.state === 'PENDING') {
    const rows = (current.variants || []).map(variant => ({ variant, proposal: effectiveProposal(variant) }));
    await api.decideReview(newCommandId(), current.id, {
      reviewId: review.id, reviewRevision: review.revision, contentVersion: review.contentVersion, decision: 'APPROVE',
      prices: rows.map(({ variant, proposal }) => ({ variantId: variant.id, proposalId: proposal?.id, approvedPrice: proposal?.proposedPrice })),
    });
    current = await api.getProduct(current.id);
  }
  for (const { proposal } of approvedUnapplied(current)) {
    await api.applyPrice(newCommandId(), proposal.id, proposal.version);
  }
  current = await api.getProduct(current.id);
  return api.setPublication(newCommandId(), current.id, current.version, true);
}
