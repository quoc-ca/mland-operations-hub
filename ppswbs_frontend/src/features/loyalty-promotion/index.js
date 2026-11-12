// Public port of the loyalty-promotion feature (FRONTEND-ARCHITECTURE §2): the only module other code may import.
// BenefitSelector is the offer picker FE-08 (retail) and FE-03 (workshop) may embed in their checkout screens. Kept on
// purpose (converge T102, 2026-11-12): no owner embeds it today (FE-08 uses RetailBenefitPanel, FE-03 its own panel),
// but it still carries the vi/en reason texts of T081 and stays available for them.
export { loyaltyPromotionRoutes } from './routes/loyaltyPromotionRoutes';
export { BenefitSelector } from './ui/checkout/BenefitSelector';
// Public offers for other features' product pages (002 catalogue, FR-058 / 006 FR-042): display only.
export { OfferBadges, OfferPrice, offersForProduct, ProductOfferList, SizeOfferPrice, usePublicProductPrograms } from './publicOffers';
