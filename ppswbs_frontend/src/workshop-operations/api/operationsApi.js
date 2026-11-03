import { newCommandId } from '../../shared/api/http';

export function createOperationsApi(http) {
  const command = (path, body, key) => http(path, { method: 'POST', body, idempotencyKey: key || newCommandId() });
  return {
    listSessions: query => http('/api/v1/workshop-sessions', { query }),
    getSession: id => http(`/api/v1/workshop-sessions/${id}`),
    transition: (id, targetState, key) => command(`/api/v1/workshop-sessions/${id}/state-transitions`, { targetState }, key),
    finalizeAttendance: (id, key) => command(`/api/v1/workshop-sessions/${id}/attendance-finalizations`, { confirmedSessionEnded: true }, key),
    checkIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-ins`, body, key),
    lookupBooking: (id, bookingCode) => http(`/api/v1/workshop-sessions/${id}/booking-lookups/${encodeURIComponent(bookingCode)}`),
    correctAttendance: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/attendance-corrections`, body, key),
    reverseCheckIn: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/check-in-reversals`, body, key),
    cancelSession: (id, body, key) => command(`/api/v1/workshop-sessions/${id}/cancellation-requests`, body, key),
  };
}
