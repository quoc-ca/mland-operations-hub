import React, { useState } from 'react';
import { ErrorNotice, Field, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { useEnumLabel, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';

const unwrap = data => Array.isArray(data) ? data : data?.items || data?.content || [];
const locationKey = item => item.id || item.locationId;

export const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Texts for the refusals these forms expect from 003.2; anything else falls back to the shared error texts. */
export const sessionAdminMessages = {
  vi: {
    'error.INSUFFICIENT_CAPACITY': 'Sức chứa mới thấp hơn số vé đã xác nhận cộng chỗ đang giữ của phiên này. Hãy nhập sức chứa lớn hơn.',
  },
  en: {
    'error.INSUFFICIENT_CAPACITY': 'The new capacity is below the tickets and payment holds already committed to this session. Enter a larger capacity.',
  },
};

const emptyBulk = { locationId: '', slotIds: [], from: '', to: '', weekdays: [], capacity: '10', initialState: 'DRAFT' };

/**
 * 003.2 T108 / FR-001f: a Manager generates sessions for a location over a date range, for the chosen slots and
 * weekdays, in DRAFT or PUBLISHED. The server creates them and reports the combinations it skipped as duplicates.
 */
export function BulkSessionForm({ api, locations, onDone }) {
  const t = useT(messages);
  const weekdayLabel = useEnumLabel(messages, 'weekday');
  const stateLabel = useEnumLabel(messages, 'sessionState');
  const [form, setForm] = useState(emptyBulk);
  const [formError, setFormError] = useState('');
  const [result, setResult] = useState(null);
  const command = useCommand();
  const slots = useResource(() => (form.locationId ? api.listSlots(form.locationId) : Promise.resolve([])), [form.locationId]);
  const slotItems = unwrap(slots.data).filter(item => item.active !== false);

  const toggle = (field, value) => setForm(current => ({
    ...current,
    [field]: current[field].includes(value) ? current[field].filter(item => item !== value) : [...current[field], value],
  }));

  const submit = async e => {
    e.preventDefault();
    setResult(null);
    if (!form.locationId || !form.slotIds.length || !form.from || !form.to || !form.weekdays.length) {
      setFormError(t('bulk.required'));
      return;
    }
    const capacity = Number(form.capacity);
    if (!Number.isInteger(capacity) || capacity < 1) {
      setFormError(t('bulk.capacity'));
      return;
    }
    if (form.from > form.to) {
      setFormError(t('bulk.dateOrder'));
      return;
    }
    setFormError('');
    const body = { ...form, locationId: Number(form.locationId), capacity };
    const outcome = await command.run(key => api.bulkCreateSessions(body, key));
    if (outcome.ok) {
      setResult({
        created: outcome.result?.createdSessionIds || [],
        skipped: outcome.result?.skippedDuplicates || [],
      });
      onDone?.();
    }
  };

  return (
    <section className="wsp-panel" aria-labelledby="bulk-session-title">
      <div className="wsp-panel-head">
        <div>
          <h2 id="bulk-session-title">{t('bulk.title')}</h2>
          <p className="muted small">{t('bulk.subtitle')}</p>
        </div>
      </div>
      <form className="wsp-evidence-grid" onSubmit={submit} aria-label={t('bulk.title')}>
        <Field label={t('admin.location')}>
          <select value={form.locationId} onChange={e => setForm({ ...form, locationId: e.target.value, slotIds: [] })}>
            <option value="">{t('admin.selectLocation')}</option>
            {locations.map(item => <option key={locationKey(item)} value={locationKey(item)}>{item.name || item.code}</option>)}
          </select>
        </Field>
        <Field label={t('bulk.from')}><input type="date" value={form.from} onChange={e => setForm({ ...form, from: e.target.value })} /></Field>
        <Field label={t('bulk.to')}><input type="date" value={form.to} onChange={e => setForm({ ...form, to: e.target.value })} /></Field>
        <Field label={t('bulk.capacityPer')}><input type="number" min="1" value={form.capacity} onChange={e => setForm({ ...form, capacity: e.target.value })} /></Field>
        <Field label={t('bulk.initialState')}>
          <select value={form.initialState} onChange={e => setForm({ ...form, initialState: e.target.value })}>
            <option value="DRAFT">{stateLabel('DRAFT')}</option>
            <option value="PUBLISHED">{stateLabel('PUBLISHED')}</option>
          </select>
        </Field>
        <fieldset className="package-checkbox-group">
          <legend>{t('bulk.slots')}</legend>
          {!form.locationId && <p className="muted small">{t('bulk.pickLocation')}</p>}
          {form.locationId && slots.loading && <p className="muted small" role="status">{t('bulk.loadingSlots')}</p>}
          {form.locationId && slots.error && <ErrorNotice error={slots.error} onRetry={slots.reload} />}
          {form.locationId && !slots.loading && !slots.error && slotItems.length === 0 && <p className="muted small">{t('bulk.noSlots')}</p>}
          {slotItems.map(item => (
            <label key={item.id} className="package-checkbox">
              <input type="checkbox" checked={form.slotIds.includes(item.id)} onChange={() => toggle('slotIds', item.id)} />
              {' '}{item.label} ({item.startsAtLocal}–{item.endsAtLocal})
            </label>
          ))}
        </fieldset>
        <fieldset className="package-checkbox-group">
          <legend>{t('bulk.weekdays')}</legend>
          {WEEKDAYS.map(value => (
            <label key={value} className="package-checkbox">
              <input type="checkbox" checked={form.weekdays.includes(value)} onChange={() => toggle('weekdays', value)} /> {weekdayLabel(value)}
            </label>
          ))}
        </fieldset>
        <div className="actions">
          <button className="button primary" disabled={command.pending}>{command.pending ? t('bulk.generating') : t('bulk.generate')}</button>
        </div>
      </form>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} onRetry={command.retry} />
      {result && (
        <SuccessNotice>
          <p>{t('bulk.result', { created: result.created.length, skipped: result.skipped.length })}</p>
          {result.skipped.length > 0 && (
            <ul className="small" aria-label={t('bulk.skippedAria')}>
              {result.skipped.map(item => <li key={item}>{item}</li>)}
            </ul>
          )}
        </SuccessNotice>
      )}
    </section>
  );
}

const pad = value => String(value).padStart(2, '0');
/** An instant as the value of a datetime-local input, in the browser's time zone. */
export function toLocalInput(instant) {
  if (!instant) return '';
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 003.2 T112 (narrowed, ERROR-LOG FE-DESIGN-0004): a Manager edits a session's capacity and its check-in opening
 * time with PATCH /workshop-sessions/{id}. The body carries the session's current location, slot and date unchanged,
 * because the backend does not move a session yet. A capacity below the committed tickets is refused
 * (INSUFFICIENT_CAPACITY); a concurrent change is a 409 offering to load the latest session.
 */
export function SessionEditForm({ api, session, onSaved, onReload, onClose }) {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const [capacity, setCapacity] = useState(String(session.capacity ?? ''));
  const originalOpening = toLocalInput(session.checkInOpensAt);
  const [opening, setOpening] = useState(originalOpening);
  const [formError, setFormError] = useState('');
  const command = useCommand();

  const submit = async e => {
    e.preventDefault();
    const value = Number(capacity);
    if (!Number.isInteger(value) || value < 1) {
      setFormError(t('bulk.capacity'));
      return;
    }
    if (opening && Number.isNaN(new Date(opening).getTime())) {
      setFormError(t('sessionEdit.invalidOpening'));
      return;
    }
    setFormError('');
    const body = { locationId: session.locationId, slotId: session.slotId, workshopDate: session.workshopDate, capacity: value };
    if (session.version != null) body.version = session.version;
    if (opening && opening !== originalOpening) body.checkInOpensAt = new Date(opening).toISOString();
    const outcome = await command.run(key => api.updateSession(session.id, body, key));
    if (outcome.ok) onSaved?.(outcome.result);
  };

  const reload = () => { command.reset(); onReload?.(); };

  return (
    <form className="wsp-evidence-grid" onSubmit={submit} aria-label={t('sessionEdit.aria', { id: session.id })}>
      <Field label={t('sessionEdit.capacity')}><input type="number" min="1" value={capacity} onChange={e => setCapacity(e.target.value)} /></Field>
      <Field label={t('sessionEdit.opensAt')} hint={session.checkInOpeningSource === 'MANAGER_SET' ? t('sessionEdit.hintManager') : t('sessionEdit.hintDefault')}>
        <input type="datetime-local" value={opening} onChange={e => setOpening(e.target.value)} />
      </Field>
      <div className="actions">
        <button className="button primary" disabled={command.pending}>{command.pending ? t('sessionEdit.saving') : t('sessionEdit.save')}</button>
        <button type="button" className="button outline" onClick={onClose} disabled={command.pending}>{ts('close')}</button>
      </div>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} messages={sessionAdminMessages} onRetry={command.retry} onReload={reload} />
    </form>
  );
}
