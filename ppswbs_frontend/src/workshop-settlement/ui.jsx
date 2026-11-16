import React, { useEffect, useState } from 'react';
import { ConfirmDialog, ErrorNotice, Field } from '../shared/ui/components';
import { confirmBuyerVerification, requestBuyerVerification } from '../api/workshops';
import { buildContactProof, EMPTY_CONTACT } from '../workshops/contactValidation';
import { PhoneInput } from '../workshops/PhoneInput';

const EMPTY_PROOF = { ...EMPTY_CONTACT };

/** Whole-VND text as the server sent it, with thousands dots; never rounds or recalculates. */
export function money(value) {
  if (value === undefined || value === null || value === '') return '—';
  return `${String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.')} VND`;
}

export function when(value) {
  return value ? new Date(value).toLocaleString() : '—';
}

export const asList = value => (Array.isArray(value) ? value : (value?.items || value?.content || []));

export const splitList = text => text.split(/[,\s]+/).map(item => item.trim()).filter(Boolean);

/** Whole-VND input check for amounts typed by an operator (the server still validates). */
export const isWholeVnd = text => /^[1-9][0-9]*$/.test(String(text).trim());

const STATUS_TEXT = {
  409: 'The record changed or the action conflicts with its current state. Reload the latest data and try again.',
  422: 'The server refused this request.',
  429: 'Too many attempts. Wait a moment before trying again.',
};

/** 409, 422 and 429 get an explicit message (with the server code and failing tickets); other errors use the shared notice. */
export function ProblemNotice({ error, onRetry, onReload }) {
  if (!error) return null;
  if (!STATUS_TEXT[error.status]) return <ErrorNotice error={error} onRetry={onRetry} onReload={onReload} />;
  return (
    <div className="notice error" role="alert">
      <p>{STATUS_TEXT[error.status]}</p>
      {error.code && <p className="muted small">Code: {error.code}</p>}
      {error.failingTicketCodes?.length > 0 && <p>Blocked tickets: {error.failingTicketCodes.join(', ')}</p>}
      {error.correlationId && <p className="muted small">Correlation: {error.correlationId}</p>}
      <div className="notice-actions">
        {onReload && error.status === 409 && <button type="button" className="button outline" onClick={onReload}>Load latest</button>}
        {onRetry && error.retryable && <button type="button" className="button outline" onClick={onRetry}>Try again</button>}
      </div>
    </div>
  );
}

/**
 * Confirmation step for a dangerous action: a reason is required before the action is sent. Cancel never calls the API.
 * onConfirm receives the trimmed reason; the caller sends it with its own Idempotency-Key.
 */
export function ReasonDialog({ open, title, children, confirmLabel, pending, onConfirm, onCancel }) {
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
      <Field label="Reason (required)" error={missing ? 'A reason is required.' : undefined}>
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
export function BuyerProof({ groupCode, onReference }) {
  const [contact, setContact] = useState(EMPTY_PROOF);
  const [verificationId, setVerificationId] = useState(null);
  const [otp, setOtp] = useState('');
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  // One contact only, in the normalized form the server compares by; null while it is empty or malformed.
  const { proof } = buildContactProof({ email: contact.contactEmail, phone: contact.contactPhone, phoneCountry: contact.contactPhoneCountry });
  const oneContact = Boolean(proof);
  const send = async () => {
    setPending(true); setError('');
    try {
      const challenge = await requestBuyerVerification(groupCode, proof);
      setVerificationId(challenge?.verificationId || null);
    } catch (err) {
      setError(err?.status === 429 ? 'Too many attempts. Wait a moment before trying again.' : 'The code could not be sent. Check the contact and try again.');
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
      setError(err?.status === 429 ? 'Too many attempts. Wait a moment before trying again.' : 'The code was not accepted.');
    } finally { setPending(false); }
  };

  if (done) return <p className="muted" role="status">Buyer verified. The proof is used once, when the action commits.</p>;
  return (
    <fieldset className="settlement-proof">
      <legend>Buyer verification</legend>
      {!verificationId ? (
        <>
          <p className="muted small">Enter exactly one contact of the booking; a code is sent to the contact on file.</p>
          <label>Contact email<input type="email" autoComplete="off" value={contact.contactEmail} onChange={event => setContact(v => ({ ...v, contactEmail: event.target.value }))} /></label>
          <label htmlFor="proof-contact-phone">Contact phone</label>
          <PhoneInput
            id="proof-contact-phone"
            country={contact.contactPhoneCountry}
            value={contact.contactPhone}
            onCountryChange={contactPhoneCountry => setContact(v => ({ ...v, contactPhoneCountry }))}
            onValueChange={contactPhone => setContact(v => ({ ...v, contactPhone }))}
          />
          {!oneContact && (contact.contactEmail.trim() || contact.contactPhone.trim()) && (
            <p className="field-error small">Enter one valid email or one valid phone number.</p>
          )}
          <button type="button" className="button outline" disabled={pending || !groupCode || !oneContact} onClick={send}>Send code</button>
        </>
      ) : (
        <>
          <label>Code (6 digits)<input inputMode="numeric" maxLength={6} autoComplete="off" value={otp} onChange={event => setOtp(event.target.value.replace(/\D/g, ''))} /></label>
          <button type="button" className="button primary" disabled={pending || otp.length !== 6} onClick={confirm}>Verify</button>
          <button type="button" className="button outline" disabled={pending || !oneContact} onClick={send}>Send again</button>
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
  if (voucherRestoration?.reason !== 'EXPIRED') return null;
  return (
    <div className="notice" role="status" aria-label="Voucher not restored">
      <p>The voucher had expired, so it was not restored. Your refund is not affected.</p>
    </div>
  );
}

/**
 * T151 (FR-040): the review step of an account entry. It shows bank, account number and holder name back and requires the
 * explicit "These details are correct" confirmation before the caller may submit; the caller sends accountDetailsConfirmed
 * only from this checkbox. The number is shown only here, from the entered values, and the caller clears them after sending.
 */
export function AccountReviewStep({ details, confirmed, onConfirmedChange, onEdit, pending, submitLabel, onSubmit }) {
  return (
    <form className="ota-form" aria-label="Review account details" onSubmit={event => { event.preventDefault(); if (confirmed) onSubmit(); }}>
      <p className="muted">Check the details below with the account owner before sending.</p>
      <dl className="ops-facts">
        <div><dt>Bank</dt><dd>{details.bankCode}</dd></div>
        <div><dt>Account number</dt><dd>{details.accountNumber}</dd></div>
        <div><dt>Account holder name</dt><dd>{details.holderName}</dd></div>
      </dl>
      <label className="settlement-addon-consent">
        <input type="checkbox" checked={confirmed} onChange={event => onConfirmedChange(event.target.checked)} />
        <span>These details are correct</span>
      </label>
      <div className="ops-actions">
        <button className="button outline" type="button" disabled={pending} onClick={onEdit}>Edit details</button>
        <button className="button primary" type="submit" disabled={pending || !confirmed}>{submitLabel}</button>
      </div>
    </form>
  );
}
