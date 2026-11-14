import React, { useState } from 'react';
import { ErrorNotice, Field, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';

const unwrap = data => Array.isArray(data) ? data : data?.items || data?.content || [];
const locationKey = item => item.id || item.locationId;

export const WEEKDAYS = [
  ['MONDAY', 'Mon'], ['TUESDAY', 'Tue'], ['WEDNESDAY', 'Wed'], ['THURSDAY', 'Thu'],
  ['FRIDAY', 'Fri'], ['SATURDAY', 'Sat'], ['SUNDAY', 'Sun'],
];

/** Texts for the refusals these forms expect from 003.2; anything else falls back to the shared error texts. */
export const sessionAdminMessages = {
  vi: {
    'error.INSUFFICIENT_CAPACITY': 'Sức chứa mới thấp hơn số vé đã xác nhận cộng chỗ đang giữ của phiên này. Hãy nhập sức chứa lớn hơn.',
  },
  en: {
    'error.INSUFFICIENT_CAPACITY': 'The new capacity is below the tickets and payment holds already committed to this session. Enter a larger capacity.',
  },
};

const emptyBulk = { locationId: '', slotIds: [], from: '', to: '', weekdays: [], initialState: 'DRAFT' };

/**
 * 003.2 T108 / FR-001f: a Manager generates sessions for a location over a date range, for the chosen slots and
 * weekdays, in DRAFT or PUBLISHED. The server creates them and reports the combinations it skipped as duplicates.
 */
export function BulkSessionForm({ api, locations, onDone }) {
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
      setFormError('Location, at least one slot, a date range and at least one weekday are required.');
      return;
    }
    if (form.from > form.to) {
      setFormError('The start date must not be after the end date.');
      return;
    }
    setFormError('');
    const body = { ...form, locationId: Number(form.locationId) };
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
    <section className="ota-panel" aria-labelledby="bulk-session-title">
      <div className="ota-panel-head">
        <div>
          <h2 id="bulk-session-title">Generate sessions</h2>
          <p className="muted small">Create future sessions for several slots and weekdays at once. Existing sessions are skipped.</p>
        </div>
      </div>
      <form className="ota-evidence-grid" onSubmit={submit} aria-label="Generate sessions">
        <Field label="Location">
          <select value={form.locationId} onChange={e => setForm({ ...form, locationId: e.target.value, slotIds: [] })}>
            <option value="">Select location</option>
            {locations.map(item => <option key={locationKey(item)} value={locationKey(item)}>{item.name || item.code}</option>)}
          </select>
        </Field>
        <Field label="From"><input type="date" value={form.from} onChange={e => setForm({ ...form, from: e.target.value })} /></Field>
        <Field label="To"><input type="date" value={form.to} onChange={e => setForm({ ...form, to: e.target.value })} /></Field>
        <Field label="Initial state">
          <select value={form.initialState} onChange={e => setForm({ ...form, initialState: e.target.value })}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
          </select>
        </Field>
        <fieldset className="package-checkbox-group">
          <legend>Slots</legend>
          {!form.locationId && <p className="muted small">Select a location to choose its slots.</p>}
          {form.locationId && slots.loading && <p className="muted small" role="status">Loading slots…</p>}
          {form.locationId && slots.error && <ErrorNotice error={slots.error} onRetry={slots.reload} />}
          {form.locationId && !slots.loading && !slots.error && slotItems.length === 0 && <p className="muted small">This location has no active slots.</p>}
          {slotItems.map(item => (
            <label key={item.id} className="package-checkbox">
              <input type="checkbox" checked={form.slotIds.includes(item.id)} onChange={() => toggle('slotIds', item.id)} />
              {' '}{item.label} ({item.startsAtLocal}–{item.endsAtLocal})
            </label>
          ))}
        </fieldset>
        <fieldset className="package-checkbox-group">
          <legend>Weekdays</legend>
          {WEEKDAYS.map(([value, label]) => (
            <label key={value} className="package-checkbox">
              <input type="checkbox" checked={form.weekdays.includes(value)} onChange={() => toggle('weekdays', value)} /> {label}
            </label>
          ))}
        </fieldset>
        <div className="actions">
          <button className="button primary" disabled={command.pending}>{command.pending ? 'Generating…' : 'Generate sessions'}</button>
        </div>
      </form>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} onRetry={command.retry} />
      {result && (
        <SuccessNotice>
          <p>{result.created.length} sessions created; {result.skipped.length} skipped as duplicates.</p>
          {result.skipped.length > 0 && (
            <ul className="small" aria-label="Skipped duplicates">
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
  const [capacity, setCapacity] = useState(String(session.capacity ?? ''));
  const originalOpening = toLocalInput(session.checkInOpensAt);
  const [opening, setOpening] = useState(originalOpening);
  const [formError, setFormError] = useState('');
  const command = useCommand();

  const submit = async e => {
    e.preventDefault();
    const value = Number(capacity);
    if (!Number.isInteger(value) || value < 1) {
      setFormError('Capacity must be a whole number of at least 1.');
      return;
    }
    if (opening && Number.isNaN(new Date(opening).getTime())) {
      setFormError('Enter a valid check-in opening time.');
      return;
    }
    setFormError('');
    const body = { locationId: session.locationId, slotId: session.slotId, workshopDate: session.workshopDate, capacity: value };
    if (opening && opening !== originalOpening) body.checkInOpensAt = new Date(opening).toISOString();
    const outcome = await command.run(key => api.updateSession(session.id, body, key));
    if (outcome.ok) onSaved?.(outcome.result);
  };

  const reload = () => { command.reset(); onReload?.(); };

  return (
    <form className="ota-evidence-grid" onSubmit={submit} aria-label={`Edit session ${session.id}`}>
      <Field label="Capacity"><input type="number" min="1" value={capacity} onChange={e => setCapacity(e.target.value)} /></Field>
      <Field label="Check-in opens at" hint={session.checkInOpeningSource === 'MANAGER_SET' ? 'Set by a Manager.' : 'Default: 60 minutes before the start.'}>
        <input type="datetime-local" value={opening} onChange={e => setOpening(e.target.value)} />
      </Field>
      <div className="actions">
        <button className="button primary" disabled={command.pending}>{command.pending ? 'Saving…' : 'Save session'}</button>
        <button type="button" className="button outline" onClick={onClose} disabled={command.pending}>Close</button>
      </div>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} messages={sessionAdminMessages} onRetry={command.retry} onReload={reload} />
    </form>
  );
}
