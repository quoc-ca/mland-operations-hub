import React, { useState } from 'react';
import { useLocation } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { Field, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { CustomerPage } from '../OpsPage';
import { useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { AccountReviewStep, ProblemNotice } from '../ui';

/**
 * Customer refund-account page (FR-040). The single-use link from email 7 is /refund-account#TOKEN: the token stays in the
 * URL fragment, is sent only in the request path, and is never stored or logged. The page asks for the payer's own account;
 * the holder name is not compared with the payer, and the account number is never shown again after it is sent.
 */
export default function RefundAccountPage() {
  const t = useT(messages);
  const api = createSettlementApi(useHttp());
  const token = decodeURIComponent(useLocation().hash.replace(/^#/, ''));
  const [form, setForm] = useState({ bankCode: '', accountNumber: '', holderName: '' });
  const [done, setDone] = useState(false);
  // T151: the form is reviewed before it is sent; a refused attempt (422) keeps the user on the review step with the values.
  const [reviewing, setReviewing] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const command = useCommand();
  const ready = form.bankCode.trim() && form.accountNumber.trim() && form.holderName.trim();

  const review = event => { event.preventDefault(); setConfirmed(false); setReviewing(true); };
  const edit = () => { setReviewing(false); setConfirmed(false); command.reset(); };
  const submit = async () => {
    const out = await command.run(key => api.submitRefundAccount(token, {
      bankCode: form.bankCode.trim(), accountNumber: form.accountNumber.trim(), holderName: form.holderName.trim(),
      accountDetailsConfirmed: confirmed,
    }, key));
    if (out.ok) finish();
  };
  const finish = () => { setForm({ bankCode: '', accountNumber: '', holderName: '' }); setReviewing(false); setConfirmed(false); setDone(true); };
  // A retry resends the same Idempotency-Key (FR-022a), so a lost response replays instead of writing twice.
  const retry = async () => { const out = await command.retry(); if (out?.ok) finish(); };

  if (!token) {
    return <CustomerPage title={t('ra.title')}><div className="notice error" role="alert"><p>{t('ra.incomplete')}</p></div></CustomerPage>;
  }
  if (done) {
    return <CustomerPage title={t('ra.title')}><div className="notice success" role="status"><p>{t('ra.thanks')}</p></div></CustomerPage>;
  }
  const error = command.error;
  return (
    <CustomerPage title={t('ra.titleAsk')} intro={t('ra.intro')}>
      {error?.status === 410 ? (
        <div className="notice error" role="alert"><p>{t('ra.used')}</p></div>
      ) : error?.status === 404 ? (
        <div className="notice error" role="alert"><p>{t('ra.invalid')}</p></div>
      ) : <ProblemNotice error={error} onRetry={retry} />}
      {reviewing ? (
        <AccountReviewStep details={form} confirmed={confirmed} onConfirmedChange={setConfirmed} onEdit={edit} pending={command.pending} submitLabel={t('ra.sendAccount')} onSubmit={submit} />
      ) : (
        <form className="ota-form" aria-label={t('ra.formAria')} onSubmit={review}>
          <Field label={t('ra.bank')}><input value={form.bankCode} maxLength={32} autoComplete="off" onChange={event => setForm(v => ({ ...v, bankCode: event.target.value }))} /></Field>
          <Field label={t('ra.accountNumber')}><input inputMode="numeric" value={form.accountNumber} maxLength={32} autoComplete="off" onChange={event => setForm(v => ({ ...v, accountNumber: event.target.value }))} /></Field>
          <Field label={t('ra.holder')} hint={t('ra.holderHint')}><input value={form.holderName} maxLength={128} autoComplete="off" onChange={event => setForm(v => ({ ...v, holderName: event.target.value }))} /></Field>
          <button className="button primary" type="submit" disabled={command.pending || !ready}>{t('ra.review')}</button>
        </form>
      )}
    </CustomerPage>
  );
}
