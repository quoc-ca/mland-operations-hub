const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class RetailApiError extends Error {
  constructor(code, message, correlationId) { super(message); this.code = code; this.correlationId = correlationId; }
}

async function protectedRequest(path, { method = 'GET', token, body, idempotencyKey } = {}) {
  if (!token) throw new RetailApiError('AUTHENTICATION_REQUIRED', 'Đăng nhập Member để tiếp tục.');
  const response = await fetch(`${apiBaseUrl}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) }, body: body ? JSON.stringify(body) : undefined });
  if (!response.ok) { const payload = await response.json().catch(() => ({})); throw new RetailApiError(payload.code || `HTTP_${response.status}`, payload.message || 'Không thể xử lý yêu cầu.', payload.correlationId); }
  return response.status === 204 ? undefined : response.json();
}

// The Firebase/session owner will pass the current ID token into these calls.
export function createRetailClient(getIdToken) {
  const token = () => getIdToken?.();
  return {
    getCart: () => protectedRequest('/api/v1/members/me/ready-ring-cart', { token: token() }),
    addCartLine: body => protectedRequest('/api/v1/members/me/ready-ring-cart/lines', { method: 'POST', token: token(), body }),
    updateCartLine: (lineId, body) => protectedRequest(`/api/v1/members/me/ready-ring-cart/lines/${encodeURIComponent(lineId)}`, { method: 'PATCH', token: token(), body }),
    removeCartLine: lineId => protectedRequest(`/api/v1/members/me/ready-ring-cart/lines/${encodeURIComponent(lineId)}`, { method: 'DELETE', token: token() }),
    previewBenefits: body => protectedRequest('/api/v1/ready-ring-benefit-previews', { method: 'POST', token: token(), body }),
    quoteShipping: body => protectedRequest('/api/v1/ready-ring-shipping-quotes', { method: 'POST', token: token(), body }),
    createCheckout: (body, idempotencyKey) => protectedRequest('/api/v1/ready-ring-orders', { method: 'POST', token: token(), body, idempotencyKey }),
    initiatePayment: checkoutId => protectedRequest(`/api/v1/ready-ring-checkouts/${encodeURIComponent(checkoutId)}/payment`, { method: 'POST', token: token() }),
    listOrders: () => protectedRequest('/api/v1/members/me/ready-ring-orders', { token: token() }),
  };
}
