import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { asList } from '../ui';

/**
 * Manager case list. The Mland refund policy was removed (FR-043: Mland always refunds 100%), as were case detail by
 * booking and the whole-booking cancellation, remedy and required-session-change routes (FR-047, FR-048): per-ticket
 * work lives on the ticket folio, refund queue, ticket actions and change-request screens, so this page only lists
 * cases the server still serves.
 */
export default function SettlementCasesPage() {
  const api = createSettlementApi(useHttp());
  const [filters, setFilters] = useState({ caseType: '', status: '' });
  const caseList = useResource(() => api.listCases({ ...(filters.caseType ? { caseType: filters.caseType } : {}), ...(filters.status ? { status: filters.status } : {}) }), [filters.caseType, filters.status]);
  const cases = useMemo(() => asList(caseList.data), [caseList.data]);

  return (
    <OpsPage title="Settlement cases" intro="Manager-only case list with safe projections; payment and provider evidence stay behind the backend boundary.">
      <ErrorNotice error={caseList.error} onRetry={() => caseList.reload()} />
      <section className="ota-panel">
        <div className="ota-panel-head"><h2>All settlement cases</h2><button className="button secondary" type="button" onClick={() => caseList.reload()}>Refresh</button></div>
        <div className="ota-inline-form">
          <label>Type
            <select value={filters.caseType} onChange={event => setFilters(v => ({ ...v, caseType: event.target.value }))}>
              <option value="">All</option>
              <option value="CUSTOMER_CANCELLATION">Customer cancellation</option>
              <option value="REQUIRED_SESSION_CHANGE">Required session change</option>
              <option value="MLAND_CANCELLATION_REMEDY">Mland remedy</option>
            </select>
          </label>
          <label>Status
            <select value={filters.status} onChange={event => setFilters(v => ({ ...v, status: event.target.value }))}>
              <option value="">All</option>
              <option value="OPEN">Open</option>
              <option value="PENDING_PROVIDER">Pending provider</option>
              <option value="RESOLVED">Resolved</option>
              <option value="REJECTED">Rejected</option>
              <option value="FAILED">Failed</option>
            </select>
          </label>
        </div>
        {caseList.loading ? <Loading /> : caseList.error ? null : cases.length === 0 ? <div className="empty-state"><p>No settlement cases found.</p></div> : (
          <table className="ops-table">
            <thead><tr><th>Case</th><th>Type</th><th>Status</th><th>Booking</th></tr></thead>
            <tbody>{cases.map(item => <tr key={item.caseReference}><td>{item.caseReference}</td><td>{item.caseType}</td><td><span className="ops-status">{item.status}</span></td><td>{item.bookingCode}</td></tr>)}</tbody>
          </table>
        )}
        <p className="muted small">Refunds are handled in the <Link to="/settlement/refunds">refund queue</Link>; ticket changes in the <Link to="/settlement/change-requests">change request queue</Link>.</p>
      </section>
    </OpsPage>
  );
}
