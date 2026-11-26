import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { useHttp } from '../../shared/api/useHttp';
import { useEnumLabel, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { Paginator, usePaged } from '../../shared/ui/Paginator';
import { createOperationsApi } from '../api/operationsApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import '../../shared/ui/workspace.css';
import '../workshop-operations.css';

/** Today as yyyy-mm-dd in the browser's local calendar (the board opens on today's and later sessions). */
const localToday = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** "09:30 – 12:00" of a session, in the viewer's time zone; empty when the times are unknown. */
export const sessionTimeRange = item => {
  const clock = value => {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime()) ? date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }) : '';
  };
  const start = clock(item?.scheduledStartAt);
  const end = clock(item?.scheduledEndAt);
  return start && end ? `${start} – ${end}` : start;
};

export default function OperationsDashboardPage() {
  const t = useT(messages);
  const stateLabel = useEnumLabel(messages, 'sessionState');
  const { status } = useAuth();
  // A Staff member sees only the sessions a Manager assigned to them (the backend filters the list).
  const isStaff = useRole() === 'STAFF';
  const api = createOperationsApi(useHttp());
  const [filters, setFilters] = useUrlFilters({ state: '', locationId: '', from: localToday(), to: '' });
  const resource = useResource(() => api.listSessions(filters), [filters.state, filters.locationId, filters.from, filters.to]);
  // Locations the signed-in Staff or Manager may see: the board shows their names instead of bare ids.
  const http = useHttp();
  const locationsResource = useResource(() => createOperationsAdminApi(http).listLocations(), []);
  const locations = Array.isArray(locationsResource.data) ? locationsResource.data : [];
  const locationName = id => locations.find(item => String(item.id) === String(id))?.name || (id ? t('board.branchFallback', { id }) : '—');
  const sessions = Array.isArray(resource.data) ? resource.data : resource.data?.content || resource.data?.items || [];
  const paged = usePaged(sessions, 15, resource.data);
  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline={t('topline.operations')}
        message={t('board.signIn')}
      />
    );
  }
  const counts = sessions.reduce((acc, item) => { acc[item.state] = (acc[item.state] || 0) + 1; return acc; }, {});
  return <div className="wsp-workspace">
    
    <main className="wsp-main"><div className="wsp-topline">{t('topline.operations')} <span>● {t('authorized')}</span></div><header className="wsp-page-heading"><div><p>{isStaff ? t('board.staffIntro') : t('board.managerIntro')}</p>{isStaff && (
        <p className="muted" style={{ marginTop: '6px' }}>
          {t('board.yourBranches')} <strong>{locationsResource.loading ? t('board.loadingLower') : (locations.length ? locations.map(item => item.name).join(', ') : t('board.noBranch'))}</strong>
        </p>
      )}</div></header>
      <section className="ops-metrics"><div><span>{t('board.metric.all')}</span><strong>{sessions.length}</strong></div><div><span>{stateLabel('PUBLISHED')}</span><strong>{counts.PUBLISHED || 0}</strong></div><div><span>{stateLabel('IN_PROGRESS')}</span><strong>{counts.IN_PROGRESS || 0}</strong></div><div><span>{t('board.metric.finalized')}</span><strong>{counts.ATTENDANCE_FINALIZED || 0}</strong></div></section>
      <section className="wsp-panel"><form className="wsp-inline-form ops-filter-form" onSubmit={e => { e.preventDefault(); resource.reload(); }}><label>{t('admin.from')}<input type="date" value={filters.from} onChange={e => setFilters({ ...filters, from: e.target.value })} /></label><label>{t('admin.to')}<input type="date" value={filters.to} onChange={e => setFilters({ ...filters, to: e.target.value })} /></label><label>{t('admin.state')}<select value={filters.state} onChange={e => setFilters({ ...filters, state: e.target.value })}><option value="">{t('admin.allStates')}</option>{['PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED'].map(v => <option key={v} value={v}>{stateLabel(v)}</option>)}</select></label><label>{t('admin.location')}<select value={filters.locationId} onChange={e => setFilters({ ...filters, locationId: e.target.value })}><option value="">{t('admin.allLocations')}</option>{locations.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button className="button outline" type="button" onClick={() => setFilters({ state: "", locationId: "", from: "", to: "" })}>{t('admin.clearFilters')}</button></form></section>
      <ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} />{resource.loading ? <Loading /> : sessions.length === 0 ? <div className="empty-state"><p>{isStaff ? t('board.emptyStaff') : t('board.emptyManager')}</p></div> : <section className="wsp-panel"><div className="wsp-panel-head"><h2>{t('admin.sessionQueue')}</h2><span>{t('records', { count: sessions.length })}</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>{t('admin.col.date')}</th><th>{t('admin.col.time')}</th><th>{t('admin.col.session')}</th><th>{t('admin.col.location')}</th><th>{t('admin.col.capacity')}</th><th>{t('admin.state')}</th><th /></tr></thead><tbody>{paged.pageItems.map(item => <tr key={item.id}><td>{item.workshopDate || '—'}</td><td><strong>{sessionTimeRange(item) || '—'}</strong></td><td title={item.id}><code>{String(item.id).slice(0, 8)}</code></td><td>{item.locationName || locationName(item.locationId)}</td><td>{item.capacity ?? '—'}</td><td><span className="ops-status">{stateLabel(item.state)}</span></td><td><Link className="button outline small" to={`/operations/sessions/${item.id}`}>{t('board.open')}</Link></td></tr>)}</tbody></table></div><Paginator page={paged.page} pageCount={paged.pageCount} onChange={paged.setPage} label={t('board.pagination')} /></section>}
    </main></div>;
}
