import React, { useState } from 'react';
import { ErrorNotice, Field, SuccessNotice, useCommand } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';

const CLOSED_STATES = ['ATTENDANCE_FINALIZED', 'CANCELLED'];

/**
 * 003.2 T113 / FR-035: a Manager reads and sets this session's reschedule quota with
 * PUT /workshop-sessions/{id}/reschedule-quota. "Restore default" sends quota null (the server recomputes
 * floor(25% × capacity), minimum 1); 0 closes the session to customer changes. A reason is required and every
 * command carries an Idempotency-Key. The figures shown are the server's (rescheduleQuota, …Overridden, …Used).
 */
export function RescheduleQuotaPanel({ api, session, onSaved, onReload }) {
  const t = useT(messages);
  const [quota, setQuota] = useState('');
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');
  const command = useCommand();
  const closed = CLOSED_STATES.includes(session.state);

  const send = async value => {
    setNotice('');
    if (!reason.trim()) { setFormError(t('quota.reasonRequired')); return; }
    if (reason.trim().length > 500) { setFormError(t('quota.reasonLength')); return; }
    if (value !== null && (!Number.isInteger(value) || value < 0)) { setFormError(t('quota.invalid')); return; }
    setFormError('');
    const outcome = await command.run(key => api.setRescheduleQuota(session.id, { quota: value, reason: reason.trim() }, key));
    if (outcome.ok) {
      setNotice(value === null ? t('quota.restored') : value === 0 ? t('quota.closed') : t('quota.updated'));
      setQuota('');
      setReason('');
      onSaved?.(outcome.result);
    }
  };

  const submit = e => {
    e.preventDefault();
    if (quota.trim() === '') { setFormError(t('quota.enter')); return; }
    send(Number(quota));
  };

  const effective = session.rescheduleQuota;
  const used = session.rescheduleQuotaUsed;
  return (
    <article className="ota-panel" aria-labelledby="reschedule-quota-title">
      <div className="ota-panel-head"><h2 id="reschedule-quota-title">{t('quota.title')}</h2></div>
      <dl className="ops-facts">
        <div><dt>{t('quota.effective')}</dt><dd>{effective ?? '—'}{effective === 0 ? t('quota.closedSuffix') : ''}</dd></div>
        <div><dt>{t('quota.source')}</dt><dd>{session.rescheduleQuotaOverridden ? t('quota.sourceManager') : t('quota.sourceDefault')}</dd></div>
        <div><dt>{t('quota.used')}</dt><dd>{used ?? '—'}</dd></div>
      </dl>
      {closed ? (
        <p className="muted small">{t('quota.cannotChange')}</p>
      ) : (
        <form className="ota-form" onSubmit={submit} aria-label={t('quota.formAria')}>
          <Field label={t('quota.new')} hint={t('quota.hint')}>
            <input type="number" min="0" step="1" value={quota} onChange={e => setQuota(e.target.value)} />
          </Field>
          <Field label={t('quota.reason')}>
            <textarea maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
          </Field>
          <div className="actions">
            <button className="button primary" disabled={command.pending}>{t('quota.set')}</button>
            <button type="button" className="button outline" disabled={command.pending || !session.rescheduleQuotaOverridden} onClick={() => send(null)}>{t('quota.restore')}</button>
          </div>
        </form>
      )}
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} onRetry={command.retry} onReload={() => { command.reset(); onReload?.(); }} />
      <SuccessNotice>{notice}</SuccessNotice>
    </article>
  );
}
