import { newCommandId } from '../../../shared/api/http';

const enc = (val) => encodeURIComponent(val);

/**
 * REST API client for FE-007 Notification Management.
 * Follows contracts/notification-api.md exactly.
 * All requests require Bearer token (supplied by useHttp / AuthProvider).
 */
export function notificationApiClient(request) {
  return {
    // 1. Template Registry & Version Endpoints
    listTemplates: () => request('/api/v1/notification-templates'),

    getTemplate: (code) => request(`/api/v1/notification-templates/${enc(code)}`),

    createDraftVersion: (code, body) =>
      request(`/api/v1/notification-templates/${enc(code)}/versions`, {
        method: 'POST',
        body,
      }),

    updateDraftVersion: (code, versionNumber, body) =>
      request(
        `/api/v1/notification-templates/${enc(code)}/versions/${enc(versionNumber)}`,
        {
          method: 'PUT',
          body,
        }
      ),

    activateVersion: (code, versionNumber) =>
      request(
        `/api/v1/notification-templates/${enc(code)}/versions/${enc(versionNumber)}/activate`,
        {
          method: 'POST',
        }
      ),

    previewTemplate: (code, body) =>
      request(`/api/v1/notification-templates/${enc(code)}/preview`, {
        method: 'POST',
        body,
      }),

    // 2. Notification Operations & Delivery Diagnostics Endpoints
    listNotificationRequests: (query = {}) =>
      request('/api/v1/notification-requests', {
        query,
      }),

    getNotificationRequest: (id) =>
      request(`/api/v1/notification-requests/${enc(id)}`),

    resendNotificationRequest: (id, body, key = newCommandId()) =>
      request(`/api/v1/notification-requests/${enc(id)}/resend`, {
        method: 'POST',
        body,
        idempotencyKey: key,
      }),

    cancelNotificationRequest: (id, body, key = newCommandId()) =>
      request(`/api/v1/notification-requests/${enc(id)}/cancel`, {
        method: 'POST',
        body,
        idempotencyKey: key,
      }),

    redeliverNotificationRequest: (id, body, key = newCommandId()) =>
      request(`/api/v1/notification-requests/${enc(id)}/redeliver`, {
        method: 'POST',
        body,
        idempotencyKey: key,
      }),
  };
}

