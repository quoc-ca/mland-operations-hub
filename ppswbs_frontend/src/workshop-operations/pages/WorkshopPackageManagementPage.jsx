import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ConfirmDialog, ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createWorkshopPackageApi } from '../api/packageApi';
import '../workshop-operations.css';
import '../../ota-connectivity/ota-connectivity.css';

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
  };
}

function validate(form) {
  const errors = {};
  if (!form.name.trim()) errors.name = 'Enter a package name.';
  if (!form.description.trim()) errors.description = 'Enter a customer-safe description.';
  for (const field of ['publicPrice', 'minParticipants', 'maxParticipants', 'requiredSessionCount', 'minimumSameDaySlotGap', 'requiredFoundryWorkingDaysBetweenSessions']) {
    const value = Number(form[field]);
    if (!Number.isInteger(value) || value < 0) errors[field] = 'Use a non-negative whole number.';
  }
  if (Number(form.publicPrice) <= 0) errors.publicPrice = 'Public price must be positive.';
  if (Number(form.minParticipants) > Number(form.maxParticipants)) errors.maxParticipants = 'Maximum must be at least minimum.';
  if (Number(form.requiredSessionCount) < 1) errors.requiredSessionCount = 'At least one session is required.';
  return errors;
}

function PackageForm({ form, setForm, errors, pending, onSubmit, onCancel, mode }) {
  const set = field => event => setForm(current => ({ ...current, [field]: event.target.type === 'checkbox' ? event.target.checked : event.target.value }));
  return <form className="ota-panel ota-form package-admin-form" onSubmit={onSubmit} noValidate>
    <div className="ota-panel-head"><div><h2>{mode === 'edit' ? 'Edit package' : 'Create package draft'}</h2><p className="muted small">Prospective changes only; published history remains immutable.</p></div></div>
    <div className="ota-evidence-grid">
      <Field label="Name" error={errors.name}><input value={form.name} onChange={set('name')} maxLength={180} /></Field>
      <Field label="Public price (VND)" error={errors.publicPrice}><input inputMode="numeric" value={form.publicPrice} onChange={set('publicPrice')} /></Field>
      <Field label="Minimum tickets" error={errors.minParticipants}><input inputMode="numeric" value={form.minParticipants} onChange={set('minParticipants')} /></Field>
      <Field label="Maximum tickets" error={errors.maxParticipants}><input inputMode="numeric" value={form.maxParticipants} onChange={set('maxParticipants')} /></Field>
      <Field label="Required sessions" error={errors.requiredSessionCount}><input inputMode="numeric" value={form.requiredSessionCount} onChange={set('requiredSessionCount')} /></Field>
      <Field label="Minimum same-day slot gap" error={errors.minimumSameDaySlotGap}><input inputMode="numeric" value={form.minimumSameDaySlotGap} onChange={set('minimumSameDaySlotGap')} /></Field>
      <Field label="Foundry working days between sessions" error={errors.requiredFoundryWorkingDaysBetweenSessions}><input inputMode="numeric" value={form.requiredFoundryWorkingDaysBetweenSessions} onChange={set('requiredFoundryWorkingDaysBetweenSessions')} /></Field>
      <label className="package-checkbox"><input type="checkbox" checked={form.sameLocationRequired} onChange={set('sameLocationRequired')} /> Require the same location</label>
    </div>
    <Field label="Customer-safe description" error={errors.description}><textarea value={form.description} onChange={set('description')} maxLength={1000} /></Field>
    <div className="actions form-actions"><button className="button primary" type="submit" disabled={pending}>{pending ? 'Saving…' : mode === 'edit' ? 'Save changes' : 'Create draft'}</button><button className="button outline" type="button" onClick={onCancel} disabled={pending}>Cancel</button></div>
  </form>;
}

