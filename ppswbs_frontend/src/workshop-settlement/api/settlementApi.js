import { newCommandId } from '../../shared/api/http';
export function createSettlementApi(http) {
  const write = (path, body, key) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId() });
  return {
    getFinalBill: code => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill`),
    createFinalBill: (code, body = {}) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill`, body),
    addCharge: (code, body) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/charges`, body),
    finalize: (code, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/finalizations`, {}, key),
    startPayment: (code, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/final-bill/payment-attempts`, {}, key),
    openCancellation: (code, body, key) => write(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/cancellation-cases`, body, key),
    getCancellation: (code, ref, query) => http(`/api/v1/workshop-bookings/${encodeURIComponent(code)}/cancellation-cases/${encodeURIComponent(ref)}`, { query }),
  };
}
