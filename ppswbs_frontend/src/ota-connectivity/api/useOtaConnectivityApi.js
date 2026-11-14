import { useMemo } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { createOtaConnectivityClient } from './otaConnectivityApi';

/**
 * React hook providing the scoped OTA Connectivity API client bound to the active session.
 */
export function useOtaConnectivityApi() {
  const http = useHttp();
  return useMemo(() => createOtaConnectivityClient(http), [http]);
}

