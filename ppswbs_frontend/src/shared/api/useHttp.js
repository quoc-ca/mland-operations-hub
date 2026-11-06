import { useMemo } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { createHttpClient } from './http';

/** HTTP client bound to the current session (FR-041 / spec-general.md §2). */
export function useHttp() {
  const { getIdToken, signOut, reportSessionLost, reportAccessDenied } = useAuth();
  return useMemo(() => createHttpClient({
    getIdToken,
    onSessionExpired: () => {
      reportSessionLost?.();
      signOut?.().catch?.(() => {});
    },
    onAccessDenied: () => {
      reportAccessDenied?.();
    },
  }), [getIdToken, signOut, reportSessionLost, reportAccessDenied]);
}
