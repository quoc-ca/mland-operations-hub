import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { RequireSession } from '../../shared/auth/RequireSession';
import { RoleArea } from '../../shared/auth/RoleArea';
import { useHttp } from '../../shared/api/useHttp';
import { roleHomePath } from '../../shared/auth/roleHome';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import OperatorHomePage from './OperatorHomePage';
import { operatorHomeMessages } from './messages';

/** `/home` sends a signed-in user to the home page of their role (the sidebar link points here); guests sign in first. */
function RoleHomeRedirect() {
  const { status } = useAuth();
  const http = useHttp();
  const t = useT(sharedMessages);
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);
  if (profile.error) {
    return (
      <>
        <ErrorNotice error={profile.error} />
        <button type="button" className="button outline" onClick={profile.reload}>{t('retry')}</button>
      </>
    );
  }
  if (profile.loading || !profile.data) return <Loading />;
  return <Navigate to={roleHomePath(profile.data?.role)} replace />;
}

/** Staff home (014 allowlist): STAFF only; everyone else signed in is replaced to /404. */
function StaffHomeRoute() {
  const t = useT(operatorHomeMessages);
  return (
    <RoleArea roles={['STAFF']} requiredText={t('page.signIn')}>
      <OperatorHomePage role="STAFF" />
    </RoleArea>
  );
}

export const operatorHomeRoutes = [
  { path: '/staff/home', element: <StaffHomeRoute /> },
  { path: '/home', element: <RequireSession><RoleHomeRedirect /></RequireSession> },
];
