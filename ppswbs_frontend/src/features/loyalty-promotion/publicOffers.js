/**
 * What 006 offers to other features' public product pages (used by 002's catalogue): read the active public product
 * programmes once, work out a product's offers, and draw them. Display only; the checkout quote is the one that counts.
 */
export { usePublicProductPrograms } from './application/usePublicProductPrograms';
export { offersForProduct } from './domain/productOffers';
export { OfferBadges, OfferPrice, ProductOfferList, SizeOfferPrice } from './ui/public/ProductOffers';
