import React, { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createSettlementApi } from '../api/settlementApi';
import '../workshop-settlement.css';

const unwrap = value => Array.isArray(value) ? value : (value?.items || value?.content || []);

export default function SettlementCasesPage() {
  const { status } = useAuth();
  const api = createSettlementApi(useHttp());
  const [params] = useSearchParams();
  const tab = params.get('tab') || 'cases';
  const [bookingCode, setBookingCode] = useState('');
  const [caseReference, setCaseReference] = useState('');
  const [caseType, setCaseType] = useState('cancellation');
  const [listFilters, setListFilters] = useState({ caseType: '', status: '' });
  const [reason, setReason] = useState('');
  const [sessions, setSessions] = useState('');
  const [handoff, setHandoff] = useState('');
  const [effectiveAt, setEffectiveAt] = useState('');
  const [refundPercent, setRefundPercent] = useState('0');
  const [lookup, setLookup] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(null);
  const command = useCommand();
  const caseResource = useResource(() => {
    if (!lookup) return Promise.resolve(null);
    if (lookup.caseType === 'required-session-change') return api.getRequiredSessionChange(lookup.bookingCode, lookup.caseReference);
    if (lookup.caseType === 'mland-remedy') return api.getMlandRemedy(lookup.bookingCode, lookup.caseReference);
    return api.getCancellation(lookup.bookingCode, lookup.caseReference);
  }, [lookup]);
  const caseListResource = useResource(() => api.listCases({ ...(listFilters.caseType ? { caseType: listFilters.caseType } : {}), ...(listFilters.status ? { status: listFilters.status } : {}) }), [listFilters.caseType, listFilters.status]);
  const policyResource = useResource(() => api.listRefundPolicies(), [tab]);
  const cases = useMemo(() => unwrap(caseListResource.data), [caseListResource.data]);
  const policies = useMemo(() => unwrap(policyResource.data), [policyResource.data]);
  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP SETTLEMENT"
        message="Authentication required to manage settlement cases."
      />
    );
  }
  const run = async (send, success) => { setError(null); const out = await command.run(send); if (out.ok) { setNotice(success); caseResource.reload(); caseListResource.reload(); policyResource.reload(); } else setError(out.error); };
  const openCase = e => { e.preventDefault(); if (bookingCode.trim() && caseReference.trim()) setLookup({ bookingCode: bookingCode.trim(), caseReference: caseReference.trim(), caseType }); };
  const openChange = e => { e.preventDefault(); const targetRequiredSessionReferences = sessions.split(',').map(v => v.trim()).filter(Boolean); if (!bookingCode.trim() || !reason.trim() || targetRequiredSessionReferences.length === 0) return setError({ message: 'Booking, reason and target session references are required.' }); run(key => api.openRequiredSessionChange(bookingCode.trim(), { reason, replacementOutcome: 'CUSTOMER_ACCEPTED', targetRequiredSessionReferences, ...(handoff.trim() ? { cancellationHandoffReference: handoff.trim() } : {}) }, key), 'Required-session change case opened.'); };
  const createPolicy = e => { e.preventDefault(); const percent = Number(refundPercent); if (!effectiveAt || !Number.isInteger(percent) || percent < 0 || percent > 100) return setError({ message: 'Enter an effective date and a refund percentage from 0 to 100.' }); run(key => api.createRefundPolicy({ effectiveAt: new Date(effectiveAt).toISOString(), wholeBookingRefundPercent: percent }, key), 'Refund policy revision created.'); };
  const selectCase = item => { const type = item.caseType === 'REQUIRED_SESSION_CHANGE' ? 'required-session-change' : item.caseType === 'MLAND_CANCELLATION_REMEDY' ? 'mland-remedy' : 'cancellation'; setBookingCode(item.bookingCode || ''); setCaseReference(item.caseReference || ''); setCaseType(type); setLookup({ bookingCode: item.bookingCode, caseReference: item.caseReference, caseType: type }); };
  return <div className="ota-workspace"><OperationsSidebar /><main className="ota-main"><div className="ota-topline">MLAND / WORKSHOP SETTLEMENT <span>• Backend-authorized</span></div><header className="ota-page-heading"><div><p className="ota-eyebrow">SETTLEMENT OPERATIONS</p><h1>{tab === 'change' ? 'Required session change' : tab === 'policies' ? 'Refund policy' : 'Settlement cases'}</h1><p>Manager-only case list with safe projections; payment/provider evidence stays behind the backend boundary.</p></div></header><nav className="ota-nav-tabs" aria-label="Settlement sub-navigation"><Link className={`ota-nav-tab ${tab === 'cases' ? 'active' : ''}`} to="/settlement/cases?tab=cases">Settlement cases</Link><Link className={`ota-nav-tab ${tab === 'change' ? 'active' : ''}`} to="/settlement/cases?tab=change">Required session change</Link><Link className={`ota-nav-tab ${tab === 'policies' ? 'active' : ''}`} to="/settlement/cases?tab=policies">Refund policy</Link></nav><SuccessNotice>{notice}</SuccessNotice><ErrorNotice error={error || command.error || caseResource.error || caseListResource.error || policyResource.error} onRetry={() => { caseResource.reload(); caseListResource.reload(); policyResource.reload(); }} />{tab === 'cases' && <><section className="ota-panel"><div className="ota-panel-head"><h2>All settlement cases</h2><button className="button secondary" type="button" onClick={() => caseListResource.reload()}>Refresh</button></div><div className="ota-inline-form"><label>Type<select value={listFilters.caseType} onChange={e => setListFilters(v => ({ ...v, caseType: e.target.value }))}><option value="">All</option><option value="CUSTOMER_CANCELLATION">Customer cancellation</option><option value="REQUIRED_SESSION_CHANGE">Required session change</option><option value="MLAND_CANCELLATION_REMEDY">Mland remedy</option></select></label><label>Status<select value={listFilters.status} onChange={e => setListFilters(v => ({ ...v, status: e.target.value }))}><option value="">All</option><option value="OPEN">Open</option><option value="PENDING_PROVIDER">Pending provider</option><option value="RESOLVED">Resolved</option><option value="CANCELED">Canceled</option></select></label></div>{caseListResource.loading ? <Loading /> : caseListResource.error ? null : cases.length === 0 ? <div className="empty-state"><p>No settlement cases found.</p></div> : <table className="ops-table"><thead><tr><th>Case</th><th>Type</th><th>Status</th><th>Booking</th></tr></thead><tbody>{cases.map(item => <tr key={item.caseReference} onClick={() => selectCase(item)} style={{ cursor: 'pointer' }}><td>{item.caseReference}</td><td>{item.caseType}</td><td><span className="ops-status">{item.status}</span></td><td>{item.bookingCode}</td></tr>)}</tbody></table>}</section><section className="ota-panel"><form className="ota-inline-form" onSubmit={openCase}><label>Booking code<input value={bookingCode} onChange={e => setBookingCode(e.target.value)} placeholder="BK-..." /></label><label>Case reference<input value={caseReference} onChange={e => setCaseReference(e.target.value)} placeholder="CASE-..." /></label><label>Case type<select value={caseType} onChange={e => setCaseType(e.target.value)}><option value="cancellation">Customer cancellation</option><option value="required-session-change">Required session change</option><option value="mland-remedy">Mland-caused remedy</option></select></label><button className="button primary">Open case detail</button></form></section>{caseResource.loading && lookup ? <Loading /> : caseResource.data ? <section className="ota-panel"><div className="ota-panel-head"><h2>{caseResource.data.caseReference || lookup.caseReference}</h2><span className="ops-status">{caseResource.data.status}</span></div><dl className="ops-facts"><div><dt>Type</dt><dd>{caseResource.data.caseType || caseType}</dd></div><div><dt>Booking</dt><dd>{caseResource.data.bookingCode || lookup.bookingCode}</dd></div><div><dt>Correlation</dt><dd>{caseResource.data.correlationReference || 'Protected by backend'}</dd></div></dl></section> : null}</>}{tab === 'change' && <section className="ota-panel"><form className="ota-form" onSubmit={openChange}><Field label="Booking code"><input value={bookingCode} onChange={e => setBookingCode(e.target.value)} /></Field><Field label="Reason"><textarea value={reason} onChange={e => setReason(e.target.value)} /></Field><Field label="Target required session references"><input value={sessions} onChange={e => setSessions(e.target.value)} placeholder="UUID, UUID" /></Field><Field label="Cancellation handoff reference (optional)"><input value={handoff} onChange={e => setHandoff(e.target.value)} /></Field><button className="button primary">Open required-session change</button></form></section>}{tab === 'policies' && <><section className="ota-panel"><form className="ota-evidence-grid" onSubmit={createPolicy}><Field label="Effective at"><input type="datetime-local" value={effectiveAt} onChange={e => setEffectiveAt(e.target.value)} /></Field><Field label="Whole-booking refund %"><input type="number" min="0" max="100" value={refundPercent} onChange={e => setRefundPercent(e.target.value)} /></Field><button className="button primary">Create policy revision</button></form></section><section className="ota-panel"><h2>Policy history</h2>{policyResource.loading ? <Loading /> : policies.length === 0 ? <div className="empty-state"><p>No policy revisions found.</p></div> : <table className="ops-table"><thead><tr><th>Reference</th><th>Effective at</th><th>Refund</th></tr></thead><tbody>{policies.map(item => <tr key={item.policyReference}><td>{item.policyReference}</td><td>{item.effectiveAt}</td><td>{item.wholeBookingRefundPercent}%</td></tr>)}</tbody></table>}</section></>}</main></div>;
}
