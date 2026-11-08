// REST operations of 006 (contracts/loyalty-promotion.openapi.json). IDs, amounts, points, percentages and
// versions are strings; expectedVersion is a JSON string here. Every write takes the key of its user action.
const id = value => encodeURIComponent(value);

export function loyaltyApi(request) {
  const write = (method, path, key, body) => request(path, { method, idempotencyKey: key, body });

  return {
    // Public promotions (T070)
    listPublicPromotions: ({ scope, cursor, limit } = {}) => request('/api/v1/promotions', { query: { scope, cursor, limit }, auth: false }),
    getPublicPromotion: promotionId => request(`/api/v1/promotions/${id(promotionId)}`, { auth: false }),

    // Member (T040, T070)
    getOwnAccount: () => request('/api/v1/members/me/loyalty-accounts/current'),
    listOwnTransactions: ({ cursor, limit } = {}) => request('/api/v1/members/me/loyalty-transactions', { query: { cursor, limit } }),
    listSavedVouchers: ({ cursor, limit } = {}) => request('/api/v1/members/me/saved-vouchers', { query: { cursor, limit } }),
    saveVoucher: (key, programId) => request(`/api/v1/members/me/saved-vouchers/${id(programId)}`, { method: 'PUT', idempotencyKey: key }),

    // Manager programmes (T030, T047)
    listPrograms: ({ scope, status, cursor, limit } = {}) => request('/api/v1/promotion-programs', { query: { scope, status, cursor, limit } }),
    getProgram: programId => request(`/api/v1/promotion-programs/${id(programId)}`),
    createProgram: (key, body) => write('POST', '/api/v1/promotion-programs', key, body),
    updateProgram: (key, programId, body) => write('PATCH', `/api/v1/promotion-programs/${id(programId)}`, key, body),
    pauseProgram: (key, programId, expectedVersion) => write('POST', `/api/v1/promotion-programs/${id(programId)}/pauses`, key, { expectedVersion }),
    resumeProgram: (key, programId, expectedVersion) => write('POST', `/api/v1/promotion-programs/${id(programId)}/resumptions`, key, { expectedVersion }),
    getUsage: programId => request(`/api/v1/promotion-programs/${id(programId)}/usage`),

    // Workshop targets for the Manager picker: read-only use of 003 reads (packages, branches, sessions).
    listWorkshopPackages: () => request('/api/v1/workshop-package-management/packages'),
    listWorkshopBranches: () => request('/api/v1/workshop-locations'),
    listWorkshopSessions: ({ from } = {}) => request('/api/v1/workshop-sessions', { query: { from } }),

    // Product targets for the Manager picker: read-only use of 002 management reads (productmanagement REST).
    searchCatalogueProducts: ({ q, page } = {}) => request('/api/v1/catalogue-management/products', { query: { q, page } }),
    getCatalogueProduct: productId => request(`/api/v1/catalogue-management/products/${id(productId)}`),

    // Names of product targets on public and Member pages: 002's public catalogue (published products only).
    listPublicProducts: ({ page } = {}) => request('/api/v1/products', { query: { page }, auth: false }),
    getPublicProduct: productId => request(`/api/v1/products/${id(productId)}`, { auth: false }),

    // Names of workshop targets (no sign-in): a published package from 003 Workshop Operations, and the active
    // branches (workshop_locations) as 002 lists them for buyers. A voucher's branch IDs are these location IDs.
    getPublicWorkshopPackage: packageId => request(`/api/v1/workshop-packages/${id(packageId)}`, { auth: false }),
    listPublicBranches: () => request('/api/v1/product-selling-locations', { auth: false }),

    // Manager point policy (T034)
    getPolicy: () => request('/api/v1/loyalty-policies/current'),
    updatePolicy: (key, body) => write('PUT', '/api/v1/loyalty-policies/current', key, body),
  };
}
