import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, ReasonDialog, money } from '../ui';

/** Cash close of one branch and working day (FR-036, FR-052). Every figure is the server's; the screen never computes a difference. */
export default function CashClosePage() {
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
  // Manager home opens this view (013 R-3): the differences waiting for a Manager, the set its card counts.
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
    if (out.ok) { setClose(out.result); setMessage('Cash book closed.'); }
  };

  const confirmDifference = async reason => {
    const subject = target || close;
    const out = await command.run(key => api.confirmCashDifference(subject.closeReference, { reason }, key));
    if (out.ok) {
      if (target) { setTarget(null); queue.reload(); } else setClose(out.result);
      setConfirming(false); setMessage('Difference confirmed; the day is closed.');
    }
  };

  return (
    <OpsPage eyebrow="CASH BOOK" title="Cash close" intro="Count the drawer for one branch and day. A difference waits for a Manager's confirmation.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={command.retry} />
      {showQueue && (
        <section className="ota-panel" aria-label="Differences awaiting confirmation">
          <div className="ota-panel-head"><h2>Differences awaiting confirmation</h2><span>{queue.data ? queue.data.length : ''}</span></div>
          <ProblemNotice error={queue.error} onRetry={queue.reload} onReload={queue.reload} />
          {queue.loading ? <Loading /> : queue.error ? null : (queue.data || []).length === 0
            ? <div className="empty-state"><p>No cash-close difference is waiting for confirmation.</p></div>
            : (
              <table className="ops-table">
                <thead><tr><th>Close</th><th>Branch</th><th>Day</th><th>Expected</th><th>Counted</th><th>Difference</th><th>Reason</th><th /></tr></thead>
                <tbody>{queue.data.map(item => (
                  <tr key={item.closeReference}>
                    <td>{item.closeReference}</td><td>{item.locationId}</td><td>{item.workingDate}</td>
                    <td>{money(item.expectedCashVnd)}</td><td>{money(item.countedCashVnd)}</td><td>{money(item.differenceVnd)}</td>
                    <td>{item.differenceReason || '—'}</td>
                    <td><button className="button danger small" type="button" onClick={() => { setTarget(item); setConfirming(true); }}>Confirm difference</button></td>
                  </tr>
                ))}</tbody>
              </table>
            )}
        </section>
      )}
      <section className="ota-panel">
        <form className="ota-inline-form" aria-label="Expected cash" onSubmit={loadExpected}>
          <label>Branch ID<input inputMode="numeric" value={locationId} onChange={event => setLocationId(event.target.value)} /></label>
          <label>Working day<input type="date" value={workingDate} onChange={event => setWorkingDate(event.target.value)} /></label>
          <button className="button outline" type="submit" disabled={command.pending || !/^[0-9]+$/.test(locationId.trim())}>Show expected cash</button>
        </form>
        {expected && (
          <dl className="ops-facts">
            <div><dt>Working day</dt><dd>{expected.workingDate}</dd></div>
            <div><dt>Expected cash</dt><dd>{money(expected.expectedCashVnd)}</dd></div>
          </dl>
        )}
      </section>
      {expected && !close && (
        <section className="ota-panel">
          <form className="ota-form" aria-label="Close cash book" onSubmit={submitClose}>
            <label>Counted cash (VND)<input inputMode="numeric" value={counted} onChange={event => setCounted(event.target.value)} /></label>
            <label>Difference reason (if the count differs)<textarea value={differenceReason} maxLength={1000} onChange={event => setDifferenceReason(event.target.value)} /></label>
            <button className="button primary" type="submit" disabled={command.pending || !/^(0|[1-9][0-9]*)$/.test(counted.trim())}>Close cash book</button>
          </form>
        </section>
      )}
      {close && (
        <section className="ota-panel">
          <div className="ota-panel-head"><h2>{close.closeReference}</h2><span className="ops-status">{close.status}</span></div>
          <dl className="ops-facts">
            <div><dt>Expected</dt><dd>{money(close.expectedCashVnd)}</dd></div>
            <div><dt>Counted</dt><dd>{money(close.countedCashVnd)}</dd></div>
            <div><dt>Difference</dt><dd>{money(close.differenceVnd)}</dd></div>
            {close.differenceReason ? <div><dt>Reason</dt><dd>{close.differenceReason}</dd></div> : null}
          </dl>
          <p className="notice" role="status" aria-label="Cash close cut-off">Cash recorded from now on belongs to the next working day{close.status === 'PENDING_MANAGER_CONFIRMATION' ? ', even while this difference waits for the Manager’s confirmation' : ''}. The figures above are the server’s.</p>
          {close.status === 'PENDING_MANAGER_CONFIRMATION' && (isManager
            ? <button className="button danger" type="button" onClick={() => setConfirming(true)}>Confirm difference</button>
            : <p className="muted">A Manager must confirm this difference before the day is closed.</p>)}
        </section>
      )}
      <ReasonDialog open={confirming} title="Confirm the cash-close difference" confirmLabel="Confirm difference" pending={command.pending}
        onCancel={() => { setConfirming(false); setTarget(null); }} onConfirm={confirmDifference}>
        <p>The day closes with this difference on record. This is audited.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
