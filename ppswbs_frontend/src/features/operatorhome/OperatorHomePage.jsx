import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useT } from '../../shared/i18n/I18nProvider';
import { roleHomePath } from '../../shared/auth/roleHome';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import StaffHome from './StaffHome';
import { operatorHomeMessages } from './messages';
import '../../ota-connectivity/ota-connectivity.css';
import './operatorHome.css';

/** Home page of Staff and Manager: the work each role does, in one place. Navigation only; the backend decides every permission. */
export default function OperatorHomePage({ role }) {
  const { status } = useAuth();
  const http = useHttp();
  const t = useT(operatorHomeMessages);
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / HOME"
        message={t('page.signIn')}
      />
    );
  }
  if (profile.loading || !profile.data) return <Loading />;
  if (profile.error) return <ErrorNotice error={profile.error} onRetry={profile.reload} />;

  const actualRole = profile.data?.role;
  // Someone who is not Staff or Manager has no internal home; a Staff or Manager opening the other role's home goes to their own.
  if (actualRole !== 'STAFF' && actualRole !== 'MANAGER') return <Navigate to="/" replace />;
  if (actualRole !== role) return <Navigate to={roleHomePath(actualRole)} replace />;

  const name = profile.data?.fullName || profile.data?.email || '';
  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / HOME <span>{t('page.authorized')}</span></div>
        <StaffHome name={name} />
      </main>
    </div>
  );
}
