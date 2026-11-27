import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { ConfirmDialog, ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { HeaderActions } from '../../shared/ui/OperationsShell';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createWorkshopPackageApi } from '../api/packageApi';
import { PackageImageField } from '../components/PackageImageField';
import '../workshop-operations.css';
import '../../shared/ui/workspace.css';

const emptyForm = {
  name: '',
  description: '',
  publicPrice: '',
  minParticipants: '1',
  maxParticipants: '1',
  requiredSessionCount: '1',
  sameLocationRequired: true,
  minimumSameDaySlotGap: '0',
  requiredFoundryWorkingDaysBetweenSessions: '0',
  imageMediaId: null,
};

function toForm(item) {
  if (!item) return { ...emptyForm };
  return {
    name: item.name || '',
    description: item.description || '',
    publicPrice: String(item.publicPrice ?? ''),
    minParticipants: String(item.minParticipants ?? 1),
    maxParticipants: String(item.maxParticipants ?? 1),
    requiredSessionCount: String(item.requiredSessionCount ?? 1),
    sameLocationRequired: item.sameLocationRequired !== false,
    minimumSameDaySlotGap: String(item.minimumSameDaySlotGap ?? 0),
    requiredFoundryWorkingDaysBetweenSessions: String(item.requiredFoundryWorkingDaysBetweenSessions ?? 0),
    imageMediaId: item.imageMediaId || null,
  };
}

function toPayload(form) {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    publicPrice: Number(form.publicPrice),
    minParticipants: Number(form.minParticipants),
    maxParticipants: Number(form.maxParticipants),
    requiredSessionCount: Number(form.requiredSessionCount),
    sameLocationRequired: Boolean(form.sameLocationRequired),
    minimumSameDaySlotGap: Number(form.minimumSameDaySlotGap),
    requiredFoundryWorkingDaysBetweenSessions: Number(form.requiredFoundryWorkingDaysBetweenSessions),
    imageMediaId: form.imageMediaId || null,
  };
}

function validate(form, t) {
  const errors = {};
  if (!form.name.trim()) errors.name = t('pkg.err.name');
  if (!form.description.trim()) errors.description = t('pkg.err.description');
  for (const field of ['publicPrice', 'minParticipants', 'maxParticipants', 'requiredSessionCount', 'minimumSameDaySlotGap', 'requiredFoundryWorkingDaysBetweenSessions']) {
    const value = Number(form[field]);
    if (!Number.isInteger(value) || value < 0) errors[field] = t('pkg.err.wholeNumber');
  }
  if (Number(form.publicPrice) <= 0) errors.publicPrice = t('pkg.err.price');
  if (Number(form.minParticipants) > Number(form.maxParticipants)) errors.maxParticipants = t('pkg.err.maxTickets');
  if (Number(form.requiredSessionCount) < 1) errors.requiredSessionCount = t('pkg.err.sessions');
  return errors;
}

function PackageForm({ form, setForm, errors, pending, onSubmit, onCancel, mode, api, savedPackage }) {
  const [uploading, setUploading] = useState(false);
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const set = field => event => setForm(current => ({ ...current, [field]: event.target.type === 'checkbox' ? event.target.checked : event.target.value }));
  return <form className="wsp-panel wsp-form package-admin-form" onSubmit={onSubmit} noValidate>
    <div className="wsp-panel-head"><div><h2>{mode === 'edit' ? t('pkg.form.edit') : t('pkg.form.create')}</h2><p className="muted small">{t('pkg.form.note')}</p></div></div>
    <div className="wsp-evidence-grid">
      <Field label={t('pkg.form.name')} error={errors.name}><input value={form.name} onChange={set('name')} maxLength={180} /></Field>
      <Field label={t('pkg.form.price')} error={errors.publicPrice}><input inputMode="numeric" value={form.publicPrice} onChange={set('publicPrice')} /></Field>
      <Field label={t('pkg.form.minTickets')} error={errors.minParticipants}><input inputMode="numeric" value={form.minParticipants} onChange={set('minParticipants')} /></Field>
      <Field label={t('pkg.form.maxTickets')} error={errors.maxParticipants}><input inputMode="numeric" value={form.maxParticipants} onChange={set('maxParticipants')} /></Field>
      <Field label={t('pkg.form.sessions')} error={errors.requiredSessionCount}><input inputMode="numeric" value={form.requiredSessionCount} onChange={set('requiredSessionCount')} /></Field>
      <Field label={t('pkg.form.slotGap')} error={errors.minimumSameDaySlotGap}><input inputMode="numeric" value={form.minimumSameDaySlotGap} onChange={set('minimumSameDaySlotGap')} /></Field>
      <Field label={t('pkg.form.workingDays')} error={errors.requiredFoundryWorkingDaysBetweenSessions}><input inputMode="numeric" value={form.requiredFoundryWorkingDaysBetweenSessions} onChange={set('requiredFoundryWorkingDaysBetweenSessions')} /></Field>
      <label className="package-checkbox"><input type="checkbox" checked={form.sameLocationRequired} onChange={set('sameLocationRequired')} /> {t('pkg.form.sameLocation')}</label>
    </div>
    <Field label={t('pkg.form.description')} error={errors.description}><textarea value={form.description} onChange={set('description')} maxLength={1000} /></Field>
    <PackageImageField api={api} savedPackage={savedPackage} value={form.imageMediaId} disabled={pending} onBusy={setUploading}
      onChange={imageMediaId => setForm(current => ({ ...current, imageMediaId }))} />
    <div className="actions form-actions"><button className="button primary" type="submit" disabled={pending || uploading}>{pending ? t('pkg.form.saving') : mode === 'edit' ? t('pkg.form.save') : t('pkg.form.createDraft')}</button><button className="button outline" type="button" onClick={onCancel} disabled={pending}>{ts('cancel')}</button></div>
  </form>;
}

