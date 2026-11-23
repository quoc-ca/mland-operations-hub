import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { Paginator, usePaged } from '../../shared/ui/Paginator';
import { createOperationsApi } from '../api/operationsApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import '../../ota-connectivity/ota-connectivity.css';
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
  const locationName = id => locations.find(item => String(item.id) === String(id))?.name || (id ? `Chi nhánh #${id}` : '—');
  const sessions = Array.isArray(resource.data) ? resource.data : resource.data?.content || resource.data?.items || [];
  const paged = usePaged(sessions, 15, resource.data);
  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Authentication required to access operations."
      />
    );
  }
  const counts = sessions.reduce((acc, item) => { acc[item.state] = (acc[item.state] || 0) + 1; return acc; }, {});
  return <div className="ota-workspace">
    
    <main className="ota-main"><div className="ota-topline">MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span></div><header className="ota-page-heading"><div><p>{isStaff ? 'Chỉ hiện các ca bạn được Manager phân công.' : 'Coordinate capacity, check-in and attendance finalization across active sessions.'}</p>{isStaff && (
        <p className="muted" style={{ marginTop: '6px' }}>
          Chi nhánh của bạn: <strong>{locationsResource.loading ? 'đang tải…' : (locations.length ? locations.map(item => item.name).join(', ') : 'chưa được gán chi nhánh nào, liên hệ Manager')}</strong>
        </p>
      )}</div></header>
      <section className="ops-metrics"><div><span>All sessions</span><strong>{sessions.length}</strong></div><div><span>Published</span><strong>{counts.PUBLISHED || 0}</strong></div><div><span>In progress</span><strong>{counts.IN_PROGRESS || 0}</strong></div><div><span>Finalized</span><strong>{counts.ATTENDANCE_FINALIZED || 0}</strong></div></section>
      <section className="ota-panel"><form className="ota-inline-form ops-filter-form" onSubmit={e => { e.preventDefault(); resource.reload(); }}><label>From<input type="date" value={filters.from} onChange={e => setFilters({ ...filters, from: e.target.value })} /></label><label>To<input type="date" value={filters.to} onChange={e => setFilters({ ...filters, to: e.target.value })} /></label><label>State<select value={filters.state} onChange={e => setFilters({ ...filters, state: e.target.value })}><option value="">All states</option>{['PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED'].map(v => <option key={v}>{v}</option>)}</select></label><label>Location<select value={filters.locationId} onChange={e => setFilters({ ...filters, locationId: e.target.value })}><option value="">All locations</option>{locations.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button className="button outline" type="button" onClick={() => setFilters({ state: "", locationId: "", from: "", to: "" })}>Xóa lọc</button></form></section>
      <ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} />{resource.loading ? <Loading /> : sessions.length === 0 ? <div className="empty-state"><p>{isStaff ? 'Bạn chưa được phân công ca nào (hoặc không có ca nào khớp bộ lọc). Liên hệ Manager để được phân công.' : 'No sessions match the selected filters.'}</p></div> : <section className="ota-panel"><div className="ota-panel-head"><h2>Session queue</h2><span>{sessions.length} records</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Date</th><th>Time</th><th>Session</th><th>Location</th><th>Capacity</th><th>State</th><th /></tr></thead><tbody>{paged.pageItems.map(item => <tr key={item.id}><td>{item.workshopDate || '—'}</td><td><strong>{sessionTimeRange(item) || '—'}</strong></td><td><strong>{item.slotId || item.id}</strong><small>{item.id}</small></td><td>{item.locationName || locationName(item.locationId)}</td><td>{item.capacity ?? '—'}</td><td><span className="ops-status">{item.state}</span></td><td><Link className="button outline small" to={`/operations/sessions/${item.id}`}>Open</Link></td></tr>)}</tbody></table></div><Paginator page={paged.page} pageCount={paged.pageCount} onChange={paged.setPage} label="Phân trang bảng điều phối ca" /></section>}
    </main></div>;
}
