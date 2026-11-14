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
    Promise.all(codes.map(code => api.previewBenefits({ ...base, selection: { choice: 'VOUCHER', voucherCode: code, requestedPoints: '0' } })
      .then(result => {
        const saving = result?.eligible ? BigInt(result.discountAmount || '0') : 0n;
        return [code, { eligible: saving > 0n, saving }];
      })
      .catch(() => [code, { eligible: false, saving: 0n }])))
      .then(entries => { if (live) setState({ key, options: new Map(entries) }); });
    return () => { live = false; };
  }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  return state.key === key ? state.options : null;
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
