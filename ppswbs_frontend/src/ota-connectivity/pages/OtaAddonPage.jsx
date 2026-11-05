import React, { useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { ErrorNotice, Loading, SuccessNotice } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import '../ota-connectivity.css';

export default function OtaAddonPage() {
  const { status } = useAuth();
  const api = useOtaConnectivityApi();
  const [form, setForm] = useState({ bookingId: '', addonType: 'LASER_ENGRAVING', catalogueAddOnReference: '', itemDescription: '' });
  const [quote, setQuote] = useState(null);
  const [acceptanceReference, setAcceptanceReference] = useState('');
  const [billResult, setBillResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  async function createQuote(event) {
    event.preventDefault(); setBusy(true); setError(null); setSuccess(null); setBillResult(null);
    try {
      const payload = form.addonType === 'PAID_OPTION'
        ? { addonType: 'PAID_OPTION', catalogueAddOnReference: form.catalogueAddOnReference.trim() }
        : { addonType: 'LASER_ENGRAVING', itemDescription: form.itemDescription.trim(), amount: { amount: '0', currency: 'VND' } };
      const result = await api.createAddonQuote(form.bookingId.trim(), payload);
      setQuote(result); setSuccess('Add-on quote recorded at the Mland shop.');
    } catch (err) { setError(err); } finally { setBusy(false); }
  }
  async function acceptQuote() {
    setBusy(true); setError(null); setBillResult(null);
    try { const result = await api.acceptAddonQuote(quote.quoteId, { acceptedAt: new Date().toISOString(), acceptanceReference: acceptanceReference.trim() }); setQuote(result); setSuccess('Customer acceptance recorded.'); }
    catch (err) { setError(err); } finally { setBusy(false); }
  }
  async function billQuote() {
    setBusy(true); setError(null);
    try { const result = await api.createAddonDirectBill(quote.quoteId); setBillResult(result); setSuccess('Direct Mland bill created.'); }
    catch (err) { setError(err); } finally { setBusy(false); }
  }
  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / OTA CONNECTIVITY"
        message="Authentication required to record shop add-ons."
      />
    );
  }
  return <div className="ota-workspace"><OperationsSidebar /><main className="ota-main"><div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● Direct Mland billing</span></div><header className="ota-page-heading"><div><p className="ota-eyebrow">WORKSHOP SERVICE</p><h1>Shop add-ons</h1><p>Record free engraving or a paid option at the workshop. OTA checkout and channel settlement stay unchanged.</p></div></header><nav className="ota-nav-tabs" aria-label="OTA Navigation"><Link to="/ota-connectivity/channels">Channels</Link><Link to="/ota-connectivity/reconciliation">Queue</Link><Link to="/ota-connectivity/finance">Finance</Link><Link className="active" to="/ota-connectivity/add-ons">Add-ons</Link></nav><SuccessNotice>{success}</SuccessNotice><ErrorNotice error={error} /><section className="ota-panel ota-addon-layout"><form className="ota-form" onSubmit={createQuote}><h2>Create shop quote</h2><label>Channel booking ID<input required value={form.bookingId} onChange={e => setForm({ ...form, bookingId: e.target.value })} placeholder="UUID" /></label><label>Add-on type<select value={form.addonType} onChange={e => setForm({ ...form, addonType: e.target.value })}><option value="LASER_ENGRAVING">Laser engraving (free)</option><option value="PAID_OPTION">Paid option</option></select></label>{form.addonType === 'PAID_OPTION' ? <label>Catalogue add-on reference<input required value={form.catalogueAddOnReference} onChange={e => setForm({ ...form, catalogueAddOnReference: e.target.value })} placeholder="UUID" /></label> : <label>Description<textarea required value={form.itemDescription} onChange={e => setForm({ ...form, itemDescription: e.target.value })} /></label>}<button className="button primary" type="submit" disabled={busy}>Create quote</button></form><div className="ota-quote-preview"><p className="ota-eyebrow">CURRENT QUOTE</p>{quote ? <><h2>{quote.addonType}</h2><span className="badge badge-enabled">{quote.status}</span><p>{quote.amount?.amount || '0'} {quote.amount?.currency || 'VND'}</p><code>{quote.quoteId}</code>{quote.addonType === 'LASER_ENGRAVING' && <p className="muted">Free for OTA customers. No direct bill is created.</p>}{quote.status === 'QUOTED' && <><label>Acceptance reference<input value={acceptanceReference} onChange={e => setAcceptanceReference(e.target.value)} placeholder="Signed at counter" /></label><button className="button outline" type="button" onClick={acceptQuote} disabled={busy || !acceptanceReference.trim()}>Record acceptance</button></>}{quote.status === 'ACCEPTED' && quote.addonType !== 'LASER_ENGRAVING' && <button className="button primary" type="button" onClick={billQuote} disabled={busy}>Create direct Mland bill</button>}{billResult && <div className="ota-result-card"><strong>Bill linked</strong><code>{billResult.settlementBillReference}</code><span>Correlation: {billResult.correlationId || '—'}</span></div>}</> : <p className="muted">Create a quote to review the immutable amount and consent steps.</p>}</div></section></main></div>;
}
