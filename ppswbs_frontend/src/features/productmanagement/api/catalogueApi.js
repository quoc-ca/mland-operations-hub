// REST operations of 002 (contracts/product-management.openapi.json). IDs, amounts and quantities are decimal
// strings; expectedVersion is a JSON number. Every write takes the Idempotency-Key of its user action.
const PUBLIC = '/api/v1';
const MANAGEMENT = '/api/v1/catalogue-management';
const id = value => encodeURIComponent(value);

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

    // Review and publication (T034, T040)
    submitReview: (key, productId, expectedVersion) => write('POST', `/products/${id(productId)}/review-submissions`, key, { expectedVersion }),
    decideReview: (key, productId, body) => write('POST', `/products/${id(productId)}/review-decisions`, key, body),
    setPublication: (key, productId, expectedVersion, published) => write('PUT', `/products/${id(productId)}/publication`, key, { expectedVersion, published }),

    // Sizes and prices (T050, T057, T064)
    setQuantity: (key, variantId, expectedVersion, quantity) => write('PUT', `/variants/${id(variantId)}/quantity`, key, { expectedVersion, quantity }),
    setSaleStatus: (key, variantId, expectedVersion, saleEnabled) => write('PUT', `/variants/${id(variantId)}/sale-status`, key, { expectedVersion, saleEnabled }),
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

    // Excel import (T101)
    downloadImportTemplate: () => request(`${MANAGEMENT}/product-imports/template`, { responseType: 'blob' }),
    validateImport: (key, file) => {
      const form = new FormData();
      form.append('file', file);
      return request(`${MANAGEMENT}/product-imports`, { method: 'POST', idempotencyKey: key, form });
    },
    getImport: batchId => read(`/product-imports/${id(batchId)}`),
    confirmImport: (key, batchId, expectedVersion) => write('POST', `/product-imports/${id(batchId)}/confirmations`, key, { expectedVersion }),
    downloadImportErrors: batchId => request(`${MANAGEMENT}/product-imports/${id(batchId)}/error-report`, { responseType: 'blob' }),
  };
}

/** Same-origin media path from the public catalogue, served by the backend. */
export function mediaUrl(baseUrl, url) {
  if (!url) return null;
  return url.startsWith('http') ? url : `${baseUrl}${url}`;
}
