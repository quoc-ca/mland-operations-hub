import { newCommandId } from '../../shared/api/http';

export function createOperationsAdminApi(http) {
  const command = (path, method, body, key) => http(path, {
    method,
    body,
    idempotencyKey: key || newCommandId(),
  });

  return {
    listLocations: () => http('/api/v1/workshop-locations'),
    createLocation: (body, key) => command('/api/v1/workshop-locations', 'POST', body, key),
    listSlots: locationId => http(`/api/v1/workshop-locations/${encodeURIComponent(locationId)}/slots`),
    createSlot: (locationId, body, key) => command(`/api/v1/workshop-locations/${encodeURIComponent(locationId)}/slots`, 'POST', body, key),
    updateSlot: (slotId, body, key) => command(`/api/v1/workshop-slots/${encodeURIComponent(slotId)}`, 'PATCH', body, key),
    listSessions: query => http('/api/v1/workshop-sessions', { query }),
    createSession: (body, key) => command('/api/v1/workshop-sessions', 'POST', body, key),
    updateSession: (sessionId, body, key) => command(`/api/v1/workshop-sessions/${encodeURIComponent(sessionId)}`, 'PATCH', body, key),
    transitionSession: (sessionId, targetState, key) => command(`/api/v1/workshop-sessions/${encodeURIComponent(sessionId)}/state-transitions`, 'POST', { targetState }, key),
  };
}
