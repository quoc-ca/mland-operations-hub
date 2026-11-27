import React, { useEffect, useRef, useState } from 'react';
import { ErrorNotice, useCommand } from '../../shared/ui/components';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';

const OPEN_STATES = ['PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'];

/**
 * What this role may change on a roster row (003.2 FR-002b, FR-008, FR-060): a Manager undoes in any open state, a
 * Staff (who only reaches assigned sessions) while the session runs; both correct a finalized session until the
 * ticket's window closes; a finalized folio rules out undo and "no-show" (FR-062). Navigation only: the server decides.
 */
export function rosterChange(role, session, row, folioStatus) {
  const none = { change: null, expired: false };
  if (role !== 'MANAGER' && role !== 'STAFF') return none;
  const reversible = role === 'MANAGER' ? OPEN_STATES : ['IN_PROGRESS'];
  const locked = folioStatus === 'FINALIZED';
  if (reversible.includes(session.state)) {
    if (row.attendanceState !== 'CHECKED_IN') return none;
    return locked ? { ...none, folioLocked: true } : { ...none, change: 'reverse' };
  }
  if (session.state !== 'ATTENDANCE_FINALIZED') return none;
  const change = { CHECKED_IN: 'markNoShow', NO_SHOW: 'markChecked' }[row.attendanceState];
  if (!change) return none;
  const expired = Boolean(row.correctionClosesAt) && new Date(row.correctionClosesAt).getTime() <= Date.now();
  if (expired) return { change: null, expired: true };
  if (change === 'markNoShow' && locked) return { ...none, folioLocked: true };
  return { change, expired: false };
}

/**
 * One ticket of the roster (003.2 FR-058): confirm a check-in, read the check-in time, and where the role allows it
 * undo or correct it with a standardized reason. Refusals are explained here; `onDone(text)` reports success to the page.
 */
export function RosterTicketDialog({ api, session, row, ticketCode, role, folioStatus, reasons, explain, onDone, onClose }) {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();
  const attendanceLabel = useEnumLabel(messages, 'attendance');
  const command = useCommand();
  const closeRef = useRef(null);
  useEffect(() => { closeRef.current?.focus(); }, []);
  const [reasonCode, setReasonCode] = useState('RECORDING_ERROR');
  const [reasonNote, setReasonNote] = useState('');

  const attendance = row.attendanceState;
  const canCheckIn = attendance === 'NOT_CHECKED_IN';
  const opensAt = session.checkInOpensAt ? new Date(session.checkInOpensAt).getTime() : null;
  const closedReason = !canCheckIn ? null
    : !OPEN_STATES.includes(session.state) ? t('ses.row.closedState')
      : opensAt && opensAt > Date.now() ? t('ses.opensAt', { when: fmt.dateTime(session.checkInOpensAt) }) : null;
  const { change, expired, folioLocked } = rosterChange(role, session, row, folioStatus);
  const reasonMissing = !reasonNote.trim();
  // Only a refused check-in has its own wording; a refused undo or correction keeps the server's reason.
  const refusal = canCheckIn ? explain(command.error) : null;

  async function send(action, success) {
    const outcome = await command.run(key => action(key));
    if (!outcome.ok) return;
    const repeat = outcome.result?.checkInResult === 'ALREADY_CHECKED_IN';
    onDone(repeat ? t('ses.alreadyCheckedIn', { when: fmt.dateTime(outcome.result.checkedInAt) }) : success);
  }

  const checkIn = () => send(key => api.checkIn(session.id, { ticketCode }, key), t('ses.ok.checkIn'));
  const applyChange = () => {
    const body = { ticketCode, reasonCode, reasonNote: reasonNote.trim() };
    if (change === 'reverse') return send(key => api.reverseCheckIn(session.id, body, key), t('ses.ok.reversal'));
    const correctedAttendanceState = change === 'markNoShow' ? 'NO_SHOW' : 'CHECKED_IN';
    return send(key => api.correctAttendance(session.id, { ...body, correctedAttendanceState }, key), t('ses.ok.correction'));
  };

  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={t('ses.row.title', { code: ticketCode })}
        onKeyDown={event => { if (event.key === 'Escape' && !command.pending) onClose(); }}>
        <h2>{t('ses.row.title', { code: ticketCode })}</h2>
        <p>
          <span className={`roster-state roster-${String(attendance).toLowerCase()}`}>{attendanceLabel(attendance)}</span>
        </p>
        {attendance === 'CHECKED_IN' && row.checkedInAt && <p>{t('ses.row.checkedInAt', { when: fmt.dateTime(row.checkedInAt) })}</p>}
        {canCheckIn && (closedReason
          ? <div className="notice error" role="alert"><p>{closedReason}</p></div>
          : <p>{t('ses.row.confirmCheckIn')}</p>)}
        {folioLocked && <p className="muted">{t('ses.row.folioLocked')}</p>}
        {expired && <p className="muted">{t('ses.row.windowClosed', { when: fmt.dateTime(row.correctionClosesAt) })}</p>}
        {change && session.state === 'ATTENDANCE_FINALIZED' && row.correctionClosesAt
          && <p className="muted">{t('ses.row.windowUntil', { when: fmt.dateTime(row.correctionClosesAt) })}</p>}
        {change && (
          <div className="wsp-form-grid">
            <label className="full-width">
              {t('ses.corr.reasonCode')}
              <select value={reasonCode} onChange={event => setReasonCode(event.target.value)}>
                {reasons.map(code => <option key={code} value={code}>{t(`ses.reason.checkin.${code}`)}</option>)}
              </select>
            </label>
            <label className="full-width">
              {t('ses.corr.reasonNote')}
              <textarea value={reasonNote} onChange={event => setReasonNote(event.target.value)} />
            </label>
          </div>
        )}
        {refusal ? <div className="notice error" role="alert"><p>{refusal}</p></div>
          : <ErrorNotice error={command.error} onRetry={command.retry} />}
        <div className="dialog-actions">
          <button type="button" ref={closeRef} className="button outline" onClick={onClose} disabled={command.pending}>{ts('cancel')}</button>
          {canCheckIn && !closedReason && (
            <button type="button" className="button primary" onClick={checkIn} disabled={command.pending}>{t('ses.row.checkIn')}</button>
          )}
          {change && (
            <button type="button" className={`button primary${change === 'markChecked' ? '' : ' danger'}`} onClick={applyChange}
              disabled={command.pending || reasonMissing}>{t(`ses.row.${change}`)}</button>
          )}
        </div>
      </div>
    </div>
  );
}
