// REST operations of 002 (contracts/product-management.openapi.json). IDs, amounts and quantities are decimal
// strings; expectedVersion is a JSON number. Every write takes the Idempotency-Key of its user action.
const PUBLIC = '/api/v1';
const MANAGEMENT = '/api/v1/catalogue-management';
const id = value => encodeURIComponent(value);

/** Backend action names of the writes, the scope of a command receipt (FR-028). */
export const ACTIONS = {
  createProduct: 'product.create', editProduct: 'product.edit', deleteProduct: 'product.delete', images: 'product.images',
  submitReview: 'product.review.submit', decideReview: 'product.review.decide',
  publish: 'product.publish', unpublish: 'product.unpublish',
  quantity: 'variant.quantity', saleStatus: 'variant.sale-status',
  addBranch: 'product.branch.add', branchQuantity: 'variant.branch.quantity', branchSale: 'product.branch.sale',
  proposePrice: 'price.propose', revisePrice: 'price.revise', submitPrice: 'price.submit', decidePrice: 'price.decide', applyPrice: 'price.apply',
  createCategory: 'category.create', updateCategory: 'category.update', categoryActivation: 'category.activation',
};

export function catalogueApi(request) {
  const publicRead = (path, query) => request(`${PUBLIC}${path}`, { query, auth: false });
  const read = (path, query) => request(`${MANAGEMENT}${path}`, { query });
  const write = (method, path, key, body, query) => request(`${MANAGEMENT}${path}`, { method, idempotencyKey: key, body, query });

  return {
    // Public catalogue (T071)
    listPublicProducts: filters => publicRead('/products', filters),
    getPublicProduct: productId => publicRead(`/products/${id(productId)}`),
    listPublicCategories: page => publicRead('/product-categories', { page }),
    listSellingLocations: () => publicRead('/product-selling-locations'),

    // Hand-off to FE-08's cart (T072): the Member's bearer, body { variantId, locationId, quantity } only.
    addCartLine: body => request('/api/v1/members/me/ready-ring-cart/lines', { method: 'POST', body }),

    // Products (T028, T050, T064)
    listProducts: filters => read('/products', filters),
    getProduct: productId => read(`/products/${id(productId)}`),
    createProduct: (key, body) => write('POST', '/products', key, body),
    updateProduct: (key, productId, body) => write('PUT', `/products/${id(productId)}`, key, body),
    deleteProduct: (key, productId, expectedVersion) => write('DELETE', `/products/${id(productId)}`, key, undefined, { expectedVersion }),
    replaceImages: (key, productId, body) => write('PUT', `/products/${id(productId)}/images`, key, body),
    uploadMedia: (key, file) => {
      const form = new FormData();
      form.append('file', file);
      return request(`${MANAGEMENT}/media-uploads`, { method: 'POST', idempotencyKey: key, form });
    },
    // Staff preview of a product image (draft/hidden included); fetched with the bearer, shown from a blob URL.
    getMediaPreview: mediaId => request(`${MANAGEMENT}/media/${id(mediaId)}`, { responseType: 'blob' }),

    // Review and publication (T034, T040)
    submitReview: (key, productId, expectedVersion) => write('POST', `/products/${id(productId)}/review-submissions`, key, { expectedVersion }),
    decideReview: (key, productId, body) => write('POST', `/products/${id(productId)}/review-decisions`, key, body),
    setPublication: (key, productId, expectedVersion, published) => write('PUT', `/products/${id(productId)}/publication`, key, { expectedVersion, published }),

    // Sizes and prices (T050, T057, T064)
    setQuantity: (key, variantId, expectedVersion, quantity) => write('PUT', `/variants/${id(variantId)}/quantity`, key, { expectedVersion, quantity }),
    setSaleStatus: (key, variantId, expectedVersion, saleEnabled) => write('PUT', `/variants/${id(variantId)}/sale-status`, key, { expectedVersion, saleEnabled }),

    // Branches (US10, Session 2026-11-08). The list is the canonical branch list of 003.2, already limited to the
    // Manager's active branches or the Staff member's assigned ones; the backend checks the scope again.
    listBranches: () => request('/api/v1/workshop-locations'),
    addBranch: (key, productId, expectedVersion, locationId, quantities) => write('POST', `/products/${id(productId)}/branches`, key, { expectedVersion, locationId, quantities }),
    setBranchQuantity: (key, variantId, locationId, expectedVersion, quantity) => write('PUT', `/variants/${id(variantId)}/branches/${id(locationId)}/quantity`, key, { expectedVersion, quantity }),
    setBranchSale: (key, productId, locationId, saleEnabled, expectedSaleEnabled) => write('PUT', `/products/${id(productId)}/branches/${id(locationId)}/sale-status`, key, { saleEnabled, expectedSaleEnabled }),
    proposePrice: (key, variantId, expectedVersion, proposedPrice) => write('POST', `/variants/${id(variantId)}/price-proposals`, key, { expectedVersion, proposedPrice }),
    revisePrice: (key, proposalId, expectedVersion, proposedPrice) => write('POST', `/price-proposals/${id(proposalId)}/revisions`, key, { expectedVersion, proposedPrice }),
    submitPrice: (key, proposalId, expectedVersion) => write('POST', `/price-proposals/${id(proposalId)}/submissions`, key, { expectedVersion }),
    decidePrice: (key, proposalId, body) => write('POST', `/price-proposals/${id(proposalId)}/decisions`, key, body),
    applyPrice: (key, proposalId, expectedVersion) => write('POST', `/price-proposals/${id(proposalId)}/applications`, key, { expectedVersion }),

    // Categories (T079)
    listCategories: page => read('/categories', { page }),
    createCategory: (key, body) => write('POST', '/categories', key, body),
    updateCategory: (key, categoryId, body) => write('PUT', `/categories/${id(categoryId)}`, key, body),
    categoryImpact: (categoryId, page) => read(`/categories/${id(categoryId)}/impact`, { page }),
    setCategoryActive: (key, categoryId, expectedVersion, active) => write('PUT', `/categories/${id(categoryId)}/activation`, key, { expectedVersion, active }),

    // Did a write whose answer was lost commit? (FR-028) 404 COMMAND_RESULT_NOT_FOUND when there is no receipt.
    getCommandReceipt: (commandId, action) => read(`/command-receipts/${id(commandId)}`, { action }),

    // Excel import (T101)
    // US10 Q-B2: locationIds are the chosen branches in column order (one "Số lượng {branch}" column each).
    downloadImportTemplate: (locationIds = []) => request(`${MANAGEMENT}/product-imports/template`, {
      responseType: 'blob', query: locationIds.length ? { locationIds: locationIds.join(',') } : undefined,
    }),
    validateImport: (key, file, locationIds = []) => {
      const form = new FormData();
      form.append('file', file);
      locationIds.forEach(locationId => form.append('locationIds', locationId));
      return request(`${MANAGEMENT}/product-imports`, { method: 'POST', idempotencyKey: key, form });
    },
    getImport: batchId => read(`/product-imports/${id(batchId)}`),
    confirmImport: (key, batchId, expectedVersion) => write('POST', `/product-imports/${id(batchId)}/confirmations`, key, { expectedVersion }),
    downloadImportErrors: batchId => request(`${MANAGEMENT}/product-imports/${id(batchId)}/error-report`, { responseType: 'blob' }),

    // Every category, for a picker or filter: the list endpoints answer 20 per page (A-002), so a select built from
    // one page would hide the 21st category onwards.
    listAllCategories: () => allPages(page => read('/categories', { page })),
    listAllPublicCategories: () => allPages(page => publicRead('/product-categories', { page })),
  };
}

/** Pages are zero-based; stops at the last page (totalElements) or at an empty page, at most MAX_PAGES pages. */
const MAX_PAGES = 100;
async function allPages(readPage) {
  const items = [];
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const data = await readPage(page);
    const pageItems = data?.items || [];
    items.push(...pageItems);
    const total = Number(data?.totalElements);
    const pageSize = Number(data?.pageSize);
    if (!pageItems.length || (Number.isFinite(total) && items.length >= total)
      || !Number.isFinite(total) || (Number.isFinite(pageSize) && pageItems.length < pageSize)) break;
  }
  return { items, totalElements: String(items.length) };
}

/** Same-origin media path from the public catalogue, served by the backend. */
export function mediaUrl(baseUrl, url) {
  if (!url) return null;
  return url.startsWith('http') ? url : `${baseUrl}${url}`;
}
