import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useCommand } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { messages } from '../messages';
import { useDateTime } from '../display';
import { checkCreate, createBody, defaultWindow, errorFieldsOf, generateVoucherCode, LIMITS, productBranchIds, productTargets, toInstant } from '../../domain/promotionRules';
import { useFieldErrors } from '../../application/useFieldErrors';
import { DateTimeField } from './DateTimeField';
import { ProductBranchPicker } from './ProductBranchPicker';
import { ProductTargetPicker } from './ProductTargetPicker';
import { WorkshopTargetPicker } from './WorkshopTargetPicker';
import { PageTop } from './PromotionManagementLayout';

const MAX_CODE_DRAWS = 3;

/**
 * FR-007 / US1.8: the key and body of a create whose answer is unknown stay in sessionStorage until the outcome is
 * known. 006 has no receipt read, so after a reload the Manager can send that same request again: the backend replays
 * a committed create for the same key instead of creating a second programme (an automatic discount has no code to
 * catch a duplicate).
 */
const PENDING = 'lp.pendingCreate';
const pending = {
  read: () => { try { return JSON.parse(sessionStorage.getItem(PENDING) || 'null'); } catch { return null; } },
  keep: value => { try { sessionStorage.setItem(PENDING, JSON.stringify(value)); } catch { /* no storage: Retry still works */ } },
  forget: () => { try { sessionStorage.removeItem(PENDING); } catch { /* nothing kept */ } },
};

const emptyForm = () => ({
  ...defaultWindow(), code: generateVoucherCode(),
  kind: 'VOUCHER', scope: 'PRODUCT', name: '', discountKind: 'PERCENT', percentage: '', amount: '',
  maxDiscountAmount: '', minimumEligibleAmount: '0', allowPoints: true, totalLimit: '', perMemberLimit: '1',
  productIds: '', variantIds: '', packageIds: '', branchIds: '', sessionIds: '',
  // Session 2026-11-09: branches of a product voucher ('ALL' = every branch, nothing sent).
  branchMode: 'ALL', productBranchIds: [],
});

/**
 * T030 create: fields follow kind and scope (an automatic discount always allows points and, for workshops, is
 * targeted by packages only — Session 2026-11-14, FR-044; only a voucher has a code and limits). Product targets are picked from 002's product list (whole product or single sizes);
 * workshop targets are still entered as IDs because 003 offers no picker read yet. Errors keep every value; success
 * opens the created programme. The voucher code is generated (never typed); if the backend reports it as already
 * issued, a new code is drawn and the request sent again, at most MAX_CODE_DRAWS times. The window starts filled in
 * uses a 24-hour time, and each time is repeated in words under its field.
 */
