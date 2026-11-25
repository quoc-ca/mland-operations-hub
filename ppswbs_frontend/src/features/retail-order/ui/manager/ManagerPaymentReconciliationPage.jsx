import React, { useMemo, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { Link } from 'react-router';
import { messages as retailMessages } from '../../messages';
import { sharedMessages } from '../../../../shared/ui/messages';
import { useDisplayTime } from '../orderCode';

const REASONS = ['QUERY_UNRESOLVED', 'LATE_OR_INVALID_STATE'];

export default function ManagerPaymentReconciliationPage() {
  const t = useT(retailMessages);
  return <div className="wsp-workspace">
    
    <main className="wsp-main payment-reconciliation">
      <div className="wsp-topline"><span className="retail-staff-crumb"><Link to="/home">{t('common.home')}</Link> {t('recon.mlandRetailReconciliation')}</span><span>● {t('common.backendAuthorized')}</span></div>
      <div className="wsp-page-heading"><div><p>{t('recon.checkThePurchaseTransactionsThat')}</p></div></div>
      <RequireSession requiredText={t('recon.signInWithAManager')}><ManagerPaymentTabs /></RequireSession>
    </main>
  </div>;
}

function ManagerPaymentTabs() {
  const t = useT(retailMessages);
  const [tab, setTab] = useState('VNPAY');
  return <>
    <div className="pickup-desk-tabs" role="tablist" aria-label={t('recon.reconciliationType')}>
      <button type="button" role="tab" aria-selected={tab === 'VNPAY'} className={tab === 'VNPAY' ? 'active' : ''} onClick={() => setTab('VNPAY')}>{t('recon.vnpayReconciliation')}</button>
      <button type="button" role="tab" aria-selected={tab === 'CASH'} className={tab === 'CASH' ? 'active' : ''} onClick={() => setTab('CASH')}>{t('recon.dailyCash')}</button>
    </div>
    {tab === 'VNPAY' ? <ReconciliationList /> : <DailyCashPanel />}
  </>;
}

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

/** FR-08-047: confirmed retail counter cash per permitted branch and Vietnam business date (read-only). */
function DailyCashPanel() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const time = useDisplayTime();
  const branches = useResource(() => api.listBranches(), []);
  const branchList = branches.data?.items || branches.data || [];
  const [locationId, setLocationId] = useState('');
  const [businessDate, setBusinessDate] = useState(todayInVietnam());
  const selectedLocation = locationId || (branchList[0] ? String(branchList[0].id) : '');
  const result = useResource(() => (selectedLocation ? api.dailyCash(selectedLocation, businessDate) : Promise.resolve(null)), [selectedLocation, businessDate]);
  const data = result.data;
  return <section className="workspace-card reconciliation-panel" aria-label={t('recon.dailyRetailCash')}>
    <div className="reconciliation-toolbar"><div><h2>{t('recon.retailCashCollected')}</h2><p className="muted">{t('recon.cashConfirmedByStaffAt')}</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>{t('common.refresh')}</button></div>
    <div className="reconciliation-filters">
      <label>{t('common.branch')}<select value={selectedLocation} onChange={event => setLocationId(event.target.value)}>{branchList.map(item => <option key={item.id} value={String(item.id)}>{item.name}</option>)}</select></label>
      <label>{t('common.date')}<input type="date" value={businessDate} max={todayInVietnam()} onChange={event => setBusinessDate(event.target.value)} /></label>
    </div>
    {(result.loading || branches.loading) && <Loading />}
    {result.error && <ErrorNotice error={result.error} onRetry={result.reload} />}
    {data && !result.loading && <>
      <p className="cash-daily-total">{t('recon.collectionsTotal', { collectionCount: data.collectionCount })} <strong>{format.money(data.totalApplied)}</strong></p>
      {data.collections.length === 0 ? <p className="reconciliation-empty">{t('recon.noCashCollectedOnThis')}</p>
        : <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">{t('recon.time')}</th><th scope="col">{t('recon.order')}</th><th scope="col">{t('common.amount')}</th><th scope="col">{t('recon.received')}</th><th scope="col">{t('recon.change')}</th><th scope="col">{t('recon.staff')}</th></tr></thead><tbody>{data.collections.map(row => <tr key={row.collectionId}><td data-label={t('recon.time')}>{time(row.confirmedAt)}</td><td data-label={t('recon.order')}>#{String(row.orderId).slice(0, 8).toUpperCase()}</td><td data-label={t('common.amount')}>{format.money(row.appliedAmount)}</td><td data-label={t('recon.received')}>{format.money(row.amountReceived)}</td><td data-label={t('recon.change')}>{format.money(row.changeReturned)}</td><td data-label={t('recon.staff')}>#{row.confirmedBy}</td></tr>)}</tbody></table></div>}
    </>}
  </section>;
}

function ReconciliationList() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const ts = useT(sharedMessages);
  const time = useDisplayTime();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [reason, setReason] = useState('ALL');
  const result = useResource(() => api.listPaymentReconciliations(page), [page]);
  const rows = Array.isArray(result.data) ? result.data : [];
  const visible = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi');
    return rows.filter(row => (reason === 'ALL' || row.reason === reason)
      && (!term || [row.checkoutId, row.merchantReference, row.providerTransaction].some(value => String(value || '').toLocaleLowerCase('vi').includes(term))));
  }, [rows, search, reason]);
  const formatMoney = amount => amount == null ? '—' : format.money(String(amount));
  return <section className="workspace-card reconciliation-panel" aria-label={t('recon.transactionsToReconcile')}>
    <div className="reconciliation-toolbar"><div><h2>{t('recon.transactionsToCheck')}</h2><p className="muted">{t('recon.recordedByTheSystemFilters')}</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>{t('common.refresh')}</button></div>
    <div className="reconciliation-filters"><label>{t('recon.searchOrderOrTransactionCode')}<input value={search} onChange={event => setSearch(event.target.value)} placeholder={t('recon.enterACode')} /></label><label>{t('recon.reason')}<select value={reason} onChange={event => setReason(event.target.value)}><option value="ALL">{t('recon.allReasons')}</option><option value="QUERY_UNRESOLVED">{t('recon.paymentResultUnknown')}</option><option value="LATE_OR_INVALID_STATE">{t('recon.latePaymentOrInvalidOrder')}</option></select></label></div>
    {result.loading && <Loading />}
    {result.error && <><ErrorNotice error={result.error} /><button type="button" className="button outline" onClick={result.reload}>{ts('retry')}</button></>}
    {!result.loading && !result.error && rows.length === 0 && <p className="reconciliation-empty">{t('recon.noTransactionToReconcileOn')}</p>}
    {!result.loading && !result.error && rows.length > 0 && visible.length === 0 && <p className="reconciliation-empty">{t('recon.noTransactionMatchesTheFilters')}</p>}
    {!result.loading && !result.error && visible.length > 0 && <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">{t('recon.checkout')}</th><th scope="col">{t('recon.paymentReference')}</th><th scope="col">{t('recon.vnpayTransaction')}</th><th scope="col">{t('common.amount')}</th><th scope="col">{t('recon.reason')}</th><th scope="col">{t('recon.recordedAt')}</th></tr></thead><tbody>{visible.map(row => <tr key={row.merchantReference}><td data-label={t('recon.checkout')}>{row.checkoutId}</td><td data-label={t('recon.paymentReference')}>{row.merchantReference}</td><td data-label={t('recon.vnpayTransaction')}>{row.providerTransaction || t('recon.none')}</td><td data-label={t('common.amount')}>{formatMoney(row.amountVnd)}</td><td data-label={t('recon.reason')}><span className="reconciliation-reason">{REASONS.includes(row.reason) ? t(`recon.reason.${row.reason}`) : t('recon.toCheck')}</span></td><td data-label={t('recon.recordedAt')}>{time(row.createdAt)}</td></tr>)}</tbody></table></div>}
    {!result.error && <div className="reconciliation-pagination"><button type="button" className="button outline" onClick={() => setPage(current => current - 1)} disabled={page === 0 || result.loading}>{t('recon.previousPage')}</button><span>{t('recon.page')} {page + 1}</span><button type="button" className="button outline" onClick={() => setPage(current => current + 1)} disabled={result.loading || rows.length < 50}>{t('recon.nextPage')}</button></div>}
  </section>;
}
