/**
 * Offers shown on 002's public product pages, from 006's public product programmes (GET /api/v1/promotions,
 * active now, targets reduced to public products). Display only: the checkout quote stays the one that counts.
 *
 * The rules are the ones of the retail quote (RetailQuote / LoyaltyCalculator) applied to ONE unit of a size:
 * - a size is covered when it matches every dimension the programme uses (product IDs AND size IDs);
 * - D = min(price, cap, floor(price × percent / 100)) or min(price, cap, fixed), only when price ≥ minimum;
 * - among automatic discounts the largest discount wins (one programme per order).
 * An automatic discount whose minimum is above the unit price is not taken off the price; it is shown as a badge
 * with its minimum instead.
 *
 * Session 2026-11-22 (FR-048): the price then also takes off the best voucher for one unit, measured on the price
 * after the automatic discount ("Giá sau voucher {mã}"). Counted: PUBLIC vouchers still usable, and the vouchers in the
 * signed-in Member's wallet that are ACTIVE there (a CLAIM_REQUIRED one included). Never counted: a CLAIM_REQUIRED
 * voucher not saved, one without uses. Ties: created first.
 * Session 2026-11-23: a voucher limited to branches counts too (for a size sold at one of its branches, `covers`), and
 * its branches are named on the label; with `options.branchId` (the branch the customer chose on the detail page)
 * only vouchers usable at that branch count, so the price matches the checkout.
 */

export const big = value => {
  try { return value === null || value === undefined || value === '' ? null : BigInt(String(value)); } catch { return null; }
};

/** "12.5" → { numerator: 125n, scale: 10n }; percentages may carry decimals. */
function parsePercent(text) {
  const match = /^(\d+)(?:\.(\d+))?$/.exec(String(text ?? '').trim());
  if (!match) return null;
  const decimals = match[2] || '';
  return { numerator: BigInt(match[1] + decimals), scale: 10n ** BigInt(decimals.length) };
}

const minBig = (...values) => values.filter(value => value !== null).reduce((low, value) => (value < low ? value : low));

export function covers(program, product, variant) {
  const targets = program.targets || {};
  const products = targets.productIds || [];
  const variants = targets.variantIds || [];
  if (!products.length && !variants.length) return false;
  return (!products.length || products.includes(String(product.id))) && (!variants.length || variants.includes(String(variant.id)))
    && soldAtBranches(targets.branchIds || [], variant);
}

/**
 * Session 2026-11-09: a voucher limited to branches only counts for a size sold at one of them (002 FR-052
 * `branches`). A size not yet assigned to branches, or a page without branch facts, is not narrowed.
 */
function soldAtBranches(branchIds, variant) {
  if (!branchIds.length || variant.assignedToBranches !== true || !Array.isArray(variant.branches)) return true;
  return variant.branches.some(branch => branchIds.includes(String(branch.locationId)));
}

/** Discount of one programme on one unit price, or 0n when the price is below its minimum. */
export function unitDiscount(program, price) {
  const amount = big(price);
  if (amount === null || amount <= 0n) return 0n;
  const minimum = big(program.minimumEligibleAmount) ?? 0n;
  if (amount < minimum) return 0n;
  const discount = program.discount || {};
  const cap = big(discount.maxDiscountAmount);
  let raw;
  if (discount.kind === 'PERCENT') {
    const percent = parsePercent(discount.percentage);
    if (!percent) return 0n;
    raw = (amount * percent.numerator) / (100n * percent.scale);
  } else {
    raw = big(discount.amount) ?? 0n;
  }
  const result = minBig(amount, cap, raw);
  return result > 0n ? result : 0n;
}

/** Created first, then the lower ID. The public card has no createdAt, so a missing one falls back to the ID. */
export function createdFirst(a, b) {
  const at = (a.createdAt || '').localeCompare(b.createdAt || '');
  if (at !== 0 && a.createdAt && b.createdAt) return at;
  const left = big(a.id) ?? 0n;
  const right = big(b.id) ?? 0n;
  return left < right ? -1 : left > right ? 1 : 0;
}

const productPrograms = programs => (programs || []).filter(program => program.scope === 'PRODUCT');

/**
 * Whether a voucher may be counted in a displayed price (FR-048). `saved`/`walletAvailability` come from the
 * Member's wallet (usePublicPrograms merges them); a public card alone says `distribution` and `availability`.
 */
export function countsForPrice(program) {
  if (program?.kind !== 'VOUCHER') return false;
  if (program.saved) return program.walletAvailability === 'ACTIVE';
  return program.distribution !== 'CLAIM_REQUIRED' && program.availability !== 'NO_USES_LEFT';
}

