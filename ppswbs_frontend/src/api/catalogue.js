const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080')).replace(/\/$/, '');

function request(path, params = {}) {
  const origin = apiBaseUrl || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${origin}${normalizedPath}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return fetch(url, { headers: { Accept: 'application/json', 'ngrok-skip-browser-warning': '1' } }).then(async response => {
    if (!response.ok) throw new Error(`CATALOGUE_${response.status}`);
    return response.json();
  });
}

export function listPublicProducts(filters) { return request('/api/v1/products', filters); }
export function listPublicCategories() { return request('/api/v1/product-categories'); }
export function getPublicProduct(productId) { return request(`/api/v1/products/${encodeURIComponent(productId)}`); }
export function publicMediaUrl(url) { return url?.startsWith('http') ? url : url ? `${apiBaseUrl}${url}` : null; }
