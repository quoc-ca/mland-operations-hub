/**
 * 003.2 US7 (FR-047 to FR-055): the two read-only audit routes the Manager screen calls independently.
 * Operations rows come from workshopoperations, Settlement rows from workshopsettlement (003.3). Empty filter values
 * are dropped by the shared client; the backend decides the 7-day default window and every permission.
 */
export const OPERATIONS_AUDIT_PATH = '/api/v1/workshop-audit/operations-events';
export const SETTLEMENT_AUDIT_PATH = '/api/v1/workshop-audit/settlement-records';

export function createAuditReviewApi(http) {
  return {
    readOperationsEvents: ({ action, outcome, actorId, sessionId, from, to, cursor, limit } = {}, options = {}) => http(
      OPERATIONS_AUDIT_PATH,
      { query: { action, outcome, actorId, sessionId, from, to, cursor, limit }, ...options },
    ),
    readSettlementRecords: ({ actionCode, actorId, aggregateType, aggregateReference, from, to, cursor, limit } = {}, options = {}) => http(
      SETTLEMENT_AUDIT_PATH,
      { query: { actionCode, actorId, aggregateType, aggregateReference, from, to, cursor, limit }, ...options },
    ),
  };
}