/** A CLAIM_REQUIRED voucher the Member has not saved: shown as "Lưu mã giảm …", never in the price. */
export const needsSaving = program => program?.kind === 'VOUCHER' && program.distribution === 'CLAIM_REQUIRED' && !program.saved;

/** Best voucher among `vouchers` on `price` (already after the automatic discount), or null. */
export function bestVoucherOn(vouchers, price) {
  if (price === null || price <= 0n) return null;
  let best = null;
  for (const program of [...vouchers].sort(createdFirst)) {
    const value = unitDiscount(program, price);
    if (value > 0n && (!best || value > best.discount)) best = { program, discount: value, priceAfter: price - value };
  }
  return best;
}

/**
 * Per size: its price, the best automatic discount on one unit and the price after it. Per product: the price
 * range before and after, the best automatic discount, automatic discounts that need a bigger order, and vouchers.
 */
/** No branch limit, or the chosen branch is one of the voucher's (Session 2026-11-23). */
export function usableAtBranch(program, branchId) {
  const branches = program.targets?.branchIds || [];
  return branchId === null || branchId === undefined || branchId === '' || !branches.length
    || branches.includes(String(branchId));
}

export function offersForProduct(product, programs, { branchId = null } = {}) {
  const list = productPrograms(programs);
  const variants = (product.variants || []).filter(variant => big(variant.appliedPrice) !== null);
  // Same order as RetailQuote: on equal discounts the programme created first wins (the API lists newest first).
  const autos = list.filter(program => program.kind === 'AUTO_DISCOUNT').sort(createdFirst);
  const covered = list.filter(program => program.kind === 'VOUCHER' && variants.some(variant => covers(program, product, variant)));
  const priceVouchers = covered.filter(program => countsForPrice(program) && usableAtBranch(program, branchId));

  const sizes = {};
  const needMore = new Map();
  let best = null;
  for (const variant of variants) {
    const price = big(variant.appliedPrice);
    let chosen = null;
    let discount = 0n;
    for (const program of autos) {
      if (!covers(program, product, variant)) continue;
      const value = unitDiscount(program, price);
      if (value > discount) { chosen = program; discount = value; }
      else if (value === 0n && price < (big(program.minimumEligibleAmount) ?? 0n)) needMore.set(program.id, program);
    }
    const voucher = bestVoucherOn(priceVouchers.filter(program => covers(program, product, variant)), price - discount);
    sizes[variant.id] = {
      price, discount, finalPrice: price - discount, program: chosen,
      voucher: voucher?.program || null, voucherDiscount: voucher?.discount || 0n,
      priceAfterVoucher: voucher ? voucher.priceAfter : price - discount,
    };
    if (chosen && (!best || discount > best.discount)) best = { program: chosen, discount };
  }
  // The voucher named on the product: the one of the cheapest size after both discounts.
  const cheapest = Object.values(sizes).filter(size => size.voucher)
    .reduce((low, size) => (!low || size.priceAfterVoucher < low.priceAfterVoucher ? size : low), null);
  const bestVoucher = cheapest ? cheapest.voucher : null;
  const vouchers = bestVoucher ? [bestVoucher, ...covered.filter(program => program !== bestVoucher)] : covered;
  // A programme already taken off one size is not "for a bigger order" any more.
  Object.values(sizes).forEach(size => { if (size.program) needMore.delete(size.program.id); });

  const prices = Object.values(sizes);
  const range = key => (prices.length ? {
    minimum: prices.reduce((low, size) => (size[key] < low ? size[key] : low), prices[0][key]),
    maximum: prices.reduce((high, size) => (size[key] > high ? size[key] : high), prices[0][key]),
  } : null);

  // Which sizes each shown programme covers, so a badge can say "size 18" when it is not every size.
  const coverage = {};
  [best?.program, ...needMore.values(), ...vouchers].filter(Boolean).forEach(program => {
    const covered = variants.filter(variant => covers(program, product, variant));
    coverage[program.id] = {
      all: covered.length === variants.length,
      sizes: covered.map(variant => variant.size).sort((a, b) => a - b),
    };
  });

  return {
    sizes,
    coverage,
    auto: best?.program || null,
    original: range('price'),
    final: range('finalPrice'),
    afterVoucher: range('priceAfterVoucher'),
    bestVoucher,
    discounted: Boolean(best) || Boolean(bestVoucher),
    autoForBiggerOrder: [...needMore.values()],
    vouchers,
  };
}
