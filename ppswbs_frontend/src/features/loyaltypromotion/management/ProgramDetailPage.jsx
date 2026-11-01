import React, { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { DiscountText, ScopeBadge, Terms } from '../display';
import { messages } from '../messages';
import { isAmount, isPercent, isPositive, LIMITS, serverFieldErrors, toInstant, toLocalInput } from '../rules';
import { PageTop } from './PromotionManagementLayout';

/**
 * T030 detail and T047 edit / pause / resume with usage. Kind, scope, code and targets never change. Each command
 * sends the version shown; on 409 the latest programme is loaded while the typed edit stays. A notice appears only
 * after the backend confirmed the command. Pausing keeps history and quota.
 */
export default function ProgramDetailPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useLoyaltyApi();
  const { programId } = useParams();
  const location = useLocation();
  const program = useResource(() => api.getProgram(programId), [programId]);
  const usage = useResource(() => api.getUsage(programId), [programId]);
  const [notice, setNotice] = useState(location.state?.notice || null);
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const toggle = useCommand();

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
      <PageTop title={data.name}><Link className="button outline" to="/promotion-management/programs">{t('back')}</Link></PageTop>
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      {program.error && <ErrorNotice error={program.error} messages={messages} onRetry={program.reload} />}

      <section className="workspace-card">
        <div className="card-head">
          <div>
            <h2>{t(`kind.${data.kind}`)} <ScopeBadge scope={data.scope} /></h2>
            {data.code && <p className="mono">{t('field.code')}: {data.code}</p>}
          </div>
          <span className={paused ? 'status' : 'status ok'}>{t(`status.${data.status}`)}</span>
        </div>
        <p><strong>{t('field.discount')}:</strong> <DiscountText discount={data.discount} /></p>
        <Terms item={data} />
        <p className="muted small">{t('detail.meta', { version: data.version, revision: data.revision, createdAt: format.dateTime(data.createdAt) })}</p>
        <div className="actions form-actions">
          {!editing && <button type="button" className="button outline" onClick={() => setEditing(true)}>{t('edit')}</button>}
          <button type="button" className={paused ? 'button primary' : 'button outline'} onClick={() => { toggle.reset(); setConfirm(true); }}>{paused ? t('program.resume') : t('program.pause')}</button>
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

function initialForm(program) {
  return {
    name: program.name,
    startAt: toLocalInput(program.startAt),
    endAt: toLocalInput(program.endAt),
    discountKind: program.discount?.kind || 'PERCENT',
    percentage: program.discount?.percentage || '',
    amount: program.discount?.amount || '',
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
  const [form, setForm] = useState(() => initialForm(program));
  const [hints, setHints] = useState({});
  const command = useCommand();
  const voucher = program.kind === 'VOUCHER';
  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.type === 'checkbox' ? event.target.checked : event.target.value }));

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
      if (form.discountKind === 'PERCENT' && !isPercent(form.percentage.trim())) found['discount.percentage'] = 'validation.percent';
      if (form.discountKind === 'FIXED' && !isPositive(form.amount.trim())) found['discount.amount'] = 'validation.positive';
      if (!isPositive(form.maxDiscountAmount.trim())) found['discount.maxDiscountAmount'] = 'validation.positive';
      body.discount = form.discountKind === 'PERCENT'
        ? { kind: 'PERCENT', percentage: form.percentage.trim(), maxDiscountAmount: form.maxDiscountAmount.trim() }
        : { kind: 'FIXED', amount: form.amount.trim(), maxDiscountAmount: form.maxDiscountAmount.trim() };
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
        <Field label={t('field.startAt')} error={errorOf('startAt')}><input type="datetime-local" value={form.startAt} onChange={set('startAt')} /></Field>
        <Field label={t('field.endAt')} error={errorOf('endAt')}><input type="datetime-local" value={form.endAt} onChange={set('endAt')} /></Field>
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
