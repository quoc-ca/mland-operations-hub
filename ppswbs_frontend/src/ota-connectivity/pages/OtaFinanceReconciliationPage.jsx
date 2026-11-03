import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { ErrorNotice, Loading, SuccessNotice } from '../../shared/ui/components';
import '../ota-connectivity.css';

const EMPTY_FORM = { kind: 'PAYOUT', evidenceType: 'ELIGIBILITY', amount: '', reference: '', reason: '', occurredAt: '', payoutReference: '', allocationReference: '' };

export default function OtaFinanceReconciliationPage() {
  const { status } = useAuth();
  const api = useOtaConnectivityApi();
  const [bookingId, setBookingId] = useState('');
  const [evidence, setEvidence] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);

  const read = useCallback(async () => {
    if (!bookingId.trim()) return;
    setLoading(true); setError(null); setSuccess(null);
    try { setEvidence(await api.getFinancialEvidence(bookingId.trim())); }
    catch (err) { setError(err); }
    finally { setLoading(false); }
  }, [api, bookingId]);

  useEffect(() => { setEvidence(null); }, [bookingId]);

  async function submit(event) {
    event.preventDefault(); setError(null); setSuccess(null);
    if (!bookingId.trim() || !form.amount || !form.reference || !form.reason || !form.occurredAt) {
      setError(new Error('Booking ID, amount, reference, reason, and occurred time are required.')); return;
    }
    try {
      const result = await api.reconcilePayout(bookingId.trim(), {
        kind: form.kind, evidenceType: form.evidenceType,
        amount: { amount: String(form.amount), currency: 'VND' }, reference: form.reference,
        reason: form.reason, occurredAt: new Date(form.occurredAt).toISOString(),
        ...(form.payoutReference ? { payoutReference: form.payoutReference } : {}),
        ...(form.allocationReference ? { allocationReference: form.allocationReference } : {}),
      });
      setEvidence(result); setSuccess('Financial evidence recorded.'); setForm(EMPTY_FORM);
    } catch (err) { setError(err); }
  }

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') return <div className="ota-container"><div className="empty-state"><p>Authentication required to access finance reconciliation.</p><a className="button primary" href="/login">Sign In</a></div></div>;

  return <div className="ota-workspace">
    <aside className="ota-sidebar"><div className="ota-brand"><span>M</span><strong>MLAND</strong><small>operations hub</small></div><div className="ota-sidebar-label">MANAGER WORKSPACE</div><Link to="/ota-connectivity/channels">Channel mappings</Link><Link to="/ota-connectivity/reconciliation">Reconciliation</Link><Link className="active" to="/ota-connectivity/finance">Finance evidence</Link><Link to="/ota-connectivity/add-ons">Shop add-ons</Link></aside>
    <main className="ota-main"><div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● Backend-authorized</span></div>
      <header className="ota-page-heading"><div><p className="ota-eyebrow">FINANCE OPERATIONS</p><h1>Payout reconciliation</h1><p>Verify channel payment and payout evidence without exposing provider credentials.</p></div></header>
      <nav className="ota-nav-tabs" aria-label="OTA Navigation"><Link to="/ota-connectivity/channels">Channels</Link><Link to="/ota-connectivity/reconciliation">Queue</Link><Link className="active" to="/ota-connectivity/finance">Finance</Link><Link to="/ota-connectivity/add-ons">Add-ons</Link></nav>
      <SuccessNotice>{success}</SuccessNotice><ErrorNotice error={error} onRetry={read} onReload={read} />
      <section className="ota-panel"><div className="ota-panel-head"><h2>Booking evidence</h2><span className="ota-live">MANAGER ONLY</span></div><form className="ota-inline-form" onSubmit={event => { event.preventDefault(); read(); }}><label>Channel booking ID<input value={bookingId} onChange={e => setBookingId(e.target.value)} placeholder="UUID" /></label><button className="button primary" type="submit" disabled={loading || !bookingId.trim()}>Load evidence</button></form></section>
      {loading && <Loading />}
      {evidence && <section className="ota-evidence-grid"><article className="ota-metric"><span>Customer payment</span><strong>{evidence.customerPaymentState || '—'}</strong></article><article className="ota-metric"><span>Payout state</span><strong>{evidence.payoutState || '—'}</strong></article><article className="ota-panel ota-span-2"><div className="ota-panel-head"><h2>Evidence ledger</h2><span>{evidence.entries?.length || 0} entries</span></div><div className="ota-ledger">{(evidence.entries || []).map(entry => <div className="ota-ledger-row" key={entry.entryId}><span>{entry.evidenceType || entry.kind}</span><strong>{entry.amount?.amount || '—'} {entry.amount?.currency || 'VND'}</strong><code>{entry.reference}</code><time>{entry.recordedAt ? new Date(entry.recordedAt).toLocaleString() : '—'}</time></div>)}</div></article><article className="ota-panel"><div className="ota-panel-head"><h2>Record stage</h2></div><form className="ota-form" onSubmit={submit}><label>Kind<select value={form.kind} onChange={e => setForm({...form, kind:e.target.value})}>{['PAYOUT','VARIANCE','DISPUTE','ADJUSTMENT','REVERSAL'].map(v => <option key={v}>{v}</option>)}</select></label><label>Evidence type<select value={form.evidenceType} onChange={e => setForm({...form, evidenceType:e.target.value})}>{['ELIGIBILITY','INVOICE','REMITTANCE','BANK_RECEIPT','ADJUSTMENT','REVERSAL','VARIANCE','DISPUTE'].map(v => <option key={v}>{v}</option>)}</select></label><label>Amount (VND)<input inputMode="decimal" value={form.amount} onChange={e => setForm({...form, amount:e.target.value})} /></label><label>Reference<input value={form.reference} onChange={e => setForm({...form, reference:e.target.value})} /></label><label>Reason<textarea value={form.reason} onChange={e => setForm({...form, reason:e.target.value})} /></label><label>Occurred at<input type="datetime-local" value={form.occurredAt} onChange={e => setForm({...form, occurredAt:e.target.value})} /></label><button className="button primary" type="submit">Record evidence</button></form></article></section>}
    </main></div>;
}
