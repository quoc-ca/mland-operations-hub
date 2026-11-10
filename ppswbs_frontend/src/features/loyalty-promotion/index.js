// Public port of the loyalty-promotion feature (FRONTEND-ARCHITECTURE §2): the only module other code may import.
// BenefitSelector is the offer picker that the FE-08 (retail) and FE-03 (workshop) checkout screens embed.
export { loyaltyPromotionRoutes } from './routes/loyaltyPromotionRoutes';
export { BenefitSelector } from './ui/checkout/BenefitSelector';
// Public offers for other features' product pages (002 catalogue, FR-058 / 006 FR-042): display only.
export { OfferBadges, OfferPrice, offersForProduct, ProductOfferList, SizeOfferPrice, usePublicProductPrograms } from './publicOffers';
