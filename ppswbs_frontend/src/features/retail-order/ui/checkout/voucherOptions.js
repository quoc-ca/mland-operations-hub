import { useEffect, useState } from 'react';

/**
 * What each saved voucher really gives for the chosen lines, asked of FE-06 (one preview per active voucher, in-store
 * pickup, no points). The cart and the checkout picker use the same answer, so the "best" voucher is the same on both
 * and a voucher for other products or branches is never suggested. Returns null while checking.
 *
 * @param base {branchOrders, cartLineIds} of the lines being bought
 * @returns Map code -> { eligible, saving: bigint }
 */
export function useVoucherOptions(api, vouchers, base) {
  const codes = vouchers.filter(voucher => voucher.availability === 'ACTIVE').map(voucher => voucher.code).sort();
  const key = JSON.stringify([codes, base]);
  const [state, setState] = useState({ key: null, options: null });
  useEffect(() => {
    if (!codes.length || !base?.cartLineIds?.length) { setState({ key, options: new Map() }); return undefined; }
    let live = true;
    Promise.all(codes.map(code => api.previewBenefits({ ...base, estimateOnly: true, selection: { choice: 'VOUCHER', voucherCode: code, requestedPoints: '0' } })
      .then(result => {
        // Since FE-06 stacks the automatic discount with the voucher (Session 2026-11-22), discountAmount includes the
        // automatic discount the Member gets anyway; a code's own saving is voucherDiscountAmount.
        const saving = result?.eligible ? BigInt(result.voucherDiscountAmount ?? result.discountAmount ?? '0') : 0n;
        // FE-06's own reason (as it wrote it), so the picker says why a voucher cannot be used here in the chosen language (2026-11-23).
        return [code, { eligible: saving > 0n, saving, reason: saving > 0n ? null : voucherReasonCode(result?.reasons) }];
      })
      .catch(failure => [code, { eligible: false, saving: 0n, reason: voucherReasonCode([failure?.message]) }])))
      .then(entries => { if (live) setState({ key, options: new Map(entries) }); });
    return () => { live = false; };
  }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  return state.key === key ? state.options : null;
}

/**
 * Every product voucher a Member can type at checkout: the public active vouchers (the ones product pages show),
 * not only the saved ones. FE-06 does not require saving a voucher before using its code. A failure leaves the list
 * empty, so the picker still shows the saved vouchers.
 */
export function usePublicProductVouchers(api) {
  const [items, setItems] = useState([]);
  useEffect(() => {
    let live = true;
    api.listPublicProductPromotions()
      .then(result => {
        if (!live) return;
        // NO_USES_LEFT (FE-06 Session 2026-11-14): every use is taken, so it is shown greyed, never suggested.
        setItems((result?.items || []).filter(item => item.kind === 'VOUCHER' && item.code).map(item => ({
          code: item.code, name: item.name || item.code, availability: item.availability === 'NO_USES_LEFT' ? 'NO_USES_LEFT' : 'ACTIVE', discount: item.discount || null,
          minimumEligibleAmount: item.minimumEligibleAmount || '0', endAt: item.endAt || null,
        })));
      })
      .catch(() => {});
    return () => { live = false; };
  }, [api]);
  return items;
}

/**
 * Saved product vouchers as picker options. A saved workshop voucher (scope WORKSHOP) can never be used on ring
 * orders, so it is left out of the retail picker (Session 2026-11-16); a saved item without a scope is kept.
 */
export function savedProductVouchers(items) {
  return (items || [])
    .filter(item => (item.promotion?.scope || item.scope || 'PRODUCT') === 'PRODUCT')
    .map(item => ({ code: item.promotion?.code || item.code, name: item.promotion?.name || item.name || item.promotion?.code || item.code, availability: item.availability,
      discount: item.promotion?.discount || null, minimumEligibleAmount: item.promotion?.minimumEligibleAmount || '0', endAt: item.promotion?.endAt || null }))
    .filter(item => item.code);
}

/** Saved vouchers first (they keep their own availability), then public vouchers not saved yet. */
export function withPublicVouchers(saved, publicVouchers) {
  const seen = new Set(saved.map(voucher => String(voucher.code).toUpperCase()));
  return [...saved, ...publicVouchers.filter(voucher => !seen.has(String(voucher.code).toUpperCase()))];
}

/** What FE-06 writes when no automatic discount applies; it never explains a voucher refusal. */
export const NO_AUTO_DISCOUNT_REASON = 'No automatic discount applies to this cart.';

/** FE-06 sends its preview reasons in English; each known one has a vi/en wording under this key in the dictionary. */
const REASON_KEYS = {
  [NO_AUTO_DISCOUNT_REASON]: 'reason.noAutomaticDiscount',
  'Save this voucher in Kho Voucher before using it.': 'reason.saveVoucherFirst',
  'The automatic discount already covers these items; this voucher gives nothing more.': 'reason.automaticDiscountCovers',
  'No item in the cart is covered by this voucher.': 'reason.noItemCovered',
  'This voucher does not apply at the branch of these items.': 'reason.otherBranch',
  'The covered items do not reach the minimum amount of this voucher.': 'reason.minimumNotReached',
  'The voucher code was not found.': 'reason.codeNotFound',
  'This voucher is for workshop bookings, not for products.': 'reason.workshopVoucher',
  'This promotion is paused.': 'reason.paused',
  'This promotion has not started yet.': 'reason.notStarted',
  'This promotion has ended.': 'reason.ended',
  'This voucher has no uses left.': 'reason.noUsesLeft',
  'You have already used this voucher the maximum number of times.': 'reason.memberLimitReached',
  'Points cannot be used with this voucher.': 'reason.noPoints',
};

/** True when the dictionary has its own wording for this FE-06 reason. */
export const isKnownReason = reason => Object.prototype.hasOwnProperty.call(REASON_KEYS, reason);

/** A reason in the chosen language; an unknown reason keeps FE-06's own text. */
export function reasonText(reason, t) {
  return isKnownReason(reason) ? t(REASON_KEYS[reason]) : reason;
}

/** The reasons in the chosen language; an unknown reason keeps FE-06's text. */
export function reasonsText(reasons, t) {
  return (reasons || []).map(reason => reasonText(reason, t));
}

/** The first FE-06 reason that concerns the voucher, exactly as FE-06 wrote it, or null. */
export function voucherReasonCode(reasons) {
  const known = (reasons || []).filter(reason => reason && reason !== NO_AUTO_DISCOUNT_REASON);
  return known.length ? known[0] : null;
}

/** The same reason in the chosen language, or null. */
export function voucherReason(reasons, t) {
  const code = voucherReasonCode(reasons);
  return code ? reasonText(code, t) : null;
}

/** The usable voucher with the largest real saving, or null. */
export function bestVoucherCode(options) {
  if (!options) return null;
  let best = null;
  let bestSaving = 0n;
  options.forEach((option, code) => {
    if (option.eligible && option.saving > bestSaving) { best = code; bestSaving = option.saving; }
  });
  return best;
}

/** Pickup branch orders for the lines, the body every voucher check uses. */
export function voucherCheckBase(lines) {
  const locations = [...new Set(lines.map(line => String(line.locationId)))];
  return {
    branchOrders: locations.map(locationId => ({ locationId: Number(locationId), method: 'PICKUP', shippingQuoteId: null, recipient: null })),
    cartLineIds: lines.map(line => String(line.id)),
  };
}
