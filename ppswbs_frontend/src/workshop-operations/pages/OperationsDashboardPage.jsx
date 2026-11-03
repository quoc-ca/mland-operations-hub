import React, { useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { createOperationsApi } from '../api/operationsApi';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

export default function OperationsDashboardPage() {
  const { status } = useAuth();
  const api = createOperationsApi(useHttp());
  const [filters, setFilters] = useState({ state: '', locationId: '', from: '', to: '' });
  const resource = useResource(() => api.listSessions(filters), [filters.state, filters.locationId, filters.from, filters.to]);
  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') return <div className="ota-container"><div className="empty-state"><p>Authentication required to access operations.</p><a className="button primary" href="/login">Sign In</a></div></div>;
  const sessions = Array.isArray(resource.data) ? resource.data : resource.data?.content || resource.data?.items || [];
  const counts = sessions.reduce((acc, item) => { acc[item.state] = (acc[item.state] || 0) + 1; return acc; }, {});
  return <div className="ota-workspace">
    <aside className="ota-sidebar"><div className="ota-brand"><span>M</span><strong>MLAND</strong><small>operations hub</small></div><div className="ota-sidebar-label">WORKSHOP OPERATIONS</div><Link className="active" to="/operations/sessions">Session board</Link><Link to="/ota-connectivity/reconciliation">Reconciliation</Link><Link to="/settlement">Settlement</Link></aside>
    <main className="ota-main"><div className="ota-topline">MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span></div><header className="ota-page-heading"><div><p className="ota-eyebrow">TODAY'S WORK</p><h1>Workshop session board</h1><p>Coordinate capacity, check-in and attendance finalization across active sessions.</p></div></header>
      <section className="ops-metrics"><div><span>All sessions</span><strong>{sessions.length}</strong></div><div><span>Published</span><strong>{counts.PUBLISHED || 0}</strong></div><div><span>In progress</span><strong>{counts.IN_PROGRESS || 0}</strong></div><div><span>Finalized</span><strong>{counts.ATTENDANCE_FINALIZED || 0}</strong></div></section>
      <section className="ota-panel"><form className="ota-inline-form" onSubmit={e => { e.preventDefault(); resource.reload(); }}><label>From<input type="date" value={filters.from} onChange={e => setFilters({ ...filters, from: e.target.value })} /></label><label>To<input type="date" value={filters.to} onChange={e => setFilters({ ...filters, to: e.target.value })} /></label><label>State<select value={filters.state} onChange={e => setFilters({ ...filters, state: e.target.value })}><option value="">All states</option>{['PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED'].map(v => <option key={v}>{v}</option>)}</select></label><label>Location ID<input value={filters.locationId} onChange={e => setFilters({ ...filters, locationId: e.target.value })} placeholder="Optional" /></label><button className="button primary" type="submit">Refresh board</button></form></section>
      <ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} />{resource.loading ? <Loading /> : sessions.length === 0 ? <div className="empty-state"><p>No sessions match the selected filters.</p></div> : <section className="ota-panel"><div className="ota-panel-head"><h2>Session queue</h2><span>{sessions.length} records</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Date</th><th>Session</th><th>Location</th><th>Capacity</th><th>State</th><th /></tr></thead><tbody>{sessions.map(item => <tr key={item.id}><td>{item.workshopDate || '—'}</td><td><strong>{item.slotId || item.id}</strong><small>{item.id}</small></td><td>{item.locationId || '—'}</td><td>{item.capacity ?? '—'}</td><td><span className="ops-status">{item.state}</span></td><td><Link className="button outline small" to={`/operations/sessions/${item.id}`}>Open</Link></td></tr>)}</tbody></table></div></section>}
    </main></div>;
}
