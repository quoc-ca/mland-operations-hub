import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, ReasonDialog, asList, money, when } from '../ui';

/**
 * Manager list of absorbed-undercharge records of one branch and period (FR-057, T136). Documentation only: nothing is
 * collected or changed on a folio. VOIDED records stay visible and are marked; the total is the server's totalVnd of ACTIVE records.
 */
export default function AbsorbedUnderchargesPage() {
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [filters, setFilters] = useUrlFilters({ locationId: '', from: '', to: '' });
  const [result, setResult] = useState(null);
  const [voiding, setVoiding] = useState(null);
  const [message, setMessage] = useState('');
  const command = useCommand();
  const ready = /^[0-9]+$/.test(filters.locationId.trim()) && filters.from && filters.to;
  const query = () => ({ locationId: filters.locationId.trim(), from: filters.from, to: filters.to });
  const set = field => event => setFilters(v => ({ ...v, [field]: event.target.value }));

  const load = async event => {
    event?.preventDefault();
    setMessage('');
    const out = await command.run(() => api.listAbsorbedUndercharges(query()));
    if (out.ok) setResult(out.result);
  };

  const retry = async () => { const out = await command.retry(); if (out?.ok && Array.isArray(out.result?.records)) setResult(out.result); };

  const confirmVoid = async reason => {
    const out = await command.run(key => api.voidAbsorbedUndercharge(voiding.ticketCode, voiding.underchargeReference, { reason }, key));
    if (!out.ok) return;
    setVoiding(null);
    setMessage('Record voided. It stays in the list and no longer counts in the total.');
    const reread = await command.run(() => api.listAbsorbedUndercharges(query()));
    if (reread.ok) setResult(reread.result);
  };

  if (!isManager) {
    return (
      <OpsPage eyebrow="ABSORBED UNDERCHARGES" title="Absorbed undercharges" intro="Undercharges Mland absorbed on finalized ticket folios.">
        <div className="notice error" role="alert"><p>Only a Manager can open the absorbed-undercharge list.</p></div>
      </OpsPage>
    );
  }
  const records = asList(result?.records);

  return (
    <OpsPage eyebrow="ABSORBED UNDERCHARGES" title="Absorbed undercharges" intro="Undercharges Mland absorbed on finalized ticket folios, by branch and period. A record is documentation only; record a new one on the ticket folio.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={retry} onReload={() => load()} />
      <section className="ota-panel">
        <form className="ota-inline-form" aria-label="Absorbed undercharge filters" onSubmit={load}>
          <label>Branch ID<input inputMode="numeric" value={filters.locationId} onChange={set('locationId')} /></label>
          <label>From<input type="date" value={filters.from} onChange={set('from')} /></label>
          <label>To<input type="date" value={filters.to} onChange={set('to')} /></label>
          <button className="button primary" type="submit" disabled={command.pending || !ready}>Show records</button>
        </form>
      </section>

      {command.pending && !result ? <Loading /> : null}
      {result && records.length === 0 && <div className="empty-state"><p>No absorbed-undercharge record for this branch and period.</p></div>}
      {result && records.length > 0 && (
        <section className="ota-panel">
          <div className="ota-panel-head"><h2>Records</h2><span className="muted">Total of ACTIVE records: <strong>{money(result.totalVnd)}</strong></span></div>
          <table className="ops-table">
            <thead><tr><th>Reference</th><th>Ticket</th><th>Amount</th><th>Reason</th><th>Status</th><th>Recorded</th><th /></tr></thead>
            <tbody>
              {records.map(record => (
                <tr key={record.underchargeReference}>
                  <td>{record.underchargeReference}</td>
                  <td>{record.ticketCode}</td>
                  <td>{money(record.amountVnd)}</td>
                  <td>{record.reason}</td>
                  <td>
                    <span className="ops-status">{record.status}</span>
                    {record.status === 'VOIDED' && <small className="muted"> not in the total{record.voidReason ? `: ${record.voidReason}` : ''}</small>}
                  </td>
                  <td>{record.recordedBy} · {when(record.recordedAt)}</td>
                  <td>
                    {record.status === 'ACTIVE' && (
                      <button type="button" className="button outline" disabled={command.pending} onClick={() => setVoiding(record)}>Void record</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      <ReasonDialog open={Boolean(voiding)} title="Void this absorbed-undercharge record" confirmLabel="Void record" pending={command.pending} onCancel={() => setVoiding(null)} onConfirm={confirmVoid}>
        <p>The record keeps its content, is marked VOIDED and stops counting in the total. It can be voided once; record a new one for a corrected amount.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
