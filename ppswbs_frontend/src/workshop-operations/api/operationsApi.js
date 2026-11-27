import { newCommandId } from '../../shared/api/http';

export function createOperationsApi(http) {
  const command = (path, body, key, opts = {}) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId(), ...opts });
  return {
    listSessions: query => http('/api/v1/workshop-sessions', { query }),
    // opts.suppressAccessDenied: a 403 on a read that only decorates another page (e.g. a session at another branch) must not send the user home.
    getSession: (id, opts = {}) => http(`/api/v1/workshop-sessions/${id}`, opts),
    transition: (id, targetState, key) => command(`/api/v1/workshop-sessions/${id}/state-transitions`, { targetState }, key, { suppressAccessDenied: true }),
    finalizeAttendance: (id, key) => command(`/api/v1/workshop-sessions/${id}/attendance-finalizations`, { confirmedSessionEnded: true }, key),
    checkIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-ins`, body, key),
    lookupBooking: (id, bookingCode) => http(`/api/v1/workshop-sessions/${id}/booking-lookups/${encodeURIComponent(bookingCode)}`),
    attendanceRoster: id => http(`/api/v1/workshop-sessions/${id}/attendance-roster`),
    folioStatuses: ticketCodes => command('/api/v1/workshop-ticket-folio-statuses', { ticketCodes }, null, { suppressAccessDenied: true }),
    resendTickets: (id, bookingCode, key) => command(`/api/v1/workshop-sessions/${id}/ticket-resends`, { bookingCode }, key),
    correctAttendance: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/attendance-corrections`, body, key),
    reverseCheckIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-in-reversals`, body, key),
    assignStaff: (id, accountId, key) => http(`/api/v1/workshop-sessions/${id}/staff-assignments`, { method: 'PUT', body: { accountId: Number(accountId) }, idempotencyKey: key || newCommandId() }),
    // confirmLastAssignmentRemoval (T115): sent only when the Manager confirmed removing the session's last Staff; repeat it with a new key.
    removeStaff: (id, accountId, key, confirmLastAssignmentRemoval = false) => http(`/api/v1/workshop-sessions/${id}/staff-assignments`, { method: 'DELETE', query: confirmLastAssignmentRemoval ? { accountId, confirmLastAssignmentRemoval: true } : { accountId }, idempotencyKey: key || newCommandId() }),
    cancelSession: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/cancellation-requests`, body, key),
    // 003.2 T113 / FR-035: Manager sets (quota ≥ 0) or clears (null) the session's reschedule quota; reason required.
    setRescheduleQuota: (id, body, key) => http(`/api/v1/workshop-sessions/${id}/reschedule-quota`, { method: 'PUT', body, idempotencyKey: key || newCommandId() }),
  };
}
