import React, { useState } from 'react';
import { useLocation } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { Field, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { CustomerPage } from '../OpsPage';
import { ProblemNotice } from '../ui';

/**
 * Customer refund-account page (FR-040). The single-use link from email 7 is /refund-account#TOKEN: the token stays in the
 * URL fragment, is sent only in the request path, and is never stored or logged. The page asks for the payer's own account;
 * the holder name is not compared with the payer, and the account number is never shown again after it is sent.
 */
export default function RefundAccountPage() {
  const api = createSettlementApi(useHttp());
  const token = decodeURIComponent(useLocation().hash.replace(/^#/, ''));
  const [form, setForm] = useState({ bankCode: '', accountNumber: '', holderName: '' });
  const [done, setDone] = useState(false);
  const command = useCommand();
  const ready = form.bankCode.trim() && form.accountNumber.trim() && form.holderName.trim();

  const submit = async event => {
    event.preventDefault();
    const out = await command.run(key => api.submitRefundAccount(token, {
      bankCode: form.bankCode.trim(), accountNumber: form.accountNumber.trim(), holderName: form.holderName.trim(),
    }, key));
    if (out.ok) finish();
  };
  const finish = () => { setForm({ bankCode: '', accountNumber: '', holderName: '' }); setDone(true); };
  // A retry resends the same Idempotency-Key (FR-022a), so a lost response replays instead of writing twice.
  const retry = async () => { const out = await command.retry(); if (out?.ok) finish(); };

  if (!token) {
    return <CustomerPage title="Refund account"><div className="notice error" role="alert"><p>This link is incomplete. Open the link from your refund email again.</p></div></CustomerPage>;
  }
  if (done) {
    return <CustomerPage title="Refund account"><div className="notice success" role="status"><p>Thank you. Your bank account was received. We will transfer the refund after it is approved and email you when it is done.</p></div></CustomerPage>;
  }
  const error = command.error;
  return (
    <CustomerPage title="Where should we send your refund?" intro="Enter your own bank account. This link works once and expires after 7 days.">
      {error?.status === 410 ? (
        <div className="notice error" role="alert"><p>This link was already used, replaced or has expired. Ask the shop to send you a new link.</p></div>
      ) : error?.status === 404 ? (
        <div className="notice error" role="alert"><p>This link is not valid. Open the link from your refund email again.</p></div>
      ) : <ProblemNotice error={error} onRetry={retry} />}
      <form className="ota-form" aria-label="Refund account" onSubmit={submit}>
        <Field label="Bank"><input value={form.bankCode} maxLength={32} autoComplete="off" onChange={event => setForm(v => ({ ...v, bankCode: event.target.value }))} /></Field>
        <Field label="Account number"><input inputMode="numeric" value={form.accountNumber} maxLength={32} autoComplete="off" onChange={event => setForm(v => ({ ...v, accountNumber: event.target.value }))} /></Field>
        <Field label="Account holder name" hint="Exactly as your bank shows it."><input value={form.holderName} maxLength={128} autoComplete="off" onChange={event => setForm(v => ({ ...v, holderName: event.target.value }))} /></Field>
        <button className="button primary" type="submit" disabled={command.pending || !ready}>Send account</button>
      </form>
    </CustomerPage>
  );
}
