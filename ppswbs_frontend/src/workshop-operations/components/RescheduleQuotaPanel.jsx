import React, { useState } from 'react';
import { ErrorNotice, Field, SuccessNotice, useCommand } from '../../shared/ui/components';

const CLOSED_STATES = ['ATTENDANCE_FINALIZED', 'CANCELLED'];

/**
 * 003.2 T113 / FR-035: a Manager reads and sets this session's reschedule quota with
 * PUT /workshop-sessions/{id}/reschedule-quota. "Restore default" sends quota null (the server recomputes
 * floor(25% × capacity), minimum 1); 0 closes the session to customer changes. A reason is required and every
 * command carries an Idempotency-Key. The figures shown are the server's (rescheduleQuota, …Overridden, …Used).
 */
export function RescheduleQuotaPanel({ api, session, onSaved, onReload }) {
  const [quota, setQuota] = useState('');
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');
  const command = useCommand();
  const closed = CLOSED_STATES.includes(session.state);

  const send = async value => {
    setNotice('');
    if (!reason.trim()) { setFormError('A reason is required to change the reschedule quota.'); return; }
    if (reason.trim().length > 500) { setFormError('The reason must not exceed 500 characters.'); return; }
    if (value !== null && (!Number.isInteger(value) || value < 0)) { setFormError('The quota must be a whole number of 0 or more.'); return; }
    setFormError('');
    const outcome = await command.run(key => api.setRescheduleQuota(session.id, { quota: value, reason: reason.trim() }, key));
    if (outcome.ok) {
      setNotice(value === null ? 'Reschedule quota restored to the default.' : value === 0 ? 'Session closed to customer changes.' : 'Reschedule quota updated.');
      setQuota('');
      setReason('');
      onSaved?.(outcome.result);
    }
  };

  const submit = e => {
    e.preventDefault();
    if (quota.trim() === '') { setFormError('Enter a quota, or use "Restore default".'); return; }
    send(Number(quota));
  };

  const effective = session.rescheduleQuota;
  const used = session.rescheduleQuotaUsed;
  return (
    <article className="ota-panel" aria-labelledby="reschedule-quota-title">
      <div className="ota-panel-head"><h2 id="reschedule-quota-title">Reschedule quota</h2></div>
      <dl className="ops-facts">
        <div><dt>Effective quota</dt><dd>{effective ?? '—'}{effective === 0 ? ' (closed to changes)' : ''}</dd></div>
        <div><dt>Source</dt><dd>{session.rescheduleQuotaOverridden ? 'Set by a Manager' : 'Default (25% of capacity, at least 1)'}</dd></div>
        <div><dt>Used</dt><dd>{used ?? '—'}</dd></div>
      </dl>
      {closed ? (
        <p className="muted small">The quota cannot change once the session is finalized or cancelled.</p>
      ) : (
        <form className="ota-form" onSubmit={submit} aria-label="Change reschedule quota">
          <Field label="New quota" hint="0 closes the session to customer changes. Tickets already moved in keep their seat.">
            <input type="number" min="0" step="1" value={quota} onChange={e => setQuota(e.target.value)} />
          </Field>
          <Field label="Reason">
            <textarea maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
          </Field>
          <div className="actions">
            <button className="button primary" disabled={command.pending}>Set quota</button>
            <button type="button" className="button outline" disabled={command.pending || !session.rescheduleQuotaOverridden} onClick={() => send(null)}>Restore default</button>
          </div>
        </form>
      )}
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} onRetry={command.retry} onReload={() => { command.reset(); onReload?.(); }} />
      <SuccessNotice>{notice}</SuccessNotice>
    </article>
  );
}