export default function ProgramCreatePage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const navigate = useNavigate();
  const dateTime = useDateTime();
  const [form, setForm] = useState(emptyForm);
  const [picked, setPicked] = useState({ products: {}, variants: {}, sizesOf: {} });
  const pick = next => {
    setPicked(next);
    const targets = productTargets(next);
    setForm(current => ({ ...current, productIds: targets.productIds.join(','), variantIds: targets.variantIds.join(',') }));
    clear(...errorFieldsOf('productIds'), ...errorFieldsOf('variantIds'));
  };
  const command = useCommand();
  const [kept, setKept] = useState(() => pending.read());
  const recovery = useCommand();
  const { errorOf, setHints, clear } = useFieldErrors(command.error, t);
  const set = name => event => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    clear(...errorFieldsOf(name));
    setForm(current => {
      const next = { ...current, [name]: value };
      // Kind or scope changed: the workshop package choice is single for a voucher and multiple for an automatic
      // discount (FR-044, packages only), so the picked workshop targets start again.
      if ((name === 'kind' || name === 'scope') && value !== current[name]) {
        next.packageIds = ''; next.branchIds = ''; next.sessionIds = '';
      }
      return next;
    });
  };
  const setField = (name, value) => { clear(...errorFieldsOf(name)); setForm(current => ({ ...current, [name]: value })); };
  const voucher = form.kind === 'VOUCHER';
  const inWords = local => (toInstant(local) ? t('create.timeInWords', { at: dateTime(toInstant(local)) }) : null);

  // A known outcome (created, or refused for good) ends the pending create; a lost answer or 503 keeps it.
  const open = outcome => {
    if (!outcome) return;
    if (outcome.ok) {
      pending.forget();
      navigate(`/promotion-management/programs/${encodeURIComponent(outcome.result.id)}`, { state: { notice: 'program.created' } });
    } else if (!outcome.error.retryable) pending.forget();
  };
  const send = body => key => { pending.keep({ key, body }); return api.createProgram(key, body); };
  const resend = async () => {
    const outcome = await recovery.run(() => api.createProgram(kept.key, kept.body));
    if (outcome.ok || !outcome.error.retryable) setKept(null);
    open(outcome);
  };
  const discard = () => { pending.forget(); setKept(null); recovery.reset(); };
  const submit = async event => {
    event.preventDefault();
    const found = checkCreate(form);
    setHints(found);
    if (Object.keys(found).length) return;
    let current = form;
    for (let draw = 1; ; draw += 1) {
      const body = createBody(current);
      const outcome = await command.run(send(body));
      if (outcome.ok || outcome.error?.code !== 'CODE_EXISTS' || current.kind !== 'VOUCHER' || draw >= MAX_CODE_DRAWS) { open(outcome); return; }
      current = { ...current, code: generateVoucherCode() };
      setForm(current);
    }
  };

  return (
    <>
      <PageTop title={t('create.title')} />
      {kept?.key && (
        <div className="notice error" role="status">
          <p>{t('create.pending', { name: kept.body?.name || '' })}</p>
          <div className="actions">
            <button type="button" className="button primary small" disabled={recovery.pending} onClick={resend}>{t('create.pendingCheck')}</button>
            <button type="button" className="button outline small" disabled={recovery.pending} onClick={discard}>{t('create.pendingDiscard')}</button>
            <Link className="text-link" to="/promotion-management/programs">{t('create.pendingList')}</Link>
          </div>
          <ErrorNotice error={recovery.error} messages={messages} onRetry={resend} />
        </div>
      )}
      <form className="workspace-card" onSubmit={submit} noValidate>
        <div className="form-grid">
          <Field label={t('field.kind')} error={errorOf('kind')}>
            <select value={form.kind} onChange={set('kind')}>
              <option value="VOUCHER">{t('kind.VOUCHER')}</option>
              <option value="AUTO_DISCOUNT">{t('kind.AUTO_DISCOUNT')}</option>
            </select>
          </Field>
          <Field label={t('field.scope')} error={errorOf('scope')} hint={voucher || form.scope !== 'WORKSHOP' ? null : t('create.autoWorkshopPackagesOnly')}>
            <select value={form.scope} onChange={set('scope')}>
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
          <DateTimeField label={t('field.startAt')} error={errorOf('startAt')} hint={inWords(form.startAt)} value={form.startAt} onChange={value => setField('startAt', value)} />
          <DateTimeField label={t('field.endAt')} error={errorOf('endAt')} hint={inWords(form.endAt)} value={form.endAt} onChange={value => setField('endAt', value)} />
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
        <p className="muted small">{t(form.scope === 'PRODUCT' ? 'create.productTargetsHint' : voucher ? 'create.workshopTargetsHint' : 'create.workshopAutoTargetsHint')}</p>
        {errorOf('targets') && <p className="field-error">{errorOf('targets')}</p>}
        {form.scope === 'PRODUCT' ? (
          <>
            <h3 className="picker-title">{t('branch.title')}</h3>
            <ProductBranchPicker api={api} voucher={voucher} mode={form.branchMode} value={form.productBranchIds}
              onChange={(mode, ids) => { clear(...errorFieldsOf('productBranchIds')); setForm(current => ({ ...current, branchMode: mode, productBranchIds: ids })); }}
              error={errorOf('targets.branchIds')} />
            <ProductTargetPicker api={api} value={picked} onChange={pick} branchIds={productBranchIds(form)}
              errors={{ productIds: errorOf('targets.productIds'), variantIds: errorOf('targets.variantIds') }} />
          </>
        ) : (
          <WorkshopTargetPicker api={api} form={form} setField={setField} packagesOnly={!voucher}
            errors={{ packageIds: errorOf('targets.packageIds'), branchIds: errorOf('targets.branchIds'), sessionIds: errorOf('targets.sessionIds') }} />
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