export default function WorkshopPackageManagementPage() {
  const { status } = useAuth();
  const api = createWorkshopPackageApi(useHttp());
  const resource = useResource(() => api.listManaged(), []);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [mode, setMode] = useState(null);
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');
  const [retireTarget, setRetireTarget] = useState(null);
  const command = useCommand();
  const packages = useMemo(() => Array.isArray(resource.data) ? resource.data : [], [resource.data]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Authentication required to manage workshop packages."
      />
    );
  }

  const startCreate = () => { setMode('create'); setSelected(null); setForm({ ...emptyForm }); setErrors({}); setNotice(''); };
  const startEdit = item => { setMode('edit'); setSelected(item); setForm(toForm(item)); setErrors({}); setNotice(''); };
  const save = async event => {
    event.preventDefault();
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    const outcome = await command.run(key => mode === 'edit' ? api.update(selected.packageId, toPayload(form), key) : api.createDraft(toPayload(form), key));
    if (outcome.ok) {
      setSelected(outcome.result);
      setForm(toForm(outcome.result));
      setMode('edit');
      setNotice(mode === 'edit' ? 'Package updated.' : 'Draft created. Publish it when the package is ready.');
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
      setNotice(targetState === 'RETIRED' ? 'Package retired.' : 'Package published.');
      resource.reload();
    }
  };

  return <div className="ota-workspace"><OperationsSidebar />
    <main className="ota-main"><div className="ota-topline">MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span></div><header className="ota-page-heading"><div><p className="ota-eyebrow">CATALOGUE</p><h1>Workshop package management</h1><p>Create, edit and retire customer-facing workshop packages.</p></div><button className="button primary" type="button" onClick={startCreate}>Create package</button></header>
      <SuccessNotice>{notice}</SuccessNotice><ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} /><ErrorNotice error={command.error} onRetry={command.retry} />
      {resource.loading ? <Loading /> : <section className="ota-panel"><div className="ota-panel-head"><h2>All packages</h2><span>{packages.length} records</span></div>{packages.length === 0 ? <div className="empty-state"><p>No workshop packages found.</p><button className="button outline" type="button" onClick={startCreate}>Create the first package</button></div> : <div className="package-admin-grid">{packages.map(item => <article className="package-admin-card" key={item.packageId}><div className="package-admin-card-head"><div><span className="ops-status">{item.state}</span><h3>{item.name}</h3></div><code>#{item.packageId}</code></div><p>{item.description}</p><dl><div><dt>Price</dt><dd>{Number(item.publicPrice).toLocaleString('vi-VN')} VND</dd></div><div><dt>Tickets</dt><dd>{item.minParticipants}–{item.maxParticipants}</dd></div><div><dt>Sessions</dt><dd>{item.requiredSessionCount}</dd></div></dl><div className="ops-actions">{item.state !== 'RETIRED' && <button className="button outline small" type="button" onClick={() => startEdit(item)}>Edit</button>}{item.state === 'PUBLISHED' && <button className="button danger small" type="button" onClick={() => setRetireTarget(item)}>Retire</button>}</div></article>)}</div>}</section>}
      {mode && <PackageForm form={form} setForm={setForm} errors={errors} pending={command.pending} onSubmit={save} onCancel={() => setMode(null)} mode={mode} />}
      {selected?.state === 'DRAFT' && mode === 'edit' && <section className="ota-panel draft-publish-panel"><div><h2>Draft ready</h2><p>Review the values above, then publish this draft to make it available for customer discovery.</p></div><button className="button primary" type="button" disabled={command.pending} onClick={() => transition(selected, 'PUBLISHED')}>Publish draft</button></section>}
      <ConfirmDialog open={Boolean(retireTarget)} title="Retire package?" confirmLabel="Retire package" danger pending={command.pending} onCancel={() => setRetireTarget(null)} onConfirm={() => transition(retireTarget, 'RETIRED')}><p>This stops new bookings. Existing booking snapshots are preserved.</p></ConfirmDialog>
    </main></div>;
}
