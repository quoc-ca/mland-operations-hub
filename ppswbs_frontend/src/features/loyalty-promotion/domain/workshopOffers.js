import { big, createdFirst, unitDiscount } from './productOffers';

/**
 * Offers shown on 003.1's public workshop package surfaces (006 FR-045, 003.1 FR-052/FR-052a), from 006's public
 * workshop programmes (GET /api/v1/promotions?scope=WORKSHOP, active now). Display only: the amount a customer pays
 * is always the one POST /api/v1/workshop-cart-quotes computes.
 *
 * The rules follow FR-042 for products, applied to ONE ticket of a package:
 * - an automatic discount covers a package when its packageIds include it (it has no other dimension, FR-044);
 * - a voucher covers a package when it has no packageIds or they include it; its branch, date and session limits
 *   cannot be checked before the customer picks them, so they do not narrow the label;
 * - D = min(price, cap, floor(price × percent / 100)) or min(price, cap, fixed), and 0 below the minimum package fee
 *   (the same unitDiscount as the product offers, so both pages round the same way);
 * - the largest automatic discount is taken off the price; on equal discounts the programme created first wins;
 * - an automatic discount whose minimum fee is above the price of one ticket is not taken off; it is listed as one
 *   "for orders from …" instead;
 * - vouchers are never taken off (Member-only, with quotas): they are labels. Points are never part of the price.
 */

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

  return {
    price,
    discount,
    finalPrice: price === null ? null : price - discount,
    discounted: discount > 0n,
    auto,
    autoForBiggerOrder,
    vouchers,
  };
}
