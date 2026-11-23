import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useLanguage } from '../../shared/i18n/I18nProvider';
import { roleHomePath } from '../../shared/auth/roleHome';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import StaffHome from './StaffHome';
import '../../ota-connectivity/ota-connectivity.css';
import './operatorHome.css';

/** Home page of Staff and Manager: the work each role does, in one place. Navigation only; the backend decides every permission. */
export default function OperatorHomePage({ role }) {
  const { status } = useAuth();
  const http = useHttp();
  const { language } = useLanguage();
  const vi = language === 'vi';
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / HOME"
        message={vi ? 'Đăng nhập bằng tài khoản Staff hoặc Manager để vào trang chủ.' : 'Sign in with a Staff or Manager account to open your home page.'}
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
        <div className="ota-topline">MLAND / HOME <span>● Backend-authorized</span></div>
        <StaffHome name={name} vi={vi} />
      </main>
    </div>
  );
}
