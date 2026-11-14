import React, { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { DiscountText, ScopeBadge, Terms, useDateTime } from '../display';
import { sharedMessages } from '../../../../shared/ui/messages';
import { messages } from '../messages';
import { checkDiscount, discountBody, effectiveFixedAmount, errorFieldsOf, isAmount, isPositive, LIMITS, toInstant, toLocalInput } from '../../domain/promotionRules';
import { useFieldErrors } from '../../application/useFieldErrors';
import { PageTop } from './PromotionManagementLayout';
import { useTargetLabels } from '../../application/useTargetLabels';
import { DateTimeField } from './DateTimeField';

/**
 * T030 detail and T047 edit / pause / resume with usage. Kind, scope, code and targets never change. Each command
 * sends the version shown; on 409 the latest programme is loaded while the typed edit stays. A notice appears only
 * after the backend confirmed the command. Pausing keeps history and quota.
 */
export default function ProgramDetailPage() {
  const t = useT(messages);
  const format = useFormat();
  const dateTime = useDateTime();
  const api = useLoyaltyApi();
  const { programId } = useParams();
  const location = useLocation();
  const program = useResource(() => api.getProgram(programId), [programId]);
  const usage = useResource(() => api.getUsage(programId), [programId]);
  const [notice, setNotice] = useState(location.state?.notice || null);
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const toggle = useCommand();
  const labels = useTargetLabels(api, program.data?.targets || null);

  const reloadAll = () => { program.reload(); usage.reload(); };
  if (program.loading && !program.data) return <Loading />;
  if (!program.data) {
    return (
      <div className="workspace-card">
        <ErrorNotice error={program.error} messages={messages} onRetry={program.reload} />
        <Link className="button outline" to="/promotion-management/programs">{t('back')}</Link>
      </div>
    );
  }
  const data = program.data;
  const paused = data.status === 'PAUSED';

  const runToggle = async send => {
    setNotice(null);
    const outcome = await send();
    if (outcome?.ok) { setConfirm(false); setNotice(paused ? 'program.resumed' : 'program.paused'); program.setData(outcome.result); usage.reload(); }
    else if (outcome && !outcome.error.retryable) setConfirm(false);
  };
  const doToggle = () => runToggle(() => toggle.run(key => (paused ? api.resumeProgram(key, data.id, data.version) : api.pauseProgram(key, data.id, data.version))));

  return (
    <>
      <PageTop eyebrow={t('manage.eyebrow')} title={data.name}><Link className="button outline" to="/promotion-management/programs">{t('back')}</Link></PageTop>
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      {program.error && <ErrorNotice error={program.error} messages={messages} onRetry={program.reload} />}

      <section className="workspace-card">
        <div className="card-head">
          <div>
            <h2>{t(`kind.${data.kind}`)}</h2>
            {data.code && <p className="mono">{t('field.code')}: {data.code}</p>}
          </div>
          <div className="chips"><ScopeBadge scope={data.scope} /><span className={paused ? 'status' : 'status ok'}>{t(`status.${data.status}`)}</span></div>
        </div>
        <p className="discount-line">{t('field.discount')}: <DiscountText discount={data.discount} /></p>
        <Terms item={data} labels={labels} />
        <TargetWarnings program={data} labels={labels} onRetry={program.reload} />
        <p className="muted small detail-meta">{t('detail.meta', { version: data.version, revision: data.revision, createdAt: dateTime(data.createdAt) })}</p>
        <div className="actions form-actions">
          {!editing && <button type="button" className="button outline" onClick={() => { setNotice(null); setEditing(true); }}>{t('edit')}</button>}
          <button type="button" className={paused ? 'button primary' : 'button outline'} onClick={() => { setNotice(null); toggle.reset(); setConfirm(true); }}>{paused ? t('program.resume') : t('program.pause')}</button>
        </div>
        {!confirm && <ErrorNotice error={toggle.error} messages={messages} onReload={reloadAll} />}
      </section>

      {editing && <EditForm program={data} api={api} onReload={reloadAll} onDone={updated => { setEditing(false); setNotice('program.updated'); program.setData(updated); usage.reload(); }} onCancel={() => setEditing(false)} />}

      <section className="workspace-card">
        <h2>{t('usage.title')}</h2>
        {usage.loading && !usage.data && <Loading />}
        {usage.error && <ErrorNotice error={usage.error} messages={messages} onRetry={usage.reload} />}
        {usage.data && (
          <dl className="facts">
            {'heldCount' in usage.data ? (
              <>
                <div><dt>{t('usage.consumed')}</dt><dd>{format.number(usage.data.consumedCount)} / {format.number(usage.data.totalLimit)}</dd></div>
                <div><dt>{t('usage.held')}</dt><dd>{format.number(usage.data.heldCount)}</dd></div>
                <div><dt>{t('field.perMemberLimit')}</dt><dd>{format.number(usage.data.perMemberLimit)}</dd></div>
              </>
            ) : (
              <div><dt>{t('usage.accepted')}</dt><dd>{format.number(usage.data.acceptedCount)}</dd></div>
            )}
            <div><dt>{t('usage.discount')}</dt><dd>{format.money(usage.data.committedDiscountAmount)}</dd></div>
          </dl>
        )}
      </section>

      <ConfirmDialog open={confirm} title={paused ? t('program.resume') : t('program.pause')} confirmLabel={paused ? t('program.resume') : t('program.pause')}
        pending={toggle.pending} onConfirm={doToggle} onCancel={() => setConfirm(false)}>
        <p>{paused ? t('program.resumeText') : t('program.pauseText')}</p>
        <ErrorNotice error={toggle.error} messages={messages} onRetry={() => runToggle(() => toggle.retry())} />
      </ConfirmDialog>
    </>
  );
}

/**
 * Products or sizes of the scope that can no longer be bought (spec clarification 2026-10-26), from targetCheck /
 * targetWarnings of getPromotionProgram. Nothing is changed: the Manager decides whether to pause.
 */
function TargetWarnings({ program, labels, onRetry }) {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  if (program.targetCheck === 'UNAVAILABLE') {
    return (
      <div className="notice error" role="status">
        <p>{t('warn.unavailable')}</p>
        <button type="button" className="button outline small" onClick={onRetry}>{tShared('retry')}</button>
      </div>
    );
  }
  const warnings = program.targetWarnings || [];
  if (warnings.length === 0) return null;
  const labelGroup = { VARIANT: 'variantIds', PRODUCT: 'productIds', BRANCH: 'branchIds' };
  const nameOf = ({ dimension, targetKey }) => labels?.[labelGroup[dimension] || 'productIds']?.[targetKey]
    || t(`warn.${dimension}`, { id: targetKey });
  return (
    <div className="notice error" role="status">
      <p><strong>{t('warn.title')}</strong></p>
      <ul>
        {warnings.map(warning => <li key={`${warning.dimension}-${warning.targetKey}`}>{nameOf(warning)} — {t(`warn.${warning.state}`)}</li>)}
      </ul>
      <p className="small">{t('warn.hint')}</p>
    </div>
  );
}

function initialForm(program) {
  return {
    name: program.name,
    startAt: toLocalInput(program.startAt),
    endAt: toLocalInput(program.endAt),
    discountKind: program.discount?.kind || 'PERCENT',
    percentage: program.discount?.percentage || '',
    // An older FIXED revision may hold a cap below its amount: show what it actually takes off (Session 2026-11-14).
    amount: program.discount?.kind === 'FIXED' ? effectiveFixedAmount(program.discount).toString() : program.discount?.amount || '',
    maxDiscountAmount: program.discount?.maxDiscountAmount || '',
    minimumEligibleAmount: program.minimumEligibleAmount || '0',
    allowPoints: program.allowPoints,
    totalLimit: program.totalLimit || '',
    perMemberLimit: program.perMemberLimit || '',
  };
}

/** Only changed fields are sent (PATCH); kind, scope, code and targets are not editable. */
function EditForm({ program, api, onDone, onCancel, onReload }) {
  const t = useT(messages);
  const dateTime = useDateTime();
  const inWords = local => (toInstant(local) ? t('create.timeInWords', { at: dateTime(toInstant(local)) }) : null);
  const [form, setForm] = useState(() => initialForm(program));
  const command = useCommand();
  const voucher = program.kind === 'VOUCHER';
  const { errorOf, setHints, clear } = useFieldErrors(command.error, t);
  const setValue = (name, value) => { clear(...errorFieldsOf(name)); setForm(current => ({ ...current, [name]: value })); };
  const set = name => event => setValue(name, event.target.type === 'checkbox' ? event.target.checked : event.target.value);

  const save = async event => {
    event.preventDefault();
    const before = initialForm(program);
    const found = {};
    const body = { expectedVersion: program.version };
    if (form.name.trim() !== before.name) {
      if (!form.name.trim()) found.name = 'validation.required';
      else if ([...form.name.trim()].length > LIMITS.nameMax) found.name = 'validation.nameTooLong';
      body.name = form.name.trim();
    }
    if (form.startAt !== before.startAt) body.startAt = toInstant(form.startAt);
    if (form.endAt !== before.endAt) body.endAt = toInstant(form.endAt);
    if (!form.startAt || !form.endAt || new Date(form.startAt) >= new Date(form.endAt)) found.endAt = 'validation.endAfterStart';
    const discountChanged = ['discountKind', 'percentage', 'amount', 'maxDiscountAmount'].some(key => form[key] !== before[key]);
    if (discountChanged) {
      checkDiscount(form, found);
      body.discount = discountBody(form);
    }
    if (form.minimumEligibleAmount !== before.minimumEligibleAmount) {
      if (!isAmount(form.minimumEligibleAmount.trim())) found.minimumEligibleAmount = 'validation.amount';
      body.minimumEligibleAmount = form.minimumEligibleAmount.trim();
    }
    if (voucher && form.allowPoints !== before.allowPoints) body.allowPoints = form.allowPoints;
    if (voucher && form.totalLimit !== before.totalLimit) {
      if (!isPositive(form.totalLimit.trim())) found.totalLimit = 'validation.positive';
      body.totalLimit = form.totalLimit.trim();
    }
    if (voucher && form.perMemberLimit !== before.perMemberLimit) {
      if (!isPositive(form.perMemberLimit.trim())) found.perMemberLimit = 'validation.positive';
      body.perMemberLimit = form.perMemberLimit.trim();
    }
    if (Object.keys(body).length === 1) found.body = 'validation.nothingChanged';
    setHints(found);
    if (Object.keys(found).length) return;
    const outcome = await command.run(key => api.updateProgram(key, program.id, body));
    if (outcome.ok) onDone(outcome.result);
  };

  return (
    <form className="workspace-card" onSubmit={save} noValidate>
      <h2>{t('edit.title')}</h2>
      <p className="muted small">{t('edit.immutable')}</p>
      {errorOf('body') && <p className="field-error">{errorOf('body')}</p>}
      <div className="form-grid">
        <Field label={t('field.name')} error={errorOf('name')}><input value={form.name} onChange={set('name')} maxLength={LIMITS.nameMax} /></Field>
        <div />
        <DateTimeField label={t('field.startAt')} error={errorOf('startAt')} hint={inWords(form.startAt)} value={form.startAt} onChange={value => setValue('startAt', value)} />
        <DateTimeField label={t('field.endAt')} error={errorOf('endAt')} hint={inWords(form.endAt)} value={form.endAt} onChange={value => setValue('endAt', value)} />
        <Field label={t('field.discountKind')} error={errorOf('discount.kind')}>
          <select value={form.discountKind} onChange={set('discountKind')}>
            <option value="PERCENT">{t('discount.PERCENT')}</option>
            <option value="FIXED">{t('discount.FIXED')}</option>
          </select>
        </Field>
        {form.discountKind === 'PERCENT'
          ? <Field label={t('field.percentage')} error={errorOf('discount.percentage')}><input inputMode="decimal" value={form.percentage} onChange={set('percentage')} /></Field>
          : <Field label={t('field.amount')} error={errorOf('discount.amount')}><input inputMode="numeric" value={form.amount} onChange={set('amount')} /></Field>}
        {form.discountKind === 'PERCENT' && <Field label={t('field.maxDiscountAmount')} error={errorOf('discount.maxDiscountAmount')}><input inputMode="numeric" value={form.maxDiscountAmount} onChange={set('maxDiscountAmount')} /></Field>}
        <Field label={t('field.minimumEligibleAmount')} error={errorOf('minimumEligibleAmount')}><input inputMode="numeric" value={form.minimumEligibleAmount} onChange={set('minimumEligibleAmount')} /></Field>
        {voucher && <Field label={t('field.totalLimit')} error={errorOf('totalLimit')} hint={t('edit.limitHint')}><input inputMode="numeric" value={form.totalLimit} onChange={set('totalLimit')} /></Field>}
        {voucher && <Field label={t('field.perMemberLimit')} error={errorOf('perMemberLimit')}><input inputMode="numeric" value={form.perMemberLimit} onChange={set('perMemberLimit')} /></Field>}
      </div>
      {voucher && <label className="check-row"><input type="checkbox" checked={form.allowPoints} onChange={set('allowPoints')} /> {t('field.allowPointsLong')}</label>}
      <ErrorNotice error={command.error} messages={messages} onReload={onReload}
        onRetry={async () => { const outcome = await command.retry(); if (outcome?.ok) onDone(outcome.result); }} />
      <div className="actions form-actions">
        <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('save')}</button>
        <button type="button" className="button outline" onClick={onCancel} disabled={command.pending}>{t('cancel')}</button>
      </div>
    </form>
  );
}