export default function WorkshopPackageManagementPage() {
  const t = useT(messages);
  const fmt = useFormat();
  const stateLabel = useEnumLabel(messages, 'packageState');
  const { status } = useAuth();
  const api = createWorkshopPackageApi(useHttp());
  const resource = useResource(() => api.listManaged(), []);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [mode, setMode] = useState(null);
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');
  // A new number per opened form, so an upload still running for the previous one cannot reach this one.
  const [formSession, setFormSession] = useState(0);
  const [retireTarget, setRetireTarget] = useState(null);
  const command = useCommand();
  const packages = useMemo(() => Array.isArray(resource.data) ? resource.data : [], [resource.data]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline={t('topline.operations')}
        message={t('pkg.signIn')}
      />
    );
  }

  const startCreate = () => { setFormSession(n => n + 1); setMode('create'); setSelected(null); setForm({ ...emptyForm }); setErrors({}); setNotice(''); };
  const startEdit = item => { setFormSession(n => n + 1); setMode('edit'); setSelected(item); setForm(toForm(item)); setErrors({}); setNotice(''); };
  const save = async event => {
    event.preventDefault();
    const found = validate(form, t);
    setErrors(found);
    if (Object.keys(found).length) return;
    const outcome = await command.run(key => mode === 'edit' ? api.update(selected.packageId, toPayload(form), key) : api.createDraft(toPayload(form), key));
    if (outcome.ok) {
      setSelected(outcome.result);
      setForm(toForm(outcome.result));
      setMode('edit');
      setNotice(mode === 'edit' ? t('pkg.ok.updated') : t('pkg.ok.draftCreated'));
      resource.reload();
    }
  };
  const transition = async (item, targetState) => {
    const outcome = await command.run(key => api.transition(item.packageId, targetState, key));
    if (outcome.ok) {
      setSelected(outcome.result);
      setForm(toForm(outcome.result));
      setMode('edit');
      setRetireTarget(null);
      setNotice(targetState === 'RETIRED' ? t('pkg.ok.retired') : t('pkg.ok.published'));
      resource.reload();
    }
  };

  return <div className="wsp-workspace">
    <main className="wsp-main"><div className="wsp-topline">{t('topline.operations')} <span>● {t('authorized')}</span></div><HeaderActions><button className="button primary" type="button" onClick={startCreate}>{t('pkg.create')}</button></HeaderActions><p className="muted">{t('pkg.intro')}</p>
      <SuccessNotice>{notice}</SuccessNotice><ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} /><ErrorNotice error={command.error} messages={messages} onRetry={command.retry} />
      {resource.loading ? <Loading /> : <section className="wsp-panel"><div className="wsp-panel-head"><h2>{t('pkg.all')}</h2><span>{t('records', { count: packages.length })}</span></div>{packages.length === 0 ? <div className="empty-state"><p>{t('pkg.empty')}</p><button className="button outline" type="button" onClick={startCreate}>{t('pkg.createFirst')}</button></div> : <div className="package-admin-grid">{packages.map(item => <article className="package-admin-card" key={item.packageId}><div className="package-admin-card-head"><div><span className="ops-status">{stateLabel(item.state)}</span><h3>{item.name}</h3></div><code>#{item.packageId}</code></div><p>{item.description}</p><dl><div><dt>{t('pkg.price')}</dt><dd>{fmt.money(String(item.publicPrice))}</dd></div><div><dt>{t('pkg.tickets')}</dt><dd>{item.minParticipants}–{item.maxParticipants}</dd></div><div><dt>{t('pkg.sessions')}</dt><dd>{item.requiredSessionCount}</dd></div></dl><div className="ops-actions">{item.state !== 'RETIRED' && <button className="button outline small" type="button" onClick={() => startEdit(item)}>{t('pkg.edit')}</button>}{item.state === 'PUBLISHED' && <button className="button danger small" type="button" onClick={() => setRetireTarget(item)}>{t('pkg.retire')}</button>}</div></article>)}</div>}</section>}
      {mode && <PackageForm key={formSession} api={api} savedPackage={selected} form={form} setForm={setForm} errors={errors} pending={command.pending} onSubmit={save} onCancel={() => setMode(null)} mode={mode} />}
      {selected?.state === 'DRAFT' && mode === 'edit' && <section className="wsp-panel draft-publish-panel"><div><h2>{t('pkg.draftReady')}</h2><p>{t('pkg.draftReadyBody')}</p></div><button className="button primary" type="button" disabled={command.pending} onClick={() => transition(selected, 'PUBLISHED')}>{t('pkg.publishDraft')}</button></section>}
      <ConfirmDialog open={Boolean(retireTarget)} title={t('pkg.retireTitle')} confirmLabel={t('pkg.retireConfirm')} danger pending={command.pending} onCancel={() => setRetireTarget(null)} onConfirm={() => transition(retireTarget, 'RETIRED')}><p>{t('pkg.retireBody')}</p></ConfirmDialog>
    </main></div>;
}
