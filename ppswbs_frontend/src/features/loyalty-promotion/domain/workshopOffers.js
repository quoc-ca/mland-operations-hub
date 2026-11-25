import { bestVoucherOn, big, countsForPrice, createdFirst, unitDiscount } from './productOffers';

/**
 * Offers shown on 003.1's public workshop package surfaces (006 FR-045, 003.1 FR-052/FR-052a), from 006's public
 * workshop programmes (GET /api/v1/promotions?scope=WORKSHOP, active now). Display only: the amount a customer pays
 * is always the one POST /api/v1/workshop-cart-quotes computes.
 *
 * The rules follow FR-042 for products, applied to ONE ticket of a package:
 * - an automatic discount covers a package when its packageIds include it (it has no other dimension, FR-044);
 * - a voucher covers a package when it has no packageIds or they include it; its branch, date and session limits
 *   cannot be checked before the customer picks them, so they do not narrow the label, but a voucher with any of
 *   those limits is never the estimated "with code" price (it may not apply to the branch, date or session chosen);
 * - D = min(price, cap, floor(price × percent / 100)) or min(price, cap, fixed), and 0 below the minimum package fee
 *   (the same unitDiscount as the product offers, so both pages round the same way);
 * - the largest automatic discount is taken off the price; on equal discounts the programme created first wins;
 * - an automatic discount whose minimum fee is above the price of one ticket is not taken off; it is listed as one
 *   "for orders from …" instead;
 * - vouchers are labels, and the best one limited by nothing but packages is also taken off as the "with code"
 *   price (FR-045(4), FR-048). Points are never part of the price.
 * Session 2026-11-22 (FR-048): that voucher is the best one the visitor could use — a PUBLIC voucher still usable, or
 * a voucher ACTIVE in the signed-in Member's wallet (a CLAIM_REQUIRED one included) — and it stacks on the automatic
 * discount at checkout too (FR-015), so the price is really what one ticket costs with that code.
 */

// Session 2026-11-23: a branch limit no longer keeps a voucher out of the price (the label names the branch); a date
// or session limit still does, a package has many of them.
const LIMITS_UNKNOWN_FOR_A_PACKAGE = ['sessionIds', 'workshopDates'];

/** True when the voucher's branch, date and session limits are all empty, so it applies to every ticket of a package. */
const limitedByPackageOnly = program =>
  LIMITS_UNKNOWN_FOR_A_PACKAGE.every(name => !(program.targets?.[name] || []).length);

const workshopPrograms = programs => (programs || []).filter(program => program.scope === 'WORKSHOP');

export function coversPackage(program, packageId) {
  if (packageId === null || packageId === undefined || packageId === '') return false;
  const packages = (program.targets?.packageIds || []).map(String);
  if (program.kind === 'AUTO_DISCOUNT') return packages.includes(String(packageId));
  return !packages.length || packages.includes(String(packageId));
}

/**
 * Offers of one package. `pkg` is a 003 package as its public reads give it: `packageId` and `publicPrice` (the price
 * of one ticket); `id` / `price` are accepted too. Returns the price of one ticket before and after the best
 * automatic discount, that programme, automatic discounts that need a bigger order, and the vouchers covering it.
 */
/**
 * A voucher limited to branches counts for a package only when the package runs at one of them (Session 2026-11-23).
 * `pkg.branchIds` (the location IDs where the package has sessions) comes from 003; a package read without it is not
 * narrowed, the label still names the voucher's branches.
 */
function runsAtVoucherBranch(program, pkg) {
  const branches = (program.targets?.branchIds || []).map(String);
  if (!branches.length || !Array.isArray(pkg?.branchIds)) return true;
  return pkg.branchIds.some(id => branches.includes(String(id)));
}

export function offersForPackage(pkg, programs) {
  const packageId = pkg?.packageId ?? pkg?.id;
  const price = big(pkg?.publicPrice ?? pkg?.price);
  const list = workshopPrograms(programs).filter(program => coversPackage(program, packageId));
  // The API lists newest first; RetailQuote and the workshop quote prefer the programme created first on a tie.
  const autos = list.filter(program => program.kind === 'AUTO_DISCOUNT').sort(createdFirst);
  const vouchers = list.filter(program => program.kind === 'VOUCHER');

  let auto = null;
  let discount = 0n;
  const autoForBiggerOrder = [];
  if (price !== null && price > 0n) {
    for (const program of autos) {
      const value = unitDiscount(program, price);
      if (value > discount) { auto = program; discount = value; }
      else if (value === 0n && price < (big(program.minimumEligibleAmount) ?? 0n)) autoForBiggerOrder.push(program);
    }
  }

  // Estimate for one ticket: the voucher with the largest discount on the price after the automatic discount, among
  // those whose minimum the ticket reaches and with no branch, date or session limit (FR-045). Shown as a Member-only
  // "with code" price; the quote stays the truth.
  const afterAuto = price === null ? null : price - discount;
  let bestVoucher = null;
  if (afterAuto !== null && afterAuto > 0n) {
    bestVoucher = bestVoucherOn(vouchers.filter(program => limitedByPackageOnly(program) && countsForPrice(program)
      && runsAtVoucherBranch(program, pkg)), afterAuto);
  }
  // The best voucher is listed first so a card showing only two labels never hides it.
  const orderedVouchers = bestVoucher
    ? [bestVoucher.program, ...vouchers.filter(program => program !== bestVoucher.program)]
    : vouchers;

  return {
    price,
    discount,
    finalPrice: price === null ? null : price - discount,
    discounted: discount > 0n,
    auto,
    autoForBiggerOrder,
    vouchers: orderedVouchers,
    bestVoucher,
  };
}
