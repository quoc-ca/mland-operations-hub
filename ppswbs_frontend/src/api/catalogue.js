const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

function request(path, params = {}) {
  const url = new URL(`${apiBaseUrl}${path}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return fetch(url, { headers: { Accept: 'application/json' } }).then(async response => {
    if (!response.ok) throw new Error(`CATALOGUE_${response.status}`);
    return response.json();
  });
}

export function listPublicProducts(filters) { return request('/api/v1/products', filters); }
export function listPublicCategories() { return request('/api/v1/product-categories'); }
export function getPublicProduct(productId) { return request(`/api/v1/products/${encodeURIComponent(productId)}`); }
export function publicMediaUrl(url) { return url?.startsWith('http') ? url : url ? `${apiBaseUrl}${url}` : null; }
