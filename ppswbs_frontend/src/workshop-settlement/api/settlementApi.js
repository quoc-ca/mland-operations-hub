import { newCommandId } from '../../shared/api/http';

const enc = encodeURIComponent;

/**
 * Per-ticket settlement client (spec 003.3 FR-030–FR-055, contracts/workshop-settlement-ticket.openapi.yaml).
 * Every booking-wide route (/workshop-bookings/{code}/final-bill*, cancellation-cases, folio-corrections,
 * mland-cancellation-remedy-cases, required-session-change-cases) was removed from the backend and is not called.
 * `opts` is merged into the http options; the customer screens pass { auth: false } for a Guest so that no bearer is
 * sent and no contact proof ever travels in a URL.
 */
export function createSettlementApi(http) {
  const write = (path, body, key, opts = {}) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId(), ...opts });
  const ticket = code => `/api/v1/workshop-tickets/${enc(code)}`;
  const group = code => `/api/v1/workshop-booking-groups/${enc(code)}`;
  return {
    // Staff or Manager (bearer): sends the buyer's code to the booking's own email, with no contact value (FR-048, T148).
    requestBuyerVerification: code => http(`${group(code)}/buyer-verifications`, { method: 'POST', body: {} }),

    // Manager case list (route that is still served).
    listCases: (query = {}) => http('/api/v1/workshop-settlement-cases', { query }),

    // Ticket folio (operator view with the bearer; Guest customer view through folio-lookups).
    getFolio: (code, opts) => http(`${ticket(code)}/folio`, opts),
    lookupFolio: (code, proof) => http(`${ticket(code)}/folio-lookups`, { method: 'POST', body: proof, auth: false }),
    recordCollection: (code, body, key) => write(`${ticket(code)}/folio/collections`, body, key),
    confirmTransfer: (code, collectionId, body, key) => write(`${ticket(code)}/folio/collections/${enc(collectionId)}/confirmations`, body, key),
    failCollection: (code, collectionId, body, key) => write(`${ticket(code)}/folio/collections/${enc(collectionId)}/failures`, body, key),
    voidLine: (code, lineId, body, key) => write(`${ticket(code)}/folio/lines/${enc(lineId)}/voids`, body, key),
    voidCollection: (code, collectionId, body, key) => write(`${ticket(code)}/folio/collections/${enc(collectionId)}/voids`, body, key),
    reverseCashCollection: (code, collectionId, body, key) => write(`${ticket(code)}/folio/collections/${enc(collectionId)}/cash-reversals`, body, key),
    recordAbsorbedUndercharge: (code, body, key) => write(`${ticket(code)}/folio/absorbed-undercharges`, body, key),
    voidAbsorbedUndercharge: (code, reference, body, key) => write(`${ticket(code)}/folio/absorbed-undercharges/${enc(reference)}/voids`, body, key),
    grantChangeAllowance: (code, body, key) => write(`${ticket(code)}/change-allowances`, body, key),
    allowHandover:(code, body, key) => write(`${ticket(code)}/folio/handover-exceptions`, body, key),
    listAddOns: code => http(`${ticket(code)}/folio/add-ons`),
    addCharge: (code, body, key) => write(`${ticket(code)}/folio/charges`, body, key, { suppressAccessDenied: true }),
    finalize: (code, key) => write(`${ticket(code)}/folio/finalizations`, undefined, key),
    recordDebt: (code, body, key) => write(`${ticket(code)}/folio/debts`, body, key),
    closeDebt: (code, body, key) => write(`${ticket(code)}/folio/debts/closures`, body, key),
    createFolioCorrection: (code, body, key) => write(`${ticket(code)}/folio/corrections`, body, key),
    getFolioCorrection: (code, ref) => http(`${ticket(code)}/folio/corrections/${enc(ref)}`),

    // Admin-only technical operation (FR-040, A10): after the key is replaced and the service restarted.
    replaceRefundAccountKey: (body, key) => write('/api/v1/workshop-refund-account-key-replacements', body, key),

    // Cash book.
    getExpectedCash: query => http('/api/v1/workshop-cash-closes/expected-cash', { query }),
    // Manager home (013 R-3): the differences waiting for a Manager, exactly what the home card counts.
    listCashClosesAwaitingConfirmation: () => http('/api/v1/workshop-cash-closes', { query: { awaitingConfirmation: true } }),
    closeCash: (body, key) => write('/api/v1/workshop-cash-closes', body, key),
    confirmCashDifference: (cashCloseId, body, key) => write(`/api/v1/workshop-cash-closes/${enc(cashCloseId)}/difference-confirmations`, body, key),

    // Manager read-only lists (FR-056, FR-057).
    listReconciliation: query => http('/api/v1/workshop-reconciliation-collections', { query }),
    listAbsorbedUndercharges: query => http('/api/v1/workshop-absorbed-undercharges', { query }),

    // Bank refunds.
    listRefunds: query => http('/api/v1/workshop-refund-requests', { query }),
    transitionRefund: (refundId, body, key) => write(`/api/v1/workshop-refund-requests/${enc(refundId)}/transitions`, body, key),
    putRefundAccount: (refundId, body, key) => http(`/api/v1/workshop-refund-requests/${enc(refundId)}/account`, { method: 'PUT', body, idempotencyKey: key || newCommandId() }),
    resendAccountLink: (refundId, key) => write(`/api/v1/workshop-refund-requests/${enc(refundId)}/account-link-resends`, undefined, key),
    // T154: optional receipt image. The upload is multipart (the browser sets the boundary); the image is streamed same-origin.
    uploadReceiptImage: (refundId, file, key) => {
      const form = new FormData();
      form.append('file', file);
      return http(`/api/v1/workshop-refund-requests/${enc(refundId)}/receipt-images`, { method: 'POST', form, idempotencyKey: key || newCommandId() });
    },
    getReceiptImage: refundId => http(`/api/v1/workshop-refund-requests/${enc(refundId)}/receipt-image`, { responseType: 'blob', headers: { Accept: 'image/*' } }),
    getRefundAccountNumber: refundId => http(`/api/v1/workshop-refund-requests/${enc(refundId)}/account-number`),
    submitRefundAccount: (token, body, key) => http(`/api/v1/refund-account-links/${enc(token)}`, { method: 'POST', body, auth: false, idempotencyKey: key || newCommandId() }),

    // Cancellation and change (buyer proof: bearer for a Member, buyerVerificationReference otherwise).
    cancelTicket: (code, body, key, opts) => write(`${ticket(code)}/cancellations`, body, key, opts),
    cancelGroupTickets: (groupCode, body, key, opts) => write(`${group(groupCode)}/cancellations`, body, key, opts),
    // FR-044a: the sessions a ticket may move to (operator view with the bearer); `from` repeats up to twice.
    getChangeOptions: (code, { type, from = [] } = {}) => http(`${ticket(code)}/change-options`, { query: { type, from } }),
    changeTicket: (code, body, key, opts) => write(`${ticket(code)}/changes`, body, key, opts),
    changeGroupTickets: (groupCode, body, key, opts) => write(`${group(groupCode)}/ticket-changes`, body, key, opts),
    listChangeRequests: query => http('/api/v1/workshop-change-requests', { query }),
    decideChangeRequest: (reference, body, key) => write(`/api/v1/workshop-change-requests/${enc(reference)}/decisions`, body, key),

    // FR-060, T187: Manager-only read of the add-ons sold at a location the ticket no longer belongs to.
    listPreviousLocationAddOns: code => http(`${ticket(code)}/folio/previous-location-add-ons`),

    // Mland remedy (buyer answer).
    getMlandRemedy: (code, opts) => http(`${ticket(code)}/mland-remedy`, opts),
    answerMlandRemedy: (code, body, key, opts) => write(`${ticket(code)}/mland-remedy/answers`, body, key, opts),
  };
}
