import React, { useState } from 'react';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { messages } from '../messages';
import { isPercent, isPositive, serverFieldErrors } from '../rules';
import { PageTop } from './PromotionManagementLayout';

const FIELDS = ['earnUnitAmount', 'redeemPointValue', 'minRedeemPoints', 'maxRedeemPercent'];

/**
 * T034 point policy: current rates with version; a change applies only to transactions accepted afterwards. The
 * form keeps its values on error; on 409 the latest policy is shown so the Manager can confirm again. "Saved" is
 * shown only from the confirmed response.
 */
export default function PointPolicyPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useLoyaltyApi();
  const policy = useResource(() => api.getPolicy(), []);
  const [form, setForm] = useState(null);
  const [hints, setHints] = useState({});
  const [notice, setNotice] = useState(null);
  const command = useCommand();
  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));

  if (policy.loading && !policy.data) return <Loading />;
  if (!policy.data) return <ErrorNotice error={policy.error} messages={messages} onRetry={policy.reload} />;
  const data = policy.data;

  const startEdit = () => { setNotice(null); command.reset(); setForm(Object.fromEntries(FIELDS.map(key => [key, data[key]]))); };
  const save = async event => {
    event.preventDefault();
    const found = {};
    ['earnUnitAmount', 'redeemPointValue', 'minRedeemPoints'].forEach(key => { if (!isPositive(form[key].trim())) found[key] = 'validation.positive'; });
    if (!isPercent(form.maxRedeemPercent.trim())) found.maxRedeemPercent = 'validation.percent';
    setHints(found);
    if (Object.keys(found).length) return;
    const body = { expectedVersion: data.version, ...Object.fromEntries(FIELDS.map(key => [key, form[key].trim()])) };
    const outcome = await command.run(key => api.updatePolicy(key, body));
    finish(outcome);
  };
  const finish = outcome => {
    if (outcome?.ok) { policy.setData(outcome.result); setForm(null); setNotice('policy.saved'); }
  };

  return (
    <>
      <PageTop title={t('policy.title')} />
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      {policy.error && <ErrorNotice error={policy.error} messages={messages} onRetry={policy.reload} />}
      <section className="workspace-card">
        <div className="card-head">
          <h2>{t('policy.current')}</h2>
          {!form && <button type="button" className="button outline" onClick={startEdit}>{t('edit')}</button>}
        </div>
        <dl className="facts">
          <div><dt>{t('policy.earnUnitAmount')}</dt><dd>{t('policy.earnText', { amount: format.money(data.earnUnitAmount) })}</dd></div>
          <div><dt>{t('policy.redeemPointValue')}</dt><dd>{t('policy.redeemText', { amount: format.money(data.redeemPointValue) })}</dd></div>
          <div><dt>{t('policy.minRedeemPoints')}</dt><dd>{format.number(data.minRedeemPoints)}</dd></div>
          <div><dt>{t('policy.maxRedeemPercent')}</dt><dd>{data.maxRedeemPercent}%</dd></div>
          <div><dt>{t('policy.roundingMode')}</dt><dd>{data.roundingMode}</dd></div>
          <div><dt>{t('policy.version')}</dt><dd>{data.version} · {format.dateTime(data.createdAt)}</dd></div>
        </dl>
        <p className="muted small">{t('policy.futureOnly')}</p>
      </section>

      {form && (
        <form className="workspace-card" onSubmit={save} noValidate>
          <h2>{t('policy.edit')}</h2>
          <div className="form-grid">
            {FIELDS.map(key => (
              <Field key={key} label={t(`policy.${key}`)} error={errorOf(key)}>
                <input inputMode={key === 'maxRedeemPercent' ? 'decimal' : 'numeric'} value={form[key]} onChange={event => setForm(current => ({ ...current, [key]: event.target.value }))} />
              </Field>
            ))}
          </div>
          <p className="muted small">{t('policy.futureOnly')}</p>
          <ErrorNotice error={command.error} messages={messages} onReload={policy.reload} onRetry={async () => finish(await command.retry())} />
          <div className="actions form-actions">
            <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('save')}</button>
            <button type="button" className="button outline" onClick={() => setForm(null)} disabled={command.pending}>{t('cancel')}</button>
          </div>
        </form>
      )}
    </>
  );
}
