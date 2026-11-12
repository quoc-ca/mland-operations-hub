import { newCommandId } from '../../../shared/api/http';

const id = value => encodeURIComponent(value);
const CART = '/api/v1/members/me/ready-ring-cart';

/**
 * Member-facing FE-08 API. Prices, stock and fulfilment remain server-authoritative;
 * the client only submits the member's selected variant, location and quantity.
 */
export function retailApi(request) {
  return {
    getCart: () => request(CART),
    addLine: body => request(`${CART}/lines`, { method: 'POST', body }),
    updateLine: (lineId, quantity) => request(`${CART}/lines/${id(lineId)}`, { method: 'PATCH', body: { quantity } }),
    removeLine: lineId => request(`${CART}/lines/${id(lineId)}`, { method: 'DELETE' }),

    listOrders: () => request('/api/v1/members/me/ready-ring-orders'),
    getOrder: orderId => request(`/api/v1/members/me/ready-ring-orders/${id(orderId)}`),
    listPaymentReconciliations: page => request('/api/v1/manager/retail-payment-reconciliations', { query: { page } }),

    listStaffShipments: (query = {}) => request('/api/v1/staff/retail-shipments', { query }),
    getStaffShipment: shipmentId => request(`/api/v1/staff/retail-shipments/${id(shipmentId)}`),
    transitionStaffShipment: (shipmentId, body) => request(`/api/v1/staff/retail-shipments/${id(shipmentId)}/transitions`, { method: 'POST', body }),

    listBranches: () => request('/api/v1/product-selling-locations', { auth: false }),
    // Counter cash (FR-08-045..047): Staff pickup desk, cash confirmation, pickup milestones, Manager daily cash.
    listStaffPickups: locationId => request('/api/v1/staff/retail-pickups', { query: locationId ? { locationId } : {} }),
    confirmCashCollection: (orderId, amountReceived, key = newCommandId()) => request(`/api/v1/ready-ring-orders/${id(orderId)}/cash-collections`, { method: 'POST', body: { amountReceived }, idempotencyKey: key }),
    prepareOrder: orderId => request(`/api/v1/ready-ring-orders/${id(orderId)}/preparation`, { method: 'POST' }),
    handOverOrder: orderId => request(`/api/v1/ready-ring-orders/${id(orderId)}/pickup-confirmations`, { method: 'POST' }),
    dailyCash: (locationId, businessDate) => request('/api/v1/manager/retail-cash-collections', { query: { locationId, businessDate } }),
    listSavedVouchers: () => request('/api/v1/members/me/saved-vouchers', { query: { limit: 100 } }),
    getLoyaltyAccount: () => request('/api/v1/members/me/loyalty-accounts/current'),
    previewBenefits: body => request('/api/v1/ready-ring-benefit-previews', { method: 'POST', body }),
    quoteShipping: body => request('/api/v1/ready-ring-shipping-quotes', { method: 'POST', body }),
    createCheckout: (body, key = newCommandId()) => request('/api/v1/ready-ring-orders', { method: 'POST', body, idempotencyKey: key }),
    initiatePayment: (checkoutId, key = newCommandId()) => request(`/api/v1/ready-ring-checkouts/${id(checkoutId)}/payment`, { method: 'POST', idempotencyKey: key }),
    cancelCheckout: (checkoutId, key = newCommandId()) => request(`/api/v1/ready-ring-checkouts/${id(checkoutId)}/cancellations`, { method: 'POST', idempotencyKey: key }),
  };
}
