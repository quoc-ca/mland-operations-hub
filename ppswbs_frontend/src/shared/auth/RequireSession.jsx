import React from 'react';
import { Navigate } from 'react-router';
import { useT } from '../i18n/I18nProvider';
import { Loading } from '../ui/components';
import { sharedMessages } from '../ui/messages';
import { useAuth } from './AuthProvider';
import { SignInLink } from './SignInLink';

/**
 * Shared session guard for protected routes (FR-041 / spec-general.md §2 / T071).
 * It is navigation only: the backend still decides every permission.
 * - 'loading': renders neutral non-PII loading indicator without exposing any private content.
 * - 'unconfigured': renders safe configuration-unavailable notice with no redirect loop.
 * - 'signed-out': if a terminal outcome (sign-out or session loss) is being handled by SessionNavigationBoundary,
 *   renders null to allow Home replace navigation;
 *   if requiredText is provided (inline feature section guard), renders inline sign-in prompt;
 *   otherwise (guest direct access to protected route) navigates to /login with replace.
 * - 'signed-in': renders children.
 */
export function RequireSession({ children, requiredText }) {
  const t = useT(sharedMessages);
  const { status, isTerminalSession } = useAuth();

  if (status === 'loading') {
    return (
      <div className="session-loading-state" role="status" aria-busy="true">
        <Loading />
      </div>
    );
  }

  if (status === 'unconfigured') {
    return (
      <div className="empty-state session-unconfigured-state" role="status">
        <p>{t('session.unconfigured') || 'Dịch vụ xác thực chưa sẵn sàng.'}</p>
      </div>
    );
  }

  if (status === 'signed-out') {
    if (requiredText) {
      return (
        <div className="empty-state" role="status">
          <p>{requiredText}</p>
          <SignInLink>{t('session.signIn')}</SignInLink>
        </div>
      );
    }
    if (isTerminalSession) {
      return null;
    }
    return <Navigate to="/login" replace />;
  }

  if (status === 'signed-in') {
    return children;
  }

  return null;
}

export default RequireSession;
