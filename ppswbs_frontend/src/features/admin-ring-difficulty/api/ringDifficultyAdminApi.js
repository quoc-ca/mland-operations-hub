import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';

const BASE = '/api/v1/admin/workshop-ring-difficulty';

/**
 * Feature-012 Admin Ring Difficulty Policy REST adapter.
 * Calls the approved OpenAPI endpoints using the shared Firebase-authenticated HTTP client.
 */
export function createRingDifficultyAdminApi(request) {
  return {
    getActive(signal) {
      return request(`${BASE}/active`, { method: 'GET', signal });
    },
    listVersions(signal) {
      return request(`${BASE}/versions`, { method: 'GET', signal });
    },
    preview(payload, signal) {
      return request(`${BASE}/preview`, {
        method: 'POST',
        body: payload,
        signal,
      });
    },
    publish(payload, idempotencyKey, signal) {
      return request(`${BASE}/versions`, {
        method: 'POST',
        body: payload,
        idempotencyKey,
        signal,
      });
    },
    getCatalogueOptions(signal) {
      return request('/api/v1/workshop-design-options', {
        method: 'GET',
        auth: false,
        signal,
      });
    },
  };
}

export function useRingDifficultyAdminApi() {
  const http = useHttp();
  return useMemo(() => createRingDifficultyAdminApi(http), [http]);
}
