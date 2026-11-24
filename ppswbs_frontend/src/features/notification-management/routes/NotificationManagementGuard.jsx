import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useRole } from '../../../shared/auth/useRole';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { notificationMessages } from '../messages';

export function NotificationManagementGuard({ children }) {
  const t = useT(notificationMessages);
  const { status } = useAuth();
  const role = useRole();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        {t('guard.checking')}
      </div>
    );
  }

  if (status === 'signed-out') {
    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  }

  // Allow MANAGER and ADMIN; if role is not yet loaded, show loading
  if (role === null && status === 'signed-in') {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        {t('guard.verifying')}
      </div>
    );
  }

  if (role && role !== 'ADMIN' && role !== 'MANAGER') {
    return (
      <div className="ota-workspace">
        
        <main className="ota-main">
          <div className="nm-container">
            <div className="nm-alert nm-alert-danger" role="alert" style={{ marginTop: '2rem' }}>
              <h3 style={{ margin: '0 0 0.5rem 0' }}>{t('guard.deniedTitle')}</h3>
              <p style={{ margin: 0 }}>
                {t('guard.deniedBody')}
              </p>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return children;
}

export default NotificationManagementGuard;

