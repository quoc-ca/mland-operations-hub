import { newCommandId } from '../../shared/api/http';

export function createOperationsApi(http) {
  const command = (path, body, key) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId() });
  return {
    listSessions: query => http('/api/v1/workshop-sessions', { query }),
    getSession: id => http(`/api/v1/workshop-sessions/${id}`),
    transition: (id, targetState, key) => command(`/api/v1/workshop-sessions/${id}/state-transitions`, { targetState }, key),
    finalizeAttendance: (id, key) => command(`/api/v1/workshop-sessions/${id}/attendance-finalizations`, { confirmedSessionEnded: true }, key),
    checkIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-ins`, body, key),
    overrideCheckIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-in-overrides`, body, key),
    lookupBooking: (id, bookingCode) => http(`/api/v1/workshop-sessions/${id}/booking-lookups/${encodeURIComponent(bookingCode)}`),
    attendanceRoster: id => http(`/api/v1/workshop-sessions/${id}/attendance-roster`),
    resendTickets: (id, bookingCode, key) => command(`/api/v1/workshop-sessions/${id}/ticket-resends`, { bookingCode }, key),
    correctAttendance: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/attendance-corrections`, body, key),
    reverseCheckIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-in-reversals`, body, key),
    assignStaff: (id, accountId, key) => http(`/api/v1/workshop-sessions/${id}/staff-assignments`, { method: 'PUT', body: { accountId: Number(accountId) }, idempotencyKey: key || newCommandId() }),
    removeStaff: (id, accountId, key) => http(`/api/v1/workshop-sessions/${id}/staff-assignments`, { method: 'DELETE', query: { accountId }, idempotencyKey: key || newCommandId() }),
    cancelSession: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/cancellation-requests`, body, key),
    // 003.2 T113 / FR-035: Manager sets (quota ≥ 0) or clears (null) the session's reschedule quota; reason required.
    setRescheduleQuota: (id, body, key) => http(`/api/v1/workshop-sessions/${id}/reschedule-quota`, { method: 'PUT', body, idempotencyKey: key || newCommandId() }),
  };
}
