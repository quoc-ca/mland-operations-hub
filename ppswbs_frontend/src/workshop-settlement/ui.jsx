import React, { useEffect, useState } from 'react';
import { ConfirmDialog, ErrorNotice, Field } from '../shared/ui/components';
import { confirmBuyerVerification, requestBuyerVerification } from '../api/workshops';
import { useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { buildContactProof, EMPTY_CONTACT } from '../workshops/contactValidation';
import { PhoneInput } from '../workshops/PhoneInput';

const EMPTY_PROOF = { ...EMPTY_CONTACT };

export const asList = value => (Array.isArray(value) ? value : (value?.items || value?.content || []));

/** Money text that may be negative (a cash difference, a reversal): the sign is kept and the digits are grouped by the shared formatter. */
export const signedMoney = (fmt, value) => (String(value ?? '').startsWith('-') ? `-${fmt.money(String(value).slice(1))}` : fmt.money(value));

export const splitList = text => text.split(/[,\s]+/).map(item => item.trim()).filter(Boolean);

/** Whole-VND input check for amounts typed by an operator (the server still validates). */
export const isWholeVnd = text => /^[1-9][0-9]*$/.test(String(text).trim());

/** 409, 422 and 429 get an explicit message (with the server code and failing tickets); other errors use the shared notice. */
export function ProblemNotice({ error, onRetry, onReload }) {
  const t = useT(messages);
  if (!error) return null;
  const codeKey = `problem.${error.code}`;
  const statusKey = `problem.status.${error.status}`;
  const codeText = t(codeKey) !== codeKey ? t(codeKey) : '';
  const statusText = t(statusKey) !== statusKey ? t(statusKey) : '';
  // A 403 with a code we can explain stays on this screen; any other 403 keeps the shared "no access" handling.
  const explained403 = error.status === 403 && codeText;
  if (!statusText && !explained403) return <ErrorNotice error={error} onRetry={onRetry} onReload={onReload} />;
  return (
    <div className="notice error" role="alert">
      <p>{codeText || statusText}</p>
      {error.code && <p className="muted small">{t('problem.code', { code: error.code })}</p>}
      {error.failingTicketCodes?.length > 0 && <p>{t('problem.blockedTickets', { codes: error.failingTicketCodes.join(', ') })}</p>}
      {error.correlationId && <p className="muted small">{t('problem.correlation', { id: error.correlationId })}</p>}
      <div className="notice-actions">
        {onReload && error.status === 409 && <button type="button" className="button outline" onClick={onReload}>{t('problem.reload')}</button>}
        {onRetry && error.retryable && <button type="button" className="button outline" onClick={onRetry}>{t('problem.retry')}</button>}
      </div>
    </div>
  );
}

/**
 * Confirmation step for a dangerous action: a reason is required before the action is sent. Cancel never calls the API.
 * onConfirm receives the trimmed reason; the caller sends it with its own Idempotency-Key.
 */
export function ReasonDialog({ open, title, children, confirmLabel, pending, onConfirm, onCancel }) {
  const t = useT(messages);
  const [reason, setReason] = useState('');
  const [missing, setMissing] = useState(false);
  useEffect(() => { if (open) { setReason(''); setMissing(false); } }, [open]);
  const submit = () => {
    if (!reason.trim()) { setMissing(true); return; }
    onConfirm(reason.trim());
  };
  return (
    <ConfirmDialog open={open} title={title} confirmLabel={confirmLabel} pending={pending} danger onConfirm={submit} onCancel={onCancel}>
      {children}
      <Field label={t('reason.label')} error={missing ? t('reason.missing') : undefined}>
        <textarea value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} />
      </Field>
    </ConfirmDialog>
  );
}

/**
 * Buyer proof for a Guest buyer or for Staff acting for the buyer (003.1 FR-041): one contact value of the group sends a
 * code, the code returns a single-use buyerVerificationReference that is kept in memory only and handed to onReference.
 * The contact value and the code never reach a URL or a log.
 */
