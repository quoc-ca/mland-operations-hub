import React from 'react';
import { useT } from '../i18n/I18nProvider';
import { Loading } from '../ui/components';
import { sharedMessages } from '../ui/messages';
import { useAuth } from './AuthProvider';
import { SignInLink } from './SignInLink';

/**
 * Renders children only with a Firebase session; otherwise the sign-in notice (the sign-in screen belongs to 001).
 * It is navigation only: the backend still decides every permission.
 */
export function RequireSession({ children, requiredText }) {
  const t = useT(sharedMessages);
  const { status } = useAuth();
  if (status === 'loading') return <Loading />;
  if (status === 'signed-in') return children;
  return (
    <div className="empty-state" role="status">
      <p>{status === 'unconfigured' ? t('session.unconfigured') : requiredText || t('error.AUTHENTICATION_REQUIRED')}</p>
      <SignInLink>{t('session.signIn')}</SignInLink>
    </div>
  );
}
