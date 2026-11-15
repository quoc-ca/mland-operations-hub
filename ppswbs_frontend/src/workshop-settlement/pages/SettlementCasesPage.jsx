import React, { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { asList } from '../ui';

/**
 * Manager case list and refund-policy history. Case detail by booking and the whole-booking cancellation, remedy and
 * required-session-change routes were removed (FR-047, FR-048): per-ticket work lives on the ticket folio, refund queue,
 * ticket actions and change-request screens, so this page only lists cases the server still serves.
 */
export default function SettlementCasesPage() {
  const api = createSettlementApi(useHttp());
  const [params] = useSearchParams();
  const tab = params.get('tab') === 'policies' ? 'policies' : 'cases';
  const [filters, setFilters] = useState({ caseType: '', status: '' });
  const [effectiveAt, setEffectiveAt] = useState('');
  const [refundPercent, setRefundPercent] = useState('0');
  const [notice, setNotice] = useState('');
  const [localError, setLocalError] = useState(null);
  const command = useCommand();
  const caseList = useResource(() => api.listCases({ ...(filters.caseType ? { caseType: filters.caseType } : {}), ...(filters.status ? { status: filters.status } : {}) }), [filters.caseType, filters.status]);
  const policyList = useResource(() => api.listRefundPolicies(), [tab]);
  const cases = useMemo(() => asList(caseList.data), [caseList.data]);
  const policies = useMemo(() => asList(policyList.data), [policyList.data]);

  const createPolicy = async event => {
    event.preventDefault();
    const percent = Number(refundPercent);
    if (!effectiveAt || !Number.isInteger(percent) || percent < 0 || percent > 100) {
      setLocalError({ message: 'Enter an effective date and a refund percentage from 0 to 100.' });
      return;
    }
    setLocalError(null);
    const out = await command.run(key => api.createRefundPolicy({ effectiveAt: new Date(effectiveAt).toISOString(), wholeBookingRefundPercent: percent }, key));
    if (out.ok) { setNotice('Refund policy revision created.'); policyList.reload(); }
  };

  return (
    <OpsPage title={tab === 'policies' ? 'Refund policy' : 'Settlement cases'} intro="Manager-only case list with safe projections; payment and provider evidence stay behind the backend boundary.">
      <nav className="ota-nav-tabs" aria-label="Settlement sub-navigation">
        <Link className={`ota-nav-tab ${tab === 'cases' ? 'active' : ''}`} to="/settlement/cases?tab=cases">Settlement cases</Link>
        <Link className={`ota-nav-tab ${tab === 'policies' ? 'active' : ''}`} to="/settlement/cases?tab=policies">Refund policy</Link>
      </nav>
      <SuccessNotice>{notice}</SuccessNotice>
      <ErrorNotice error={localError || command.error || caseList.error || policyList.error} onRetry={() => { caseList.reload(); policyList.reload(); }} />
      {tab === 'cases' && (
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
      )}
      {tab === 'policies' && (<>
        <section className="ota-panel">
          <form className="ota-evidence-grid" onSubmit={createPolicy}>
            <Field label="Effective at"><input type="datetime-local" value={effectiveAt} onChange={event => setEffectiveAt(event.target.value)} /></Field>
            <Field label="Whole-booking refund %"><input type="number" min="0" max="100" value={refundPercent} onChange={event => setRefundPercent(event.target.value)} /></Field>
            <button className="button primary" disabled={command.pending}>Create policy revision</button>
          </form>
        </section>
        <section className="ota-panel">
          <h2>Policy history</h2>
          {policyList.loading ? <Loading /> : policies.length === 0 ? <div className="empty-state"><p>No policy revisions found.</p></div> : (
            <table className="ops-table">
              <thead><tr><th>Reference</th><th>Effective at</th><th>Refund</th></tr></thead>
              <tbody>{policies.map(item => <tr key={item.policyReference}><td>{item.policyReference}</td><td>{item.effectiveAt}</td><td>{item.wholeBookingRefundPercent}%</td></tr>)}</tbody>
            </table>
          )}
        </section>
      </>)}
    </OpsPage>
  );
}
