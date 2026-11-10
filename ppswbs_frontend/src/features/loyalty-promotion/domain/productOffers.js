/**
 * Offers shown on 002's public product pages, from 006's public product programmes (GET /api/v1/promotions,
 * active now, targets reduced to public products). Display only: the checkout quote stays the one that counts.
 *
 * The rules are the ones of the retail quote (RetailQuote / LoyaltyCalculator) applied to ONE unit of a size:
 * - a size is covered when it matches every dimension the programme uses (product IDs AND size IDs);
 * - D = min(price, cap, floor(price × percent / 100)) or min(price, cap, fixed), only when price ≥ minimum;
 * - among automatic discounts the largest discount wins (one programme per order).
 * An automatic discount whose minimum is above the unit price is not taken off the price; it is shown as a badge
 * with its minimum instead. Vouchers are never taken off: they need a code at checkout, so they are badges.
 */

const big = value => {
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
function createdFirst(a, b) {
  const at = (a.createdAt || '').localeCompare(b.createdAt || '');
  if (at !== 0 && a.createdAt && b.createdAt) return at;
  const left = big(a.id) ?? 0n;
  const right = big(b.id) ?? 0n;
  return left < right ? -1 : left > right ? 1 : 0;
}

const productPrograms = programs => (programs || []).filter(program => program.scope === 'PRODUCT');

/**
 * Per size: its price, the best automatic discount on one unit and the price after it. Per product: the price
 * range before and after, the best automatic discount, automatic discounts that need a bigger order, and vouchers.
 */
export function offersForProduct(product, programs) {
  const list = productPrograms(programs);
  const variants = (product.variants || []).filter(variant => big(variant.appliedPrice) !== null);
  // Same order as RetailQuote: on equal discounts the programme created first wins (the API lists newest first).
  const autos = list.filter(program => program.kind === 'AUTO_DISCOUNT').sort(createdFirst);
  const vouchers = list.filter(program => program.kind === 'VOUCHER' && variants.some(variant => covers(program, product, variant)));

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
    sizes[variant.id] = { price, discount, finalPrice: price - discount, program: chosen };
    if (chosen && (!best || discount > best.discount)) best = { program: chosen, discount };
  }
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
    discounted: Boolean(best),
    autoForBiggerOrder: [...needMore.values()],
    vouchers,
  };
}
