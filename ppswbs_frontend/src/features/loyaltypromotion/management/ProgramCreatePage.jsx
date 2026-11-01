import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useCommand } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { messages } from '../messages';
import { checkCreate, createBody, defaultWindow, generateVoucherCode, LIMITS, serverFieldErrors, toInstant } from '../rules';
import { DateTimeField } from './DateTimeField';
import { ProductTargetPicker } from './ProductTargetPicker';
import { PageTop } from './PromotionManagementLayout';

const MAX_CODE_DRAWS = 3;

const emptyForm = () => ({
  ...defaultWindow(), code: generateVoucherCode(),
  kind: 'VOUCHER', scope: 'PRODUCT', name: '', discountKind: 'PERCENT', percentage: '', amount: '',
  maxDiscountAmount: '', minimumEligibleAmount: '0', allowPoints: true, totalLimit: '', perMemberLimit: '1',
  productIds: '', variantIds: '', packageIds: '', branchIds: '', workshopDates: '', sessionIds: '',
});

/**
 * T030 create: fields follow kind and scope (an automatic discount is product-only and always allows points; only a
 * voucher has a code and limits). Product targets are picked from 002's product list (whole product or single sizes);
 * workshop targets are still entered as IDs because 003 offers no picker read yet. Errors keep every value; success
 * opens the created programme. The voucher code is generated (never typed); if the backend reports it as already
 * issued, a new code is drawn and the request sent again, at most MAX_CODE_DRAWS times. The window starts filled in
 * uses a 24-hour time, and each time is repeated in words under its field.
 */
export default function ProgramCreatePage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const navigate = useNavigate();
  const format = useFormat();
  const [form, setForm] = useState(emptyForm);
  const [hints, setHints] = useState({});
  const [picked, setPicked] = useState({ products: {}, variants: {} });
  const pick = next => {
    setPicked(next);
    setForm(current => ({ ...current, productIds: Object.keys(next.products).join(','), variantIds: Object.keys(next.variants).join(',') }));
  };
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
  const inWords = local => (toInstant(local) ? t('create.timeInWords', { at: format.dateTime(toInstant(local)) }) : null);

  const open = outcome => {
    if (outcome?.ok) navigate(`/promotion-management/programs/${encodeURIComponent(outcome.result.id)}`, { state: { notice: 'program.created' } });
  };
  const submit = async event => {
    event.preventDefault();
    const found = checkCreate(form);
    setHints(found);
    if (Object.keys(found).length) return;
    let current = form;
    for (let draw = 1; ; draw += 1) {
      const body = createBody(current);
      const outcome = await command.run(key => api.createProgram(key, body));
      if (outcome.ok || outcome.error?.code !== 'CODE_EXISTS' || current.kind !== 'VOUCHER' || draw >= MAX_CODE_DRAWS) { open(outcome); return; }
      current = { ...current, code: generateVoucherCode() };
      setForm(current);
    }
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
          {voucher && (
            <div className="code-field">
              <Field label={t('field.code')} error={errorOf('code')} hint={t('create.codeGenerated')}>
                <input value={form.code} readOnly className="mono" maxLength={LIMITS.codeMax} />
              </Field>
              <button type="button" className="text-button" onClick={() => setForm(current => ({ ...current, code: generateVoucherCode() }))}>{t('create.newCode')}</button>
            </div>
          )}
          <DateTimeField label={t('field.startAt')} error={errorOf('startAt')} hint={inWords(form.startAt)} value={form.startAt} onChange={value => setForm(current => ({ ...current, startAt: value }))} />
          <DateTimeField label={t('field.endAt')} error={errorOf('endAt')} hint={inWords(form.endAt)} value={form.endAt} onChange={value => setForm(current => ({ ...current, endAt: value }))} />
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
        <p className="muted small">{t(form.scope === 'PRODUCT' ? 'create.productTargetsHint' : 'create.targetsHint')}</p>
        {errorOf('targets') && <p className="field-error">{errorOf('targets')}</p>}
        {form.scope === 'PRODUCT' ? (
          <ProductTargetPicker api={api} value={picked} onChange={pick}
            errors={{ productIds: errorOf('targets.productIds'), variantIds: errorOf('targets.variantIds') }} />
        ) : (
          <div className="form-grid">
            <Field label={t('target.packageIds')} error={errorOf('targets.packageIds')}><input value={form.packageIds} onChange={set('packageIds')} /></Field>
            <Field label={t('target.branchIds')} error={errorOf('targets.branchIds')}><input value={form.branchIds} onChange={set('branchIds')} /></Field>
            <Field label={t('target.workshopDates')} error={errorOf('targets.workshopDates')} hint="YYYY-MM-DD"><input value={form.workshopDates} onChange={set('workshopDates')} /></Field>
            <Field label={t('target.sessionIds')} error={errorOf('targets.sessionIds')}><input value={form.sessionIds} onChange={set('sessionIds')} /></Field>
          </div>
        )}

        <ErrorNotice error={command.error} messages={messages} onRetry={async () => open(await command.retry())} />
        <div className="actions form-actions">
          <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('create.submit')}</button>
          <button type="button" className="button outline" onClick={() => navigate('/promotion-management/programs')}>{t('cancel')}</button>
        </div>
      </form>
    </>
  );
}
