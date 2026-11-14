/**
 * What 006 offers to other features' public pages: read the active public programmes once, work out the offers of a
 * product (002's catalogue, FR-042) or of a workshop package (003.1's package surfaces, FR-045), and draw them.
 * Display only; the checkout or booking quote is the one that counts.
 */
export { usePublicProductPrograms, usePublicWorkshopPrograms } from './application/usePublicProductPrograms';
export { offersForProduct } from './domain/productOffers';
export { offersForPackage } from './domain/workshopOffers';
export { OfferBadges, OfferPrice, ProductOfferList, SizeOfferPrice } from './ui/public/ProductOffers';
export { WorkshopOfferBadges, WorkshopOfferList, WorkshopOfferPrice } from './ui/public/WorkshopOffers';
