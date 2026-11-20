import React, { useState } from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useHttp } from '../../../shared/api/useHttp';
import { roleHomePath } from '../../../shared/auth/roleHome';
import { useLanguage, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { OperationsSidebar } from '../../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../../shared/ui/OperationsAuthWorkspace';
import { useHomeCards } from '../application/useHomeCards';
import { formatVietnamTime } from '../application/vietnamTime';
import { managerHomeMessages } from './messages';
import WaitingWorkSection from './WaitingWorkSection';
import TodayStrip from './TodayStrip';
import RevenueCard from './RevenueCard';
import RecentEvents from './RecentEvents';
import Shortcuts from './Shortcuts';
import '../../../ota-connectivity/ota-connectivity.css';
import './managerHome.css';

/**
 * Manager Home: what needs the Manager today. It only reads and links (spec FR-012). The role check below is navigation;
 * the backend decides every permission, read by read (FR-001).
 */
export default function ManagerHomePage() {
  const { status } = useAuth();
  const http = useHttp();
  const t = useT(managerHomeMessages);
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') return <OperationsAuthWorkspace topline="MLAND / HOME" message={t('signIn')} />;
  if (profile.error) return <ErrorNotice error={profile.error} onRetry={profile.reload} />;
  if (profile.loading || !profile.data) return <Loading />;

  const role = profile.data.role;
  // Anyone who is not a Manager sees none of the figures and is sent to their own home (a Member or Admin: the general home).
  if (role !== 'MANAGER') return <Navigate to={roleHomePath(role)} replace />;

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main mh-page">
        <div className="ota-topline">MLAND / HOME <span>● Backend-authorized</span></div>
        <HomeContent name={profile.data.fullName || profile.data.email || ''} />
      </main>
    </div>
  );
}

function HomeContent({ name }) {
  const t = useT(managerHomeMessages);
  const { language } = useLanguage();
  const [branch, setBranch] = useState('all'); // kept for the visit only, not a saved preference (FR-008)
  const cards = useHomeCards({ branch });

  return (
    <>
      <header className="ota-page-heading">
        <div>
          <p className="operator-home-hello">{t('hello')}{name ? `, ${name}` : ''}</p>
          <h1>{t('title')}</h1>
          <p>{t('subtitle')}</p>
        </div>
        <div className="mh-refresh">
          <button type="button" className="button outline" onClick={cards.reloadAll}>{t('refresh')}</button>
          {cards.lastUpdatedAt && <span role="status">{t('lastUpdated', { time: formatVietnamTime(cards.lastUpdatedAt, language) })}</span>}
        </div>
      </header>
      <WaitingWorkSection queues={cards.queues} section={cards.section} onRetry={cards.reloadSection} />
      <TodayStrip
        strip={cards.section('strip')}
        locations={cards.section('locations')}
        branch={branch}
        onBranchChange={setBranch}
        onRetry={cards.reloadSection}
      />
      <RevenueCard revenue={cards.section('revenue')} onRetry={cards.reloadSection} />
      <RecentEvents events={cards.section('events')} onRetry={cards.reloadSection} />
      <Shortcuts />
    </>
  );
}
