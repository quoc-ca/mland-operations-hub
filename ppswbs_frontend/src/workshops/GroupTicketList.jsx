import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  cancelTicket,
  changeTicketRecipient,
  changeTicketSessions,
  confirmBuyerVerification,
  getTicketChangeOptions,
  getTicketChangeRequestStatus,
  lookupTicketChangeOptions,
  requestBuyerVerification,
  WorkshopApiError,
} from '../api/workshops';
import { useFormat, useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { TicketSessionPicker } from '../shared/ui/TicketSessionPicker';
import { isValidEmail, normalizeEmail } from './contactValidation';
import {
  collectionKindLabel,
  formatSessionWindow,
  hasCurrentMoney,
  isTicketNoShow,
  nonRefundableNotice,
  ticketAttendanceLabel,
  ticketNoShowNote,
  ticketStatusLabel,
  vnd,
} from './groupDisplay';

const RECIPIENT_ERROR_CODES = new Set([
  'BUYER_VERIFICATION_REQUIRED',
  'BUYER_VERIFICATION_FAILED',
  'BUYER_VERIFICATION_LOCKED',
  'BUYER_VERIFICATION_EXPIRED',
  'RECIPIENT_CHANGE_FORBIDDEN',
  'TICKET_NOT_CONFIRMED',
  'TICKET_ALREADY_CHECKED_IN',
  'RECIPIENT_CHANGE_LIMIT_REACHED',
]);

const CHANGE_ERROR_CODES = new Set([
  'CHANGE_ALLOWANCE_EXHAUSTED',
  'CHANGE_REQUEST_PENDING',
  'ADVANCE_CHANGE_WINDOW_EXPIRED',
  'RESCHEDULE_QUOTA_EXHAUSTED',
  'INSUFFICIENT_CAPACITY',
  'INVALID_REPLACEMENT',
  'TICKET_FOLIO_NOT_OPEN',
  'FROM_SESSION_NOT_OF_TICKET',
  'PACKAGE_NOT_AVAILABLE_AT_LOCATION',
  'LATE_ARRIVAL_WINDOW_EXPIRED',
  'FROM_SESSIONS_MISMATCH',
]);

const describeRecipientError = (error, t, fallbackKey = 'ticket.err.sendFallback') => (error instanceof WorkshopApiError
  ? (RECIPIENT_ERROR_CODES.has(error.code) ? t(`ticket.err.${error.code}`) : error.detail || t(fallbackKey))
  : (error?.message || t(fallbackKey)));

const describeChangeError = (error, t, fallbackKey = 'ticket.err.changeFallback') => (error instanceof WorkshopApiError
  ? (CHANGE_ERROR_CODES.has(error.code) ? t(`ticket.err.${error.code}`)
    : RECIPIENT_ERROR_CODES.has(error.code) ? t(`ticket.err.${error.code}`) : error.detail || t(fallbackKey))
  : (error?.message || t(fallbackKey)));

const pickerLabels = t => ({
  loading: t('ticket.picker.loading'),
  loadFailed: t('ticket.picker.loadFailed'),
  retry: t('ticket.picker.retry'),
  sessionToChange: t('ticket.picker.sessionToChange'),
  bothSessions: t('ticket.picker.bothSessions'),
  sessionLabel: ordinal => t('ticket.picker.sessionLabel', { ordinal }),
  notChangeable: t('ticket.picker.notChangeable'),
  noOptions: t('ticket.picker.noOptions'),
  noPairs: t('ticket.picker.noPairs'),
  seatsLeft: count => t('ticket.picker.seatsLeft', { count }),
  quotaLeft: count => t('ticket.picker.quotaLeft', { count }),
  full: t('ticket.picker.full'),
  needsManager: t('ticket.picker.needsManager'),
  priceIncrease: amount => t('ticket.picker.priceIncrease', { amount }),
  allowanceUsed: t('ticket.picker.allowanceUsed'),
  windowClosed: t('ticket.picker.windowClosed'),
  choose: t('ticket.picker.choose'),
  pickPlaceholder: t('ticket.picker.pickPlaceholder'),
  rule: {
    CHANGE_ALLOWANCE_EXHAUSTED: t('ticket.picker.rule.CHANGE_ALLOWANCE_EXHAUSTED'),
    ADVANCE_CHANGE_WINDOW_EXPIRED: t('ticket.picker.rule.ADVANCE_CHANGE_WINDOW_EXPIRED'),
    RESCHEDULE_QUOTA_EXHAUSTED: t('ticket.picker.rule.RESCHEDULE_QUOTA_EXHAUSTED'),
  },
});

/**
 * Buyer verification of a Guest buyer before a recipient change (003.1 FR-041): the OTP always goes to the group's
 * original contact email, never to a recipient, and the answer never says whether the contact matched. The returned
 * reference is single-use and is kept only in memory.
 */
function BuyerVerification({ groupCode, proof, onVerified, purpose = 'send' }) {
  const t = useT(messages);
  const [stage, setStage] = useState('IDLE');
  const [verificationId, setVerificationId] = useState(null);
  const [otp, setOtp] = useState('');
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);

  const start = async () => {
    setPending(true);
    setError(null);
    try {
      const challenge = await requestBuyerVerification(groupCode, {
        contactEmail: proof?.contactEmail || undefined,
        contactPhone: proof?.contactPhone || undefined,
      });
      setVerificationId(challenge?.verificationId || null);
      setStage('OTP');
    } catch (err) {
      setError(describeRecipientError(err, t));
    } finally {
      setPending(false);
    }
  };

  const confirm = async () => {
    setPending(true);
    setError(null);
    try {
      const result = await confirmBuyerVerification(groupCode, verificationId, { otp });
      setOtp('');
      setStage('IDLE');
      onVerified(result?.buyerVerificationReference || null);
    } catch (err) {
      setError(describeRecipientError(err, t));
    } finally {
      setPending(false);
    }
  };

  return (
    <div style={{ marginTop: '10px', padding: '12px', background: '#f8fafc', border: '1px solid #dcdfe6' }}>
      {stage === 'IDLE' ? (
        <>
          <p className="muted" style={{ margin: '0 0 8px', fontSize: '13px' }}>
            {t('ticket.verify.intro', { purpose: t(`ticket.purpose.${purpose}`) })}
          </p>
          <button type="button" className="button outline small" onClick={start} disabled={pending}>
            {pending ? t('ticket.verify.sending') : t('ticket.verify.send')}
          </button>
        </>
      ) : (
        <>
          <label style={{ display: 'block' }}>
            {t('ticket.verify.otpLabel')}
            <input
              inputMode="numeric"
              maxLength={6}
              value={otp}
              onChange={event => setOtp(event.target.value.replace(/\D/g, ''))}
              disabled={pending}
            />
          </label>
          <div style={{ display: 'flex', gap: '8px', marginTop: '8px', flexWrap: 'wrap' }}>
            <button type="button" className="button primary small" onClick={confirm} disabled={pending || otp.length !== 6}>
              {pending ? t('ticket.verify.verifying') : t('ticket.verify.verify')}
            </button>
            <button type="button" className="button outline small" onClick={start} disabled={pending}>
              {t('ticket.verify.resend')}
            </button>
          </div>
        </>
      )}
      {error && <p role="alert" style={{ color: '#b00', margin: '8px 0 0', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

const HOURS_24 = 24 * 60 * 60 * 1000;

/**
 * The buyer cancels one ticket (003.3 FR-041/FR-042). Only a hint is computed here: the server decides the 24-hour
 * outcome. Points come back only for a cancellation at least 24 hours before the first session; the booking-time payment
 * is never refunded and the group voucher returns only when every ticket of the group ended validly.
 */
function CancelTicketForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const t = useT(messages);
  const { language } = useLanguage();
  const format = useFormat();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const needsVerification = !authorization && !reference;
  const firstStart = (ticket.sessions || []).map(s => new Date(s.scheduledStart).getTime()).filter(Number.isFinite).sort((a, b) => a - b)[0];
  const started = firstStart !== undefined && firstStart <= Date.now();
  const valid = firstStart !== undefined && firstStart - Date.now() >= HOURS_24;

  if (result) {
    return (
      <div role="status" style={{ marginTop: '10px', padding: '12px', background: '#f0fdf4', border: '1px solid #bbf7d0', fontSize: '13px' }}>
        <strong>{t('ticket.cancel.done', { code: result.ticketCode || ticket.ticketCode })}</strong>
        <p style={{ margin: '6px 0 0' }}>
          {result.benefitOutcome === 'VALID_CANCELLATION'
            ? t('ticket.cancel.validResult', { points: format.number(String(Math.round(Number(result.pointsReturned || 0)))) })
            : t('ticket.cancel.lateResult')}
          {' '}{nonRefundableNotice(language)}
        </p>
        {(result.voucherRestoration?.restored ?? result.voucherRestored) && <p style={{ margin: '6px 0 0' }}>{t('ticket.cancel.voucherRestored')}</p>}
        {result.voucherRestoration?.reason === 'EXPIRED' && (
          <p style={{ margin: '6px 0 0' }}>{t('ticket.cancel.voucherExpired')}</p>
        )}
      </div>
    );
  }
  if (started) return null;

  if (!open) {
    return (
      <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
        {t('ticket.cancel.open')}
      </button>
    );
  }

  const submit = async () => {
    setPending(true);
    setError(null);
    try {
      const outcome = await cancelTicket(ticket.ticketCode, {
        reason,
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      setReference(null);
      setResult(outcome || {});
      onUpdated?.();
    } catch (err) {
      setReference(null);
      setError(describeRecipientError(err, t, 'ticket.err.cancelFallback'));
    } finally {
      setPending(false);
    }
  };

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      <p role="note" style={{ margin: '0 0 8px', fontSize: '13px', lineHeight: 1.5 }}>
        {nonRefundableNotice(language)}{' '}
        {valid ? t('ticket.cancel.hintValid') : t('ticket.cancel.hintLate')}
        {' '}{t('ticket.cancel.hintVoucher')}
      </p>
      <label style={{ display: 'block' }}>
        {t('ticket.cancel.reason')}
        <input value={reason} onChange={event => setReason(event.target.value)} maxLength={1000} disabled={pending} />
      </label>
      {needsVerification && <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} purpose="cancel" />}
      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button type="button" className="button primary small" onClick={submit} disabled={pending || needsVerification || !reason.trim()}>
          {pending ? t('ticket.cancel.cancelling') : t('ticket.cancel.confirm')}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>{t('ticket.close')}</button>
      </div>
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

// The buyer's own change is always an advance change; late arrival is done by Staff at the shop (FR-044b).
const CHANGE_TYPE = 'ADVANCE';

/**
 * The buyer changes the sessions of one ticket (003.3 FR-044, FR-044a, FR-045). The picker lists only the sessions the
 * server says could replace the chosen one, with the exact seats left; the server re-checks everything on confirmation.
 * A Member sends the bearer; a Guest first proves the group with one contact to see the list (no OTP) and with the OTP
 * to commit. A price increase is confirmed first and the call repeats with a new key; a change that only a Manager may
 * allow is queued (202) and reported as sent. A session that has started cannot be changed online (FR-044b): the buyer is
 * told to contact the shop.
 */
const REQUEST_POLL_MS = 20000;

function ChangeScheduleForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const t = useT(messages);
  const { language } = useLanguage();
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState(null);
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [increase, setIncrease] = useState(null);
  const [outcome, setOutcome] = useState(null);
  const [pickerKey, setPickerKey] = useState(0);
  const needsVerification = !authorization && !reference;
  const code = ticket.ticketCode;

  // A queued request is decided by a Manager elsewhere. The buyer Member reads its state from the server: on load (so a
  // reload still shows "waiting") and every REQUEST_POLL_MS while it waits. Guests have no such read; their notice stays local.
  const [decision, setDecision] = useState(null);
  const onUpdatedRef = useRef(onUpdated);
  onUpdatedRef.current = onUpdated;
  const queuedReference = outcome?.queued ? outcome.reference : null;
  useEffect(() => {
    if (!authorization || decision) return undefined;
    let active = true;
    let busy = false;
    const read = async () => {
      if (busy || document.visibilityState === 'hidden') return;
      busy = true;
      try {
        const latest = await getTicketChangeRequestStatus(code, { authorization });
        if (!active || !latest) return;
        if (latest.status === 'PENDING') {
          setOutcome(current => (current?.queued ? current : { queued: true, reference: latest.requestReference }));
        } else if (queuedReference && latest.requestReference === queuedReference) {
          setDecision(latest);
          onUpdatedRef.current?.();
        }
      } catch {
        // The notice keeps what it shows; the next read tries again.
      } finally {
        busy = false;
      }
    };
    read();
    if (!queuedReference) return () => { active = false; };
    const timer = setInterval(read, REQUEST_POLL_MS);
    document.addEventListener('visibilitychange', read);
    return () => { active = false; clearInterval(timer); document.removeEventListener('visibilitychange', read); };
  }, [authorization, code, queuedReference, decision]);

  const loadOptions = useCallback(from => (authorization
    ? getTicketChangeOptions(code, { type: CHANGE_TYPE, fromSessionReferences: from, authorization })
    : lookupTicketChangeOptions(code, {
      contactEmail: proof?.contactEmail || undefined,
      contactPhone: proof?.contactPhone || undefined,
      type: CHANGE_TYPE,
      fromSessionReferences: from,
    })), [code, authorization, proof?.contactEmail, proof?.contactPhone]);

  const submit = async confirmIncrease => {
    if (!selection) return;
    setPending(true);
    setError(null);
    try {
      const result = await changeTicketSessions(code, {
        type: CHANGE_TYPE,
        fromSessionReferences: selection.fromSessionReferences,
        targetSessionReferences: selection.targetSessionReferences,
        confirmIncrease: confirmIncrease || undefined,
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      if (result?.confirmIncreaseRequired && !result.applied) {
        // Nothing changed yet and the OTP is not used up: show the amount and ask the buyer to confirm.
        setIncrease(result.priceIncreaseAmount);
        return;
      }
      setIncrease(null);
      setReference(null);
      if (result?.changeRequestReference && !result.applied) {
        setOutcome({ queued: true, reference: result.changeRequestReference });
      } else {
        setOutcome({ queued: false, balanceDue: result?.balanceDue, increase: result?.priceIncreaseAmount });
        onUpdated?.(result);
      }
    } catch (err) {
      if (String(err?.code || '').startsWith('BUYER_VERIFICATION_')) setReference(null);
      setIncrease(null);
      // The pair stopped being sold at that location: re-read the list and clear the choice (FR-059 d).
      if (err?.code === 'PACKAGE_NOT_AVAILABLE_AT_LOCATION') { setSelection(null); setPickerKey(value => value + 1); }
      setError(describeChangeError(err, t));
    } finally {
      setPending(false);
    }
  };

  if (decision) {
    const approved = decision.status === 'APPROVED';
    const title = approved ? t('ticket.change.approved')
      : decision.status === 'REJECTED' ? t('ticket.change.rejected') : t('ticket.change.expired');
    return (
      <div role="status" style={{ marginTop: '10px', padding: '12px', fontSize: '13px', background: approved ? '#f0fdf4' : '#fef2f2', border: approved ? '1px solid #bbf7d0' : '1px solid #fecaca' }}>
        <strong>{title}</strong>
        <p style={{ margin: '6px 0 0' }}>{t('ticket.change.requestCode')} <code>{decision.requestReference}</code>.</p>
        {decision.decisionReason && <p style={{ margin: '6px 0 0' }}>{t('ticket.change.reason')} {decision.decisionReason}</p>}
      </div>
    );
  }

  if (outcome) {
    return (
      <div role="status" style={{ marginTop: '10px', padding: '12px', background: '#f0fdf4', border: '1px solid #bbf7d0', fontSize: '13px' }}>
        {outcome.queued ? (
          <>
            <strong>{t('ticket.change.sent')}</strong>
            <p style={{ margin: '6px 0 0' }}>{t('ticket.change.requestCode')} <code>{outcome.reference}</code>. {t('ticket.change.willEmail')}</p>
          </>
        ) : (
          <>
            <strong>{t('ticket.change.done', { code })}</strong>
            {Number(outcome.increase) > 0 && <p style={{ margin: '6px 0 0' }}>{t('ticket.change.increaseAdded', { amount: vnd(outcome.increase, language) })}</p>}
          </>
        )}
      </div>
    );
  }

  const sessions = ticket.sessions || [];
  const started = sessions.filter(session => new Date(session.scheduledStart).getTime() <= Date.now());
  const branches = [...new Set(started.map(session => session.locationName).filter(Boolean))].join(', ');
  // The group data carries the branch name only (no phone number).
  const contactShop = started.length > 0 && (
    <p role="note" style={{ margin: '10px 0 0', fontSize: '13px', color: '#874d00' }}>
      {t(branches ? 'ticket.change.contactShopAt' : 'ticket.change.contactShop', {
        sessions: started.map(session => session.ordinal).join(', '),
        branch: branches,
      })}
    </p>
  );
  if (started.length > 0 && started.length === sessions.length) return contactShop;

  if (!open) {
    return (
      <>
        {contactShop}
        <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
          {t('ticket.change.open')}
        </button>
      </>
    );
  }

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      {contactShop}
      <TicketSessionPicker
        key={pickerKey}
        ticketCode={code}
        type={CHANGE_TYPE}
        loadOptions={loadOptions}
        onSelect={next => { setSelection(next); setIncrease(null); if (next) setError(null); }}
        labels={pickerLabels(t)}
        formatWindow={(start, end) => formatSessionWindow(start, end, language)}
        formatMoney={value => vnd(value, language)}
      />
      {selection?.requiresManager && (
        <p role="note" style={{ margin: '8px 0 0', fontSize: '13px', color: '#874d00' }}>
          {t('ticket.change.needsManagerNote')}
        </p>
      )}
      {selection && needsVerification && (
        <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} purpose="change" />
      )}
      {increase !== null && (
        <div role="status" style={{ marginTop: '8px', padding: '10px 12px', background: '#fff8e1', border: '1px solid #f0d58c', fontSize: '13px' }}>
          {t('ticket.change.increasePrefix')}<strong>{vnd(increase, language)}</strong>{t('ticket.change.increaseSuffix')}
          <div style={{ marginTop: '8px' }}>
            <button type="button" className="button primary small" onClick={() => submit(true)} disabled={pending}>
              {t('ticket.change.agreeIncrease')}
            </button>
          </div>
        </div>
      )}
      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className="button primary small"
          onClick={() => submit(false)}
          disabled={pending || !selection || needsVerification || increase !== null}
        >
          {pending ? t('ticket.sending') : (selection?.requiresManager ? t('ticket.change.sendRequest') : t('ticket.change.confirm'))}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>{t('ticket.close')}</button>
      </div>
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

/** The recipient form of one ticket (FR-041). The first send keeps the QR; a different email issues a new one. */
function RecipientForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const t = useT(messages);
  const [open, setOpen] = useState(false);
  const [recipientEmail, setRecipientEmail] = useState('');
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const needsVerification = !authorization && !reference;
  const changesLeft = ticket.onlineRecipientChangesRemaining;
  const limitReached = changesLeft !== null && changesLeft !== undefined && Number(changesLeft) <= 0;

  const submit = async () => {
    setPending(true);
    setError(null);
    setSuccess(null);
    try {
      const updated = await changeTicketRecipient(ticket.ticketCode, {
        recipientEmail: normalizeEmail(recipientEmail),
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      // The reference is single-use: a further change needs a new verification.
      setReference(null);
      setSuccess(t('ticket.recipient.sent'));
      setRecipientEmail('');
      onUpdated?.(updated);
    } catch (err) {
      setReference(null);
      setError(describeRecipientError(err, t));
    } finally {
      setPending(false);
    }
  };

  if (!open) {
    return (
      <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
        {t('ticket.recipient.open')}
      </button>
    );
  }

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      <label style={{ display: 'block' }}>
        {t('ticket.recipient.email')}
        <input
          type="email"
          value={recipientEmail}
          onChange={event => setRecipientEmail(event.target.value)}
          disabled={pending || limitReached}
        />
      </label>
      {changesLeft !== null && changesLeft !== undefined && (
        <p className="muted" style={{ margin: '6px 0 0', fontSize: '12px' }}>
          {t('ticket.recipient.changesLeft', { count: changesLeft })}
        </p>
      )}
      {limitReached && (
        <p role="alert" style={{ color: '#b00', fontSize: '13px' }}>{t('ticket.err.RECIPIENT_CHANGE_LIMIT_REACHED')}</p>
      )}

      {needsVerification && !limitReached && (
        <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} />
      )}

      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className="button primary small"
          onClick={submit}
          disabled={pending || limitReached || needsVerification || !isValidEmail(recipientEmail)}
        >
          {pending ? t('ticket.sending') : t('ticket.recipient.send')}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>
          {t('ticket.close')}
        </button>
      </div>
      {success && <p role="status" style={{ color: '#0b5c34', marginTop: '8px', fontSize: '13px' }}>{success}</p>}
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

/**
 * Every ticket booking of a group with its own facts (003.1 FR-042, FR-049): status, price, amount collected at
 * booking, balance left at checkout, collection kind, masked recipient, attendance and — for the buyer only — the QR
 * reference. An action on one ticket never touches another ticket of the group (FR-030).
 */
export function GroupTicketList({ group, authorization, proof, onTicketUpdated, allowRecipientChange = true, allowCancel = true, allowChange = allowCancel, renderTicketAction }) {
  const t = useT(messages);
  const { language } = useLanguage();
  const tickets = group?.tickets || [];
  if (tickets.length === 0) {
    return <p className="muted">{t('ticket.list.empty')}</p>;
  }

  return (
    <div className="group-tickets" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(300px, 100%), 1fr))', gap: '12px' }}>
      {tickets.map(ticket => {
        const attendance = ticketAttendanceLabel(ticket, language);
        const noShow = isTicketNoShow(ticket);
        return (
          <article key={ticket.ticketCode} style={{ background: '#fff', border: '1px solid #d9d9d9', padding: '14px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', flexWrap: 'wrap' }}>
              <div>
                <span className="muted" style={{ fontSize: '12px' }}>{t('ticket.code')}</span>
                <strong style={{ display: 'block', fontSize: '18px', color: '#002967' }}>{ticket.ticketCode}</strong>
              </div>
              <span className={`status ${ticket.ticketStatus === 'CONFIRMED' && !noShow ? 'ok' : ''}`}>
                {attendance || ticketStatusLabel(ticket.ticketStatus, language) || ticket.ticketStatus}
              </span>
            </div>

            <p style={{ margin: '8px 0 0', fontSize: '13px' }}>
              {ticket.packageName || t('ticket.package', { id: ticket.packageId })} · {t('ticket.person', { ordinal: ticket.participantOrdinal })}
            </p>

            {Array.isArray(ticket.sessions) && ticket.sessions.length > 0 && (
              <ul className="muted" style={{ margin: '6px 0 0', paddingLeft: '18px', fontSize: '12px' }}>
                {ticket.sessions.map(session => (
                  <li key={session.sessionReference}>
                    {t('ticket.sessionLine', { ordinal: session.ordinal })} {formatSessionWindow(session.scheduledStart, session.scheduledEnd, language)}
                    {session.locationName ? ` · ${session.locationName}` : ''}
                  </li>
                ))}
              </ul>
            )}

            <dl style={{ margin: '10px 0 0', fontSize: '13px', display: 'grid', gridTemplateColumns: 'auto auto', gap: '2px 10px' }}>
              {/* Once the ticket has a folio, its figures win: add-ons and later payments are only there. */}
              {hasCurrentMoney(ticket) ? (
                <>
                  <dt>{t('ticket.price')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.finalPriceVnd, language)}</dd>
                  {Number(ticket.currentAddOnVnd) > 0 && (
                    <><dt>{t('ticket.addOn')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.currentAddOnVnd, language)}</dd></>
                  )}
                  <dt>{t('ticket.paidSoFar')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.currentPaidVnd, language)}</dd>
                  <dt>{t('ticket.remaining')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{ticket.ticketStatus === 'CANCELLED' ? t('ticket.notCollected') : vnd(ticket.currentBalanceDueVnd, language)}</dd>
                </>
              ) : (
                <>
                  <dt>{t('ticket.price')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.finalPriceVnd, language)}</dd>
                  <dt>{t('ticket.paidAtBooking')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.collectedAtBookingVnd, language)}</dd>
                  <dt>{t('ticket.remaining')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{ticket.ticketStatus === 'CANCELLED' ? t('ticket.notCollected') : vnd(ticket.remainingVnd, language)}</dd>
                </>
              )}
              <dt>{t('ticket.collectionKind')}</dt><dd style={{ margin: 0, textAlign: 'right' }}>{collectionKindLabel(ticket.collectionKind, language)}</dd>
            </dl>

            <p className="muted" style={{ margin: '8px 0 0', fontSize: '12px' }}>
              {t('ticket.recipientLabel')} {ticket.recipientEmailMasked || t('ticket.recipientBuyer')}
            </p>

            {ticket.qrReference && (
              <>
                <span className="muted" style={{ fontSize: '12px', marginTop: '6px', display: 'block' }}>{t('ticket.qr')}</span>
                <code style={{ fontSize: '12px', background: '#f5f5f5', padding: '2px 6px' }}>{ticket.qrReference}</code>
              </>
            )}

            {noShow && (
              <p role="note" style={{ margin: '8px 0 0', fontSize: '12px', color: '#874d00' }}>{ticketNoShowNote(language)}</p>
            )}

            {ticket.mlandRemedyPending && (
              <p role="note" style={{ margin: '10px 0 0', padding: '10px 12px', background: '#fff8e1', border: '1px solid #f0d58c', fontSize: '13px' }}>
                {t('ticket.remedyNote')}{' '}
                <a className="text-link" href={`/workshop-tickets/${encodeURIComponent(ticket.ticketCode)}/remedy`}>{t('ticket.remedyLink')}</a>
              </p>
            )}

            {(ticket.ticketStatus === 'CONFIRMED' || ticket.ticketStatus === 'COMPLETED') && (
              <p style={{ margin: '10px 0 0' }}>
                <a className="text-link" href={`/workshop-tickets/${encodeURIComponent(ticket.ticketCode)}/folio`}>{t('ticket.folioLink')}</a>
              </p>
            )}

            {allowChange && ticket.ticketStatus === 'CONFIRMED' && !noShow && !ticket.attendanceStatus && (
              <ChangeScheduleForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
            )}

            {allowCancel && ticket.ticketStatus === 'CONFIRMED' && !noShow && !ticket.attendanceStatus && (
              <CancelTicketForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
            )}

            {allowRecipientChange && ticket.ticketStatus === 'CONFIRMED' && !noShow && !ticket.attendanceStatus && (
              <RecipientForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
            )}
            {renderTicketAction ? renderTicketAction(ticket) : null}
          </article>
        );
      })}
    </div>
  );
}
