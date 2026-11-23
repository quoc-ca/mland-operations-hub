import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { ErrorNotice, Loading, SuccessNotice } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { useT, useEnumLabel, useFormat } from '../../shared/i18n/I18nProvider';
import { otaMessages } from '../messages';
import { moneyText } from '../moneyText';
import '../ota-connectivity.css';

const EMPTY_FORM = { kind: 'PAYOUT', evidenceType: 'ELIGIBILITY', amount: '', reference: '', reason: '', occurredAt: '', payoutReference: '', allocationReference: '' };
const KINDS = ['PAYOUT', 'VARIANCE', 'DISPUTE', 'ADJUSTMENT', 'REVERSAL'];
const EVIDENCE_TYPES = ['ELIGIBILITY', 'INVOICE', 'REMITTANCE', 'BANK_RECEIPT', 'ADJUSTMENT', 'REVERSAL', 'VARIANCE', 'DISPUTE'];

export default function OtaFinanceReconciliationPage() {
  const { status } = useAuth();
  const api = useOtaConnectivityApi();
  const t = useT(otaMessages);
  const fmt = useFormat();
  const paymentLabel = useEnumLabel(otaMessages, 'payment');
  const payoutLabel = useEnumLabel(otaMessages, 'payout');
  const kindLabel = useEnumLabel(otaMessages, 'kind');
  const evidenceLabel = useEnumLabel(otaMessages, 'evidence');
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
      setError(new Error(t('finance.required'))); return;
    }
    try {
      const result = await api.reconcilePayout(bookingId.trim(), {
        kind: form.kind, evidenceType: form.evidenceType,
        amount: { amount: String(form.amount), currency: 'VND' }, reference: form.reference,
        reason: form.reason, occurredAt: new Date(form.occurredAt).toISOString(),
        ...(form.payoutReference ? { payoutReference: form.payoutReference } : {}),
        ...(form.allocationReference ? { allocationReference: form.allocationReference } : {}),
      });
      setEvidence(result); setSuccess(t('finance.recorded')); setForm(EMPTY_FORM);
    } catch (err) { setError(err); }
  }

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / OTA CONNECTIVITY"
        message={t('auth.finance')}
      />
    );
  }

  return <div className="ota-workspace">

    <main className="ota-main"><div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● {t('finance.sub')}</span></div>
      <header className="ota-page-heading"><div><p>{t('finance.intro')}</p></div></header>
      <SuccessNotice>{success}</SuccessNotice><ErrorNotice error={error} onRetry={read} onReload={read} />
      <section className="ota-panel"><div className="ota-panel-head"><h2>{t('finance.bookingEvidence')}</h2><span className="ota-live">{t('finance.managerOnly')}</span></div><form className="ota-inline-form" onSubmit={event => { event.preventDefault(); read(); }}><label>{t('finance.bookingId')}<input value={bookingId} onChange={e => setBookingId(e.target.value)} placeholder="UUID" /></label><button className="button primary" type="submit" disabled={loading || !bookingId.trim()}>{t('finance.load')}</button></form></section>
      {loading && <Loading />}
      {evidence && <section className="ota-evidence-grid"><article className="ota-metric"><span>{t('finance.customerPayment')}</span><strong>{paymentLabel(evidence.customerPaymentState)}</strong></article><article className="ota-metric"><span>{t('finance.payoutState')}</span><strong>{payoutLabel(evidence.payoutState)}</strong></article><article className="ota-panel ota-span-2"><div className="ota-panel-head"><h2>{t('finance.ledger')}</h2><span>{t('finance.entries', { count: evidence.entries?.length || 0 })}</span></div><div className="ota-ledger">{(evidence.entries || []).map(entry => <div className="ota-ledger-row" key={entry.entryId}><span>{entry.evidenceType ? evidenceLabel(entry.evidenceType) : kindLabel(entry.kind)}</span><strong>{entry.amount?.amount ? moneyText(fmt, entry.amount) : '—'}</strong><code>{entry.reference}</code><time>{fmt.dateTime(entry.recordedAt)}</time></div>)}</div></article><article className="ota-panel"><div className="ota-panel-head"><h2>{t('finance.recordStage')}</h2></div><form className="ota-form" onSubmit={submit}><label>{t('finance.kind')}<select value={form.kind} onChange={e => setForm({...form, kind:e.target.value})}>{KINDS.map(v => <option key={v} value={v}>{kindLabel(v)}</option>)}</select></label><label>{t('finance.evidenceType')}<select value={form.evidenceType} onChange={e => setForm({...form, evidenceType:e.target.value})}>{EVIDENCE_TYPES.map(v => <option key={v} value={v}>{evidenceLabel(v)}</option>)}</select></label><label>{t('finance.amount')}<input inputMode="decimal" value={form.amount} onChange={e => setForm({...form, amount:e.target.value})} /></label><label>{t('finance.reference')}<input value={form.reference} onChange={e => setForm({...form, reference:e.target.value})} /></label><label>{t('finance.reason')}<textarea value={form.reason} onChange={e => setForm({...form, reason:e.target.value})} /></label><label>{t('finance.occurredAt')}<input type="datetime-local" value={form.occurredAt} onChange={e => setForm({...form, occurredAt:e.target.value})} /></label><button className="button primary" type="submit">{t('finance.record')}</button></form></article></section>}
    </main></div>;
}
