import { newCommandId } from '../../../shared/api/http';

/**
 * FR-069 (Session 2026-11-23): after sizes were added to a product already published once, each new size has one
 * independent DRAFT price proposal. Staff submit it (it waits in "Chờ duyệt giá"); a Manager submits, approves it at
 * the same amount and, when applyNow, applies it (like FR-066 "Đổi giá"). Every step is its own command; a failing
 * size stops there and does not stop the others. Before the first publication the price is part of the initial review
 * draft (FR-011), so nothing is sent here.
 *
 * Returns { done: [size], failed: [{ size, error }] }.
 */
export async function sendNewSizePrices(api, productId, sizes, { manager = false, applyNow = true } = {}) {
  const result = { done: [], failed: [] };
  if (!sizes.length) return result;
  const product = await api.getProduct(productId);
  if (!product.firstPublishedAt) return result;
  for (const size of sizes) {
    const variant = (product.variants || []).find(item => item.size === size);
    const draft = latestDraft(variant);
    if (!draft) { result.failed.push({ size, error: null }); continue; }
    try {
      const submitted = await api.submitPrice(newCommandId(), draft.id, draft.version);
      if (manager) {
        const decided = await api.decidePrice(newCommandId(), submitted.resourceId,
          { expectedVersion: submitted.version, decision: 'APPROVE', approvedPrice: draft.proposedPrice });
        if (applyNow) await api.applyPrice(newCommandId(), decided.resourceId, decided.version);
      }
      result.done.push(size);
    } catch (error) {
      result.failed.push({ size, error });
    }
  }
  return result;
}

function latestDraft(variant) {
  const drafts = (variant?.proposals || []).filter(proposal => proposal.state === 'DRAFT' && proposal.effective);
  return drafts.sort((a, b) => b.revision - a.revision)[0] || null;
}

/** Sizes 5–29 in order. */
export const ALL_SIZES = Array.from({ length: 25 }, (_, index) => index + 5);
