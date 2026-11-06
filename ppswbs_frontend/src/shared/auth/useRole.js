import { useEffect, useState } from 'react';
import { useAuth } from './AuthProvider';
import { useHttp } from '../api/useHttp';

// The role of the signed-in account, kept for the page's lifetime so every screen does not refetch the profile.
// It is cleared when the session ends, so the next user never sees the previous one's role.
let cachedRole = null;

/** For tests: forget the remembered role. */
export function clearRoleCache() {
  cachedRole = null;
}

/**
 * The signed-in account's role (MEMBER, STAFF, MANAGER, ADMIN), or null while it is unknown or when there is no session.
 * Navigation only: the backend still decides every permission.
 */
export function useRole() {
  const { status } = useAuth();
  const http = useHttp();
  const [role, setRole] = useState(status === 'signed-in' ? cachedRole : null);

  useEffect(() => {
    if (status !== 'signed-in') {
      cachedRole = null;
      setRole(null);
      return undefined;
    }
    if (cachedRole) {
      setRole(cachedRole);
      return undefined;
    }
    let active = true;
    http('/api/v1/accounts/me/profile')
      .then(profile => {
        cachedRole = profile?.role || null;
        if (active) setRole(cachedRole);
      })
      .catch(() => { if (active) setRole(null); });
    return () => { active = false; };
  }, [status, http]);

  return role;
}
