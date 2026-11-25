import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { roleHomePath } from '../../shared/auth/roleHome';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { HomeBlocks } from './operations';
import { useT } from '../../shared/i18n/I18nProvider';
import RevenueReport from './RevenueReport';
import { managerDashboardMessages } from './messages';
import '../../ota-connectivity/ota-connectivity.css';
import './managerDashboard.css';

/**
 * Manager dashboard (spec 010 and 014, navigation decision of 2026-11-22): today's operations, the month's revenue with
 * the branch table, and recent events to look at, each loading and failing on its own. The waiting-work counts are
 * badges on the navigation rail. The role check is navigation only; the backend decides every permission.
 */
export default function ManagerDashboardPage() {
  const { status } = useAuth();
  const http = useHttp();
  const t = useT(managerDashboardMessages);
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') return <OperationsAuthWorkspace topline="MLAND / DASHBOARD" message={t('page.signIn')} />;
  if (profile.error) return <ErrorNotice error={profile.error} onRetry={profile.reload} />;
  if (profile.loading || !profile.data) return <Loading />;

  const role = profile.data.role;
  // Anyone who is not a Manager sees none of the figures and is sent to their own home (a Member or Admin: the general home).
  if (role !== 'MANAGER') return <Navigate to={roleHomePath(role)} replace />;

  return (
    <div className="ota-workspace">
      <main className="ota-main manager-dashboard mh-page">
        <HomeBlocks revenue={(
          <section aria-labelledby="mh-revenue" className="mh-section">
            <h2 id="mh-revenue" className="mh-section-title">{t('page.revenueTitle')}</h2>
            <RevenueReport />
          </section>
        )} />
      </main>
    </div>
  );
}
