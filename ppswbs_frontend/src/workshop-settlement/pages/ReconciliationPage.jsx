import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ProblemNotice, asList } from '../ui';

const METHODS = ['BANK_TRANSFER', 'VNPAY', 'BOOKING_TIME'];
const STATES = ['CONFIRMED', 'PENDING_VERIFICATION', 'VOIDED', 'EXCESS'];

const isLatePayment = row => row.method === 'BOOKING_TIME' && row.state === 'PENDING_VERIFICATION' && /late payment/i.test(row.note || '');

/**
 * Manager's read-only reconciliation list (FR-056, T134). It records no reconciled mark and changes nothing: the Manager
 * compares the rows with the bank statement and VNPay Merchant Admin outside the system. Rows and amounts are the server's;
 * only CONFIRMED rows count into the shown total (unverified, late, voided and excess rows do not).
 */
export default function ReconciliationPage() {
  const t = useT(messages);
  const fmt = useFormat();
  const methodLabel = useEnumLabel(messages, 'method');
  const stateLabel = useEnumLabel(messages, 'collectionStatus');
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [filters, setFilters] = useUrlFilters({ locationId: '', workingDate: '', method: '', state: '' });
  const [rows, setRows] = useState(null);
  const command = useCommand();
  const ready = /^[0-9]+$/.test(filters.locationId.trim()) && filters.workingDate;

  const load = async event => {
    event.preventDefault();
    setRows(null);
    const out = await command.run(() => api.listReconciliation({
      locationId: filters.locationId.trim(), workingDate: filters.workingDate,
      ...(filters.method ? { method: filters.method } : {}), ...(filters.state ? { state: filters.state } : {}),
    }));
    if (out.ok) setRows(asList(out.result));
  };

  // A retry (503 or a lost response) reuses the same request and must show its result too.
  const retry = async () => { const out = await command.retry(); if (out?.ok) setRows(asList(out.result)); };

  const confirmedTotal = (rows || []).filter(row => row.state === 'CONFIRMED').reduce((sum, row) => sum + Number(row.amount || 0), 0);
  const hasMultiBranch = (rows || []).some(row => row.method === 'BOOKING_TIME' && (row.locationIds || []).length > 1);
  const set = field => event => setFilters(v => ({ ...v, [field]: event.target.value }));

  if (!isManager) {
    return (
      <OpsPage title={t('recon.title')} intro={t('recon.introShort')}>
        <div className="notice error" role="alert"><p>{t('recon.managerOnly')}</p></div>
      </OpsPage>
    );
  }

  return (
    <OpsPage title={t('recon.title')} intro={t('recon.intro')}>
      <ProblemNotice error={command.error?.status === 503 ? null : command.error} onRetry={retry} />
      {command.error?.status === 503 && (
        <div className="notice error" role="alert">
          <p>{t('recon.unavailable')}</p>
          {command.error.code && <p className="muted small">{t('problem.code', { code: command.error.code })}</p>}
          <div className="notice-actions"><button type="button" className="button outline" onClick={retry}>{t('problem.retry')}</button></div>
        </div>
      )}
      <section className="ota-panel">
        <form className="ota-inline-form" aria-label={t('recon.filtersAria')} onSubmit={load}>
          <label>{t('recon.branchId')}<input inputMode="numeric" value={filters.locationId} onChange={set('locationId')} /></label>
          <label>{t('recon.workingDay')}<input type="date" value={filters.workingDate} onChange={set('workingDate')} /></label>
          <label>{t('recon.method')}
            <select value={filters.method} onChange={set('method')}>
              <option value="">{t('recon.all')}</option>
              {METHODS.map(value => <option key={value} value={value}>{methodLabel(value)}</option>)}
            </select>
          </label>
          <label>{t('recon.state')}
            <select value={filters.state} onChange={set('state')}>
              <option value="">{t('recon.all')}</option>
              {STATES.map(value => <option key={value} value={value}>{stateLabel(value)}</option>)}
            </select>
          </label>
          <button className="button primary" type="submit" disabled={command.pending || !ready}>{t('recon.show')}</button>
        </form>
      </section>

      {command.pending ? <Loading /> : null}
      {rows && rows.length === 0 && <div className="empty-state"><p>{t('recon.empty')}</p></div>}
      {rows && rows.length > 0 && (
        <section className="ota-panel">
          <div className="ota-panel-head"><h2>{t('recon.collections')}</h2><span className="muted">{t('recon.confirmedTotal')}: <strong>{fmt.money(String(confirmedTotal))}</strong></span></div>
          <p className="muted small">{t('recon.totalNote')}</p>
          {hasMultiBranch && (
            <p className="notice warning" role="alert">{t('recon.multiBranch')}</p>
          )}
          <table className="ops-table">
            <thead><tr><th>{t('recon.col.ticket')}</th><th>{t('recon.col.group')}</th><th>{t('recon.col.method')}</th><th>{t('recon.col.amount')}</th><th>{t('recon.col.state')}</th><th>{t('recon.col.reference')}</th><th>{t('recon.col.confirmedBy')}</th><th>{t('recon.col.time')}</th><th>{t('recon.col.note')}</th></tr></thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.collectionId || 'excess'}-${index}`}>
                  <td>{(row.ticketCodes && row.ticketCodes.length > 0 ? row.ticketCodes.join(', ') : row.ticketCode) || '—'}</td>
                  <td>{row.groupCode}</td>
                  <td>
                    {methodLabel(row.method)}
                    {row.method === 'BOOKING_TIME' && <small className="muted"> {t('recon.wholeGroup')}</small>}
                    {row.method === 'BOOKING_TIME' && (row.locationIds || []).length > 1 && <small className="muted"> {t('recon.branches', { ids: row.locationIds.join(', ') })}</small>}
                  </td>
                  <td>
                    {fmt.money(row.amount)}
                    {row.excessAmount ? <small className="muted"> ({t('recon.excess', { amount: fmt.money(row.excessAmount) })}{row.refundRequestReference ? t('recon.excessRefund', { ref: row.refundRequestReference }) : ''})</small> : null}
                  </td>
                  <td>
                    <span className="ops-status">{stateLabel(row.state)}</span>
                    {row.method === 'BOOKING_TIME' && row.state === 'PENDING_VERIFICATION' && !isLatePayment(row) && <small className="muted"> {t('recon.unverified')}</small>}
                    {isLatePayment(row) && <small className="muted"> {t('recon.late')}</small>}
                  </td>
                  <td>{row.transactionReference || '—'}</td>
                  <td>{row.confirmedBy || (row.method === 'BOOKING_TIME' ? t('recon.systemIpn') : '—')}</td>
                  <td>{fmt.dateTime(row.confirmedAt || row.createdAt)}</td>
                  <td>{row.note || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </OpsPage>
  );
}
