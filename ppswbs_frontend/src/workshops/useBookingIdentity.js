import { useEffect, useState } from 'react';
import { useAuth } from '../shared/auth/AuthProvider';
import { useHttp } from '../shared/api/useHttp';

/**
 * Guest or Member, derived from the session plus the canonical account profile (approved 2026-11-03 journey
 * correction). A signed-in account whose profile cannot be read, or whose canonical role is not MEMBER, is
 * fail-closed as `null` instead of being downgraded to the Guest journey. The backend still decides authorization.
 */
export function useBookingIdentity() {
  const { status: authStatus, getIdToken } = useAuth();
  const http = useHttp();
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState(null);

  useEffect(() => {
    let active = true;
    if (authStatus !== 'signed-in') {
      setProfile(null);
      setProfileLoading(false);
      setProfileError(null);
      return () => { active = false; };
    }
    setProfileLoading(true);
    setProfileError(null);
    http('/api/v1/accounts/me/profile')
      .then(result => { if (active) setProfile(result || null); })
      .catch(error => {
        if (!active) return;
        setProfile(null);
        setProfileError(error?.message || 'Không thể tải hồ sơ Member.');
      })
      .finally(() => { if (active) setProfileLoading(false); });
    return () => { active = false; };
  }, [authStatus, http]);

  const mode = authStatus === 'signed-out' || authStatus === 'unconfigured'
    ? 'GUEST'
    : profile?.role === 'MEMBER' ? 'MEMBER' : null;

  const operatorRole = ['STAFF', 'MANAGER', 'ADMIN'].includes(profile?.role) ? profile.role : null;

  /** The Member bearer for an owned-group route, or null for a Guest. */
  const memberAuthorization = async () => {
    if (mode !== 'MEMBER') return null;
    let token = null;
    try { token = await getIdToken(false); } catch { /* fall through to a forced refresh */ }
    if (!token) {
      try { token = await getIdToken(true); } catch { token = null; }
    }
    return token ? `Bearer ${token}` : null;
  };

  return { authStatus, mode, profile, profileLoading, profileError, operatorRole, memberAuthorization };
}
