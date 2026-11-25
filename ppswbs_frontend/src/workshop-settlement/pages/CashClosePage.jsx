import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ProblemNotice, ReasonDialog, signedMoney } from '../ui';

/** Cash close of one branch and working day (FR-036, FR-052). Every figure is the server's; the screen never computes a difference. */
export default function CashClosePage() {
  const t = useT(messages);
  const fmt = useFormat();
  const closeStatus = useEnumLabel(messages, 'closeStatus');
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [locationId, setLocationId] = useState('');
  const [workingDate, setWorkingDate] = useState('');
  const [expected, setExpected] = useState(null);
  const [counted, setCounted] = useState('');
  const [differenceReason, setDifferenceReason] = useState('');
  const [close, setClose] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [target, setTarget] = useState(null); // a difference chosen from the confirmation view
  // Manager home opens this view (014 R-3): the differences waiting for a Manager, the set its card counts.
  const [{ view }] = useUrlFilters({ view: '' });
  const showQueue = view === 'confirmation' && isManager;
  const queue = useResource(() => (showQueue ? api.listCashClosesAwaitingConfirmation() : Promise.resolve([])), [showQueue]);
  const [message, setMessage] = useState('');
  const command = useCommand();

  const loadExpected = async event => {
    event.preventDefault();
    setMessage(''); setClose(null);
    const out = await command.run(() => api.getExpectedCash({ locationId: locationId.trim(), ...(workingDate ? { workingDate } : {}) }));
    if (out.ok) setExpected(out.result);
  };

  const submitClose = async event => {
    event.preventDefault();
    setMessage('');
    const body = {
      locationId: Number(locationId),
      workingDate: expected?.workingDate || workingDate,
      countedAmount: counted.trim(),
      ...(differenceReason.trim() ? { differenceReason: differenceReason.trim() } : {}),
    };
    const out = await command.run(key => api.closeCash(body, key));
    if (out.ok) { setClose(out.result); setMessage(t('cash.closed')); }
  };

  const confirmDifference = async reason => {
    const subject = target || close;
    const out = await command.run(key => api.confirmCashDifference(subject.closeReference, { reason }, key));
    if (out.ok) {
      if (target) { setTarget(null); queue.reload(); } else setClose(out.result);
      setConfirming(false); setMessage(t('cash.differenceConfirmed'));
    }
  };

  return (
    <OpsPage title={t('cash.title')} intro={t('cash.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={command.retry} />
      {showQueue && (
        <section className="wsp-panel" aria-label={t('cash.queueAria')}>
          <div className="wsp-panel-head"><h2>{t('cash.queueTitle')}</h2><span>{queue.data ? queue.data.length : ''}</span></div>
          <ProblemNotice error={queue.error} onRetry={queue.reload} onReload={queue.reload} />
          {queue.loading ? <Loading /> : queue.error ? null : (queue.data || []).length === 0
            ? <div className="empty-state"><p>{t('cash.queueEmpty')}</p></div>
            : (
              <table className="ops-table">
                <thead><tr><th>{t('cash.col.close')}</th><th>{t('cash.col.branch')}</th><th>{t('cash.col.day')}</th><th>{t('cash.col.expected')}</th><th>{t('cash.col.counted')}</th><th>{t('cash.col.difference')}</th><th>{t('cash.col.reason')}</th><th /></tr></thead>
                <tbody>{queue.data.map(item => (
                  <tr key={item.closeReference}>
                    <td>{item.closeReference}</td><td>{item.locationId}</td><td>{item.workingDate}</td>
                    <td>{fmt.money(item.expectedCashVnd)}</td><td>{fmt.money(item.countedCashVnd)}</td><td>{signedMoney(fmt, item.differenceVnd)}</td>
                    <td>{item.differenceReason || '—'}</td>
                    <td><button className="button danger small" type="button" onClick={() => { setTarget(item); setConfirming(true); }}>{t('cash.confirmDifference')}</button></td>
                  </tr>
                ))}</tbody>
              </table>
            )}
        </section>
      )}
      <section className="wsp-panel">
        <form className="wsp-inline-form" aria-label={t('cash.expectedAria')} onSubmit={loadExpected}>
          <label>{t('cash.branchId')}<input inputMode="numeric" value={locationId} onChange={event => setLocationId(event.target.value)} /></label>
          <label>{t('cash.workingDay')}<input type="date" value={workingDate} onChange={event => setWorkingDate(event.target.value)} /></label>
          <button className="button outline" type="submit" disabled={command.pending || !/^[0-9]+$/.test(locationId.trim())}>{t('cash.showExpected')}</button>
        </form>
        {expected && (
          <dl className="ops-facts">
            <div><dt>{t('cash.workingDay')}</dt><dd>{expected.workingDate}</dd></div>
            <div><dt>{t('cash.expectedCash')}</dt><dd>{fmt.money(expected.expectedCashVnd)}</dd></div>
          </dl>
        )}
      </section>
      {expected && !close && (
        <section className="wsp-panel">
          <form className="wsp-form" aria-label={t('cash.closeAria')} onSubmit={submitClose}>
            <label>{t('cash.countedLabel')}<input inputMode="numeric" value={counted} onChange={event => setCounted(event.target.value)} /></label>
            <label>{t('cash.differenceReasonLabel')}<textarea value={differenceReason} maxLength={1000} onChange={event => setDifferenceReason(event.target.value)} /></label>
            <button className="button primary" type="submit" disabled={command.pending || !/^(0|[1-9][0-9]*)$/.test(counted.trim())}>{t('cash.closeBook')}</button>
          </form>
        </section>
      )}
      {close && (
        <section className="wsp-panel">
          <div className="wsp-panel-head"><h2>{close.closeReference}</h2><span className="ops-status">{closeStatus(close.status)}</span></div>
          <dl className="ops-facts">
            <div><dt>{t('cash.expected')}</dt><dd>{fmt.money(close.expectedCashVnd)}</dd></div>
            <div><dt>{t('cash.counted')}</dt><dd>{fmt.money(close.countedCashVnd)}</dd></div>
            <div><dt>{t('cash.difference')}</dt><dd>{signedMoney(fmt, close.differenceVnd)}</dd></div>
            {close.differenceReason ? <div><dt>{t('cash.reason')}</dt><dd>{close.differenceReason}</dd></div> : null}
          </dl>
          <p className="notice" role="status" aria-label={t('cash.cutoffAria')}>{t('cash.cutoff')}{close.status === 'PENDING_MANAGER_CONFIRMATION' ? t('cash.cutoffPending') : ''}{t('cash.cutoffEnd')}</p>
          {close.status === 'PENDING_MANAGER_CONFIRMATION' && (isManager
            ? <button className="button danger" type="button" onClick={() => setConfirming(true)}>{t('cash.confirmDifference')}</button>
            : <p className="muted">{t('cash.managerMustConfirm')}</p>)}
        </section>
      )}
      <ReasonDialog open={confirming} title={t('cash.dialogTitle')} confirmLabel={t('cash.confirmDifference')} pending={command.pending}
        onCancel={() => { setConfirming(false); setTarget(null); }} onConfirm={confirmDifference}>
        <p>{t('cash.dialogBody')}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
