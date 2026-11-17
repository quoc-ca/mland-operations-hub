import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { SuccessNotice, useCommand } from '../../shared/ui/components';
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
    const out = await command.run(key => api.confirmCashDifference(close.closeReference, { reason }, key));
    if (out.ok) { setClose(out.result); setConfirming(false); setMessage('Difference confirmed; the day is closed.'); }
  };

  return (
    <OpsPage eyebrow="CASH BOOK" title="Cash close" intro="Count the drawer for one branch and day. A difference waits for a Manager's confirmation.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={command.retry} />
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
        onCancel={() => setConfirming(false)} onConfirm={confirmDifference}>
        <p>The day closes with this difference on record. This is audited.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
