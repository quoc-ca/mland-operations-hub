// Menu data holds message keys (see messages.js), not text: the page translates them with useT when it renders.
const item = (to, titleKey, descKey) => ({ to, titleKey, descKey });

// A Staff sees only the sessions a Manager assigned to them.
const mySessions = item('/operations/sessions', 'menu.mySessions', 'menu.mySessionsDesc');

const dailyWork = {
  id: 'daily', titleKey: 'section.daily',
  items: [
    mySessions,
    item('/settlement', 'menu.settlement', 'menu.settlementDesc'),
    item('/ota-connectivity/add-ons', 'menu.otaAddOns', 'menu.otaAddOnsDesc'),
    item('/staff/retail-orders', 'menu.retailOrders', 'menu.retailOrdersDesc'),
    item('/review-moderation', 'menu.reviews', 'menu.reviewsDesc'),
  ],
};

const products = {
  id: 'products', titleKey: 'section.products',
  items: [item('/catalogue-management/products', 'menu.products', 'menu.productsDesc')],
};

const account = {
  id: 'account', titleKey: 'section.account',
  items: [item('/profile', 'menu.profile', 'menu.profileDesc')],
};

/**
 * The Staff home. The Manager home is its own page (feature 010, `features/manager-dashboard`) and no longer a fixed menu.
 */
export function operatorSections() {
  return [dailyWork, products, account];
}
