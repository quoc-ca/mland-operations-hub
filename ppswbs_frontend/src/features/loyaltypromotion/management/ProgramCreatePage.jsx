import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useCommand } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { messages } from '../messages';
import { checkCreate, createBody, LIMITS, serverFieldErrors } from '../rules';
import { PageTop } from './PromotionManagementLayout';

const EMPTY = {
  kind: 'VOUCHER', scope: 'PRODUCT', name: '', startAt: '', endAt: '', discountKind: 'PERCENT', percentage: '', amount: '',
  maxDiscountAmount: '', minimumEligibleAmount: '0', allowPoints: true, code: '', totalLimit: '', perMemberLimit: '1',
  productIds: '', variantIds: '', packageIds: '', branchIds: '', workshopDates: '', sessionIds: '',
};

/**
 * T030 create: fields follow kind and scope (an automatic discount is product-only and always allows points; only a
 * voucher has a code and limits). Targets are entered as IDs because no target picker contract exists yet. Errors
 * keep every value; success opens the created programme.
 */
export default function ProgramCreatePage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [hints, setHints] = useState({});
  const command = useCommand();
  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm(current => {
      const next = { ...current, [name]: value };
      if (name === 'kind' && value === 'AUTO_DISCOUNT') next.scope = 'PRODUCT';
      return next;
    });
  };
  const voucher = form.kind === 'VOUCHER';

  const open = outcome => {
    if (outcome?.ok) navigate(`/promotion-management/programs/${encodeURIComponent(outcome.result.id)}`, { state: { notice: 'program.created' } });
  };
  const submit = async event => {
    event.preventDefault();
    const found = checkCreate(form);
    setHints(found);
    if (Object.keys(found).length) return;
    const body = createBody(form);
    open(await command.run(key => api.createProgram(key, body)));
  };

  return (
    <>
      <PageTop title={t('create.title')} />
      <form className="workspace-card" onSubmit={submit} noValidate>
        <div className="form-grid">
          <Field label={t('field.kind')} error={errorOf('kind')}>
            <select value={form.kind} onChange={set('kind')}>
              <option value="VOUCHER">{t('kind.VOUCHER')}</option>
              <option value="AUTO_DISCOUNT">{t('kind.AUTO_DISCOUNT')}</option>
            </select>
          </Field>
          <Field label={t('field.scope')} error={errorOf('scope')} hint={voucher ? null : t('create.autoProductOnly')}>
            <select value={form.scope} onChange={set('scope')} disabled={!voucher}>
              <option value="PRODUCT">{t('scope.PRODUCT')}</option>
              <option value="WORKSHOP">{t('scope.WORKSHOP')}</option>
            </select>
          </Field>
          <Field label={t('field.name')} error={errorOf('name')}><input value={form.name} onChange={set('name')} maxLength={LIMITS.nameMax} /></Field>
          {voucher && <Field label={t('field.code')} error={errorOf('code')} hint={t('create.codeHint')}><input value={form.code} onChange={set('code')} maxLength={LIMITS.codeMax} /></Field>}
          <Field label={t('field.startAt')} error={errorOf('startAt')}><input type="datetime-local" value={form.startAt} onChange={set('startAt')} /></Field>
          <Field label={t('field.endAt')} error={errorOf('endAt')}><input type="datetime-local" value={form.endAt} onChange={set('endAt')} /></Field>
        </div>

        <h2 className="section-title">{t('field.discount')}</h2>
        <div className="form-grid">
          <Field label={t('field.discountKind')} error={errorOf('discount.kind')}>
            <select value={form.discountKind} onChange={set('discountKind')}>
              <option value="PERCENT">{t('discount.PERCENT')}</option>
              <option value="FIXED">{t('discount.FIXED')}</option>
            </select>
          </Field>
          {form.discountKind === 'PERCENT'
            ? <Field label={t('field.percentage')} error={errorOf('discount.percentage')}><input inputMode="decimal" value={form.percentage} onChange={set('percentage')} /></Field>
            : <Field label={t('field.amount')} error={errorOf('discount.amount')}><input inputMode="numeric" value={form.amount} onChange={set('amount')} /></Field>}
          <Field label={t('field.maxDiscountAmount')} error={errorOf('discount.maxDiscountAmount')}><input inputMode="numeric" value={form.maxDiscountAmount} onChange={set('maxDiscountAmount')} /></Field>
          <Field label={t('field.minimumEligibleAmount')} error={errorOf('minimumEligibleAmount')}><input inputMode="numeric" value={form.minimumEligibleAmount} onChange={set('minimumEligibleAmount')} /></Field>
        </div>
        {voucher
          ? <label className="check-row"><input type="checkbox" checked={form.allowPoints} onChange={set('allowPoints')} /> {t('field.allowPointsLong')}</label>
          : <p className="muted small">{t('create.autoAllowsPoints')}</p>}

        {voucher && (
          <>
            <h2 className="section-title">{t('create.limits')}</h2>
            <div className="form-grid">
              <Field label={t('field.totalLimit')} error={errorOf('totalLimit')}><input inputMode="numeric" value={form.totalLimit} onChange={set('totalLimit')} /></Field>
              <Field label={t('field.perMemberLimit')} error={errorOf('perMemberLimit')}><input inputMode="numeric" value={form.perMemberLimit} onChange={set('perMemberLimit')} /></Field>
            </div>
          </>
        )}

        <h2 className="section-title">{t('create.targets')}</h2>
        <p className="muted small">{t('create.targetsHint')}</p>
        {errorOf('targets') && <p className="field-error">{errorOf('targets')}</p>}
        <div className="form-grid">
          {form.scope === 'PRODUCT' ? (
            <>
              <Field label={t('target.productIds')} error={errorOf('targets.productIds')}><input value={form.productIds} onChange={set('productIds')} placeholder="12, 15" /></Field>
              <Field label={t('target.variantIds')} error={errorOf('targets.variantIds')}><input value={form.variantIds} onChange={set('variantIds')} placeholder="101, 102" /></Field>
            </>
          ) : (
            <>
              <Field label={t('target.packageIds')} error={errorOf('targets.packageIds')}><input value={form.packageIds} onChange={set('packageIds')} /></Field>
              <Field label={t('target.branchIds')} error={errorOf('targets.branchIds')}><input value={form.branchIds} onChange={set('branchIds')} /></Field>
              <Field label={t('target.workshopDates')} error={errorOf('targets.workshopDates')} hint="YYYY-MM-DD"><input value={form.workshopDates} onChange={set('workshopDates')} /></Field>
              <Field label={t('target.sessionIds')} error={errorOf('targets.sessionIds')}><input value={form.sessionIds} onChange={set('sessionIds')} /></Field>
            </>
          )}
        </div>

        <ErrorNotice error={command.error} messages={messages} onRetry={async () => open(await command.retry())} />
        <div className="actions form-actions">
          <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('create.submit')}</button>
          <button type="button" className="button outline" onClick={() => navigate('/promotion-management/programs')}>{t('cancel')}</button>
        </div>
      </form>
    </>
  );
}
