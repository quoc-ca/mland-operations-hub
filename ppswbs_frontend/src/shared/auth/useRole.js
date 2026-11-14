import { useEffect, useState } from 'react';
import { useAuth } from './AuthProvider';
import { useHttp } from '../api/useHttp';

// The role of the signed-in account, kept for the page's lifetime so every screen does not refetch the profile.
// It is kept for ONE account: when another account signs in (the same browser tab is often used for a Member, then a
// Manager) the remembered role is dropped, so the next user never sees the previous one's role or menu.
let cached = { userId: null, role: null };

/** For tests: forget the remembered role. */
export function clearRoleCache() {
  cached = { userId: null, role: null };
}

const KNOWN_NOBODY = '__unknown__';

/**
 * The signed-in account's role (MEMBER, STAFF, MANAGER, ADMIN), or null while it is unknown or when there is no session.
 * Navigation only: the backend still decides every permission.
 */
export function useRole() {
  const { status, userId } = useAuth();
  const http = useHttp();
  const who = userId ?? KNOWN_NOBODY;
  const remembered = () => (cached.userId === who ? cached.role : null);
  const [role, setRole] = useState(status === 'signed-in' ? remembered() : null);

  useEffect(() => {
    if (status !== 'signed-in') {
      clearRoleCache();
      setRole(null);
      return undefined;
    }
    if (cached.userId === who && cached.role) {
      setRole(cached.role);
      return undefined;
    }
    // Another account (or none remembered yet): forget the old role at once and ask for this account's.
    if (cached.userId !== who) clearRoleCache();
    setRole(null);
    let active = true;
    http('/api/v1/accounts/me/profile')
      .then(profile => {
        const next = profile?.role || null;
        cached = { userId: who, role: next };
        if (active) setRole(next);
      })
      .catch(() => { if (active) setRole(null); });
    return () => { active = false; };
  }, [status, who, http]);

  return role;
}
