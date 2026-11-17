import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, asList, money, when } from '../ui';

const METHODS = ['BANK_TRANSFER', 'VNPAY', 'BOOKING_TIME'];
const STATES = ['CONFIRMED', 'PENDING_VERIFICATION', 'VOIDED', 'EXCESS'];

const isLatePayment = row => row.method === 'BOOKING_TIME' && row.state === 'PENDING_VERIFICATION' && /late payment/i.test(row.note || '');

/**
 * Manager's read-only reconciliation list (FR-056, T134). It records no reconciled mark and changes nothing: the Manager
 * compares the rows with the bank statement and VNPay Merchant Admin outside the system. Rows and amounts are the server's;
 * only CONFIRMED rows count into the shown total (unverified, late, voided and excess rows do not).
 */
export default function ReconciliationPage() {
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
      <OpsPage eyebrow="RECONCILIATION" title="Reconciliation list" intro="Transfer, VNPay and booking-time collections of one branch and day.">
        <div className="notice error" role="alert"><p>Only a Manager can open the reconciliation list.</p></div>
      </OpsPage>
    );
  }

  return (
    <OpsPage eyebrow="RECONCILIATION" title="Reconciliation list" intro="Read-only list of transfer, VNPay and booking-time collections of one branch and day. Compare it with the bank statement and VNPay Merchant Admin; nothing is marked reconciled here.">
      <ProblemNotice error={command.error?.status === 503 ? null : command.error} onRetry={retry} />
      {command.error?.status === 503 && (
        <div className="notice error" role="alert">
          <p>The booking-time payments could not be read right now, so no partial list is shown. Try again, or filter without BOOKING_TIME.</p>
          {command.error.code && <p className="muted small">Code: {command.error.code}</p>}
          <div className="notice-actions"><button type="button" className="button outline" onClick={retry}>Try again</button></div>
        </div>
      )}
      <section className="ota-panel">
        <form className="ota-inline-form" aria-label="Reconciliation filters" onSubmit={load}>
          <label>Branch ID<input inputMode="numeric" value={filters.locationId} onChange={set('locationId')} /></label>
          <label>Working day<input type="date" value={filters.workingDate} onChange={set('workingDate')} /></label>
          <label>Method
            <select value={filters.method} onChange={set('method')}>
              <option value="">All</option>
              {METHODS.map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label>State
            <select value={filters.state} onChange={set('state')}>
              <option value="">All</option>
              {STATES.map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <button className="button primary" type="submit" disabled={command.pending || !ready}>Show collections</button>
        </form>
      </section>

      {command.pending ? <Loading /> : null}
      {rows && rows.length === 0 && <div className="empty-state"><p>No transfer, VNPay or booking-time collection for these filters.</p></div>}
      {rows && rows.length > 0 && (
        <section className="ota-panel">
          <div className="ota-panel-head"><h2>Collections</h2><span className="muted">Confirmed total: <strong>{money(String(confirmedTotal))}</strong></span></div>
          <p className="muted small">The confirmed total counts only CONFIRMED rows. Unverified, late, voided and excess rows are listed but not counted.</p>
          {hasMultiBranch && (
            <p className="notice warning" role="alert">A booking-time payment with sessions at several branches appears once under each of its branches. Do not add the per-branch totals: the whole-system day total counts that payment once.</p>
          )}
          <table className="ops-table">
            <thead><tr><th>Ticket</th><th>Group</th><th>Method</th><th>Amount</th><th>State</th><th>Reference</th><th>Confirmed by</th><th>Time</th><th>Note</th></tr></thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.collectionId || 'excess'}-${index}`}>
                  <td>{(row.ticketCodes && row.ticketCodes.length > 0 ? row.ticketCodes.join(', ') : row.ticketCode) || '—'}</td>
                  <td>{row.groupCode}</td>
                  <td>
                    {row.method}
                    {row.method === 'BOOKING_TIME' && <small className="muted"> whole group payment</small>}
                    {row.method === 'BOOKING_TIME' && (row.locationIds || []).length > 1 && <small className="muted"> branches: {row.locationIds.join(', ')}</small>}
                  </td>
                  <td>
                    {money(row.amount)}
                    {row.excessAmount ? <small className="muted"> (excess {money(row.excessAmount)}{row.refundRequestReference ? `, refund ${row.refundRequestReference}` : ''})</small> : null}
                  </td>
                  <td>
                    <span className="ops-status">{row.state}</span>
                    {row.method === 'BOOKING_TIME' && row.state === 'PENDING_VERIFICATION' && !isLatePayment(row) && <small className="muted"> chưa xác minh (unverified)</small>}
                    {isLatePayment(row) && <small className="muted"> late payment under review</small>}
                  </td>
                  <td>{row.transactionReference || '—'}</td>
                  <td>{row.confirmedBy || (row.method === 'BOOKING_TIME' ? 'System (IPN)' : '—')}</td>
                  <td>{when(row.confirmedAt || row.createdAt)}</td>
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
