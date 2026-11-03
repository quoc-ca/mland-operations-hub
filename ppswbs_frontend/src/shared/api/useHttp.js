import { useMemo } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { createHttpClient } from './http';

/** HTTP client bound to the current session. A 401 after a token refresh ends the session (proposal §7). */
export function useHttp() {
  const { getIdToken, signOut } = useAuth();
  return useMemo(() => createHttpClient({ getIdToken, onSessionExpired: () => { signOut(); } }), [getIdToken, signOut]);
}