export function BuyerProof({ groupCode, onReference, staffRequest }) {
  const t = useT(messages);
  const [contact, setContact] = useState(EMPTY_PROOF);
  const [verificationId, setVerificationId] = useState(null);
  const [maskedEmail, setMaskedEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  // One contact only, in the normalized form the server compares by; null while it is empty or malformed.
  const { proof } = buildContactProof({ email: contact.contactEmail, phone: contact.contactPhone, phoneCountry: contact.contactPhoneCountry });
  // A Staff or Manager caller needs no contact value: the code goes to the booking's own email (FR-048, T148).
  const oneContact = staffRequest ? Boolean(groupCode) : Boolean(proof);
  const send = async () => {
    setPending(true); setError('');
    try {
      const challenge = staffRequest ? await staffRequest(groupCode) : await requestBuyerVerification(groupCode, proof);
      setVerificationId(challenge?.verificationId || null);
      setMaskedEmail(challenge?.maskedEmail || '');
    } catch (err) {
      setError(err?.status === 429 ? t('proof.tooMany') : t('proof.sendFailed'));
    } finally { setPending(false); }
  };
  const confirm = async () => {
    setPending(true); setError('');
    try {
      const result = await confirmBuyerVerification(groupCode, verificationId, { otp });
      setOtp(''); setContact(EMPTY_PROOF);
      setDone(true);
      onReference(result?.buyerVerificationReference || null);
    } catch (err) {
      setError(err?.status === 429 ? t('proof.tooMany') : t('proof.notAccepted'));
    } finally { setPending(false); }
  };

  if (done) return <p className="muted" role="status">{t('proof.verified')}</p>;
  return (
    <fieldset className="settlement-proof">
      <legend>{t('proof.legend')}</legend>
      {!verificationId && staffRequest ? (
        <>
          <p className="muted small">{t('proof.staffHint')}</p>
          <button type="button" className="button outline" disabled={pending || !oneContact} onClick={send}>{t('proof.staffSend')}</button>
        </>
      ) : !verificationId ? (
        <>
          <p className="muted small">{t('proof.guestHint')}</p>
          <label>{t('proof.email')}<input type="email" autoComplete="off" value={contact.contactEmail} onChange={event => setContact(v => ({ ...v, contactEmail: event.target.value, contactPhone: '' }))} /></label>
          <label htmlFor="proof-contact-phone">{t('proof.phone')}</label>
          <PhoneInput
            id="proof-contact-phone"
            country={contact.contactPhoneCountry}
            value={contact.contactPhone}
            onCountryChange={contactPhoneCountry => setContact(v => ({ ...v, contactPhoneCountry }))}
            onValueChange={contactPhone => setContact(v => ({ ...v, contactPhone, contactEmail: '' }))}
          />
          {!oneContact && (contact.contactEmail.trim() || contact.contactPhone.trim()) && (
            <p className="field-error small">{t('proof.oneContact')}</p>
          )}
          <button type="button" className="button outline" disabled={pending || !groupCode || !oneContact} onClick={send}>{t('proof.send')}</button>
        </>
      ) : (
        <>
          {maskedEmail && <p className="muted small" role="status">{t('proof.sentPre')}<strong>{maskedEmail}</strong>{t('proof.sentPost')}</p>}
          <label>{t('proof.code')}<input inputMode="numeric" maxLength={6} autoComplete="off" value={otp} onChange={event => setOtp(event.target.value.replace(/\D/g, ''))} /></label>
          <button type="button" className="button primary" disabled={pending || otp.length !== 6} onClick={confirm}>{t('proof.verify')}</button>
          <button type="button" className="button outline" disabled={pending || !oneContact} onClick={send}>{t('proof.sendAgain')}</button>
        </>
      )}
      {error && <p role="alert" className="field-error">{error}</p>}
    </fieldset>
  );
}

/**
 * T163 (FR-042/FR-043): shown only when the stored voucher restoration reason is EXPIRED (never for ALREADY_RESTORED or a
 * restored voucher). The voucher is never extended and the money already paid is refunded by bank whatever happened to it.
 */
export function VoucherNotRestoredNotice({ voucherRestoration }) {
  const t = useT(messages);
  if (voucherRestoration?.reason !== 'EXPIRED') return null;
  return (
    <div className="notice" role="status" aria-label={t('voucher.aria')}>
      <p>{t('voucher.text')}</p>
    </div>
  );
}

/**
 * T151 (FR-040): the review step of an account entry. It shows bank, account number and holder name back and requires the
 * explicit "These details are correct" confirmation before the caller may submit; the caller sends accountDetailsConfirmed
 * only from this checkbox. The number is shown only here, from the entered values, and the caller clears them after sending.
 */
export function AccountReviewStep({ details, confirmed, onConfirmedChange, onEdit, pending, submitLabel, onSubmit }) {
  const t = useT(messages);
  return (
    <form className="wsp-form" aria-label={t('review.aria')} onSubmit={event => { event.preventDefault(); if (confirmed) onSubmit(); }}>
      <p className="muted">{t('review.check')}</p>
      <dl className="ops-facts">
        <div><dt>{t('review.bank')}</dt><dd>{details.bankCode}</dd></div>
        <div><dt>{t('review.accountNumber')}</dt><dd>{details.accountNumber}</dd></div>
        <div><dt>{t('review.holder')}</dt><dd>{details.holderName}</dd></div>
      </dl>
      <label className="settlement-addon-consent">
        <input type="checkbox" checked={confirmed} onChange={event => onConfirmedChange(event.target.checked)} />
        <span>{t('review.correct')}</span>
      </label>
      <div className="ops-actions">
        <button className="button outline" type="button" disabled={pending} onClick={onEdit}>{t('review.edit')}</button>
        <button className="button primary" type="submit" disabled={pending || !confirmed}>{submitLabel}</button>
      </div>
    </form>
  );
}
