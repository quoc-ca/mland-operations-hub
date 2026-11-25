import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ProblemNotice, ReasonDialog, asList } from '../ui';

/**
 * Manager list of absorbed-undercharge records of one branch and period (FR-057, T136). Documentation only: nothing is
 * collected or changed on a folio. VOIDED records stay visible and are marked; the total is the server's totalVnd of ACTIVE records.
 */
export default function AbsorbedUnderchargesPage() {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'underStatus');
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
    setMessage(t('under.voided'));
    const reread = await command.run(() => api.listAbsorbedUndercharges(query()));
    if (reread.ok) setResult(reread.result);
  };

  if (!isManager) {
    return (
      <OpsPage title={t('under.title')} intro={t('under.introShort')}>
        <div className="notice error" role="alert"><p>{t('under.managerOnly')}</p></div>
      </OpsPage>
    );
  }
  const records = asList(result?.records);

  return (
    <OpsPage title={t('under.title')} intro={t('under.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={retry} onReload={() => load()} />
      <section className="ota-panel">
        <form className="ota-inline-form" aria-label={t('under.filtersAria')} onSubmit={load}>
          <label>{t('under.branchId')}<input inputMode="numeric" value={filters.locationId} onChange={set('locationId')} /></label>
          <label>{t('under.from')}<input type="date" value={filters.from} onChange={set('from')} /></label>
          <label>{t('under.to')}<input type="date" value={filters.to} onChange={set('to')} /></label>
          <button className="button primary" type="submit" disabled={command.pending || !ready}>{t('under.show')}</button>
        </form>
      </section>

      {command.pending && !result ? <Loading /> : null}
      {result && records.length === 0 && <div className="empty-state"><p>{t('under.empty')}</p></div>}
      {result && records.length > 0 && (
        <section className="ota-panel">
          <div className="ota-panel-head"><h2>{t('under.records')}</h2><span className="muted">{t('under.total')}: <strong>{fmt.money(result.totalVnd)}</strong></span></div>
          <table className="ops-table">
            <thead><tr><th>{t('under.col.reference')}</th><th>{t('under.col.ticket')}</th><th>{t('under.col.amount')}</th><th>{t('under.col.reason')}</th><th>{t('under.col.status')}</th><th>{t('under.col.recorded')}</th><th /></tr></thead>
            <tbody>
              {records.map(record => (
                <tr key={record.underchargeReference}>
                  <td>{record.underchargeReference}</td>
                  <td>{record.ticketCode}</td>
                  <td>{fmt.money(record.amountVnd)}</td>
                  <td>{record.reason}</td>
                  <td>
                    <span className="ops-status">{statusLabel(record.status)}</span>
                    {record.status === 'VOIDED' && <small className="muted">{t('under.notInTotal')}{record.voidReason ? `: ${record.voidReason}` : ''}</small>}
                  </td>
                  <td>{record.recordedBy} · {fmt.dateTime(record.recordedAt)}</td>
                  <td>
                    {record.status === 'ACTIVE' && (
                      <button type="button" className="button outline" disabled={command.pending} onClick={() => setVoiding(record)}>{t('under.voidRecord')}</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      <ReasonDialog open={Boolean(voiding)} title={t('under.voidTitle')} confirmLabel={t('under.voidRecord')} pending={command.pending} onCancel={() => setVoiding(null)} onConfirm={confirmVoid}>
        <p>{t('under.voidBody')}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
