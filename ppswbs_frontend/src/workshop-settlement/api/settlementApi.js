import { newCommandId } from '../../shared/api/http';
export function createSettlementApi(http) {
  const write = (path, body, key) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId() });
  return {
    listCases: (query = {}) => http('/api/v1/workshop-settlement-cases', { query }),
    getFinalBill: code => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill`),
    createFinalBill: (code, body = {}, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill`, body, key),
    addCharge: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/charges`, body, key),
    createDirectAddonBill: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/direct-addon-bills`, body, key),
    startDirectAddonPayment: (code, billReference, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/direct-addon-bills/${encodeURIComponent(billReference)}/payment-attempts`, {}, key),
    getDirectAddonBill: (code, billReference, query) => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/direct-addon-bills/${encodeURIComponent(billReference)}`, { query }),
    finalize: (code, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/finalizations`, {}, key),
    startPayment: (code, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/payment-attempts`, {}, key),
    openCancellation: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/cancellation-cases`, body, key),
    openMlandRemedy: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/mland-cancellation-remedy-cases`, body, key),
    getCancellation: (code, ref, query) => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/cancellation-cases/${encodeURIComponent(ref)}`, { query }),
    getRequiredSessionChange: (code, ref, query) => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/required-session-change-cases/${encodeURIComponent(ref)}`, { query }),
    getMlandRemedy: (code, ref, query) => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/mland-cancellation-remedy-cases/${encodeURIComponent(ref)}`, { query }),
    openRequiredSessionChange: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/required-session-change-cases`, body, key),
    listRefundPolicies: () => http('/api/v1/workshop-refund-policies'),
    createRefundPolicy: (body, key) => write('/api/v1/workshop-refund-policies', body, key),
  };
}
