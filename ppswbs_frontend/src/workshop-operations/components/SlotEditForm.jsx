import React, { useState } from 'react';
import { ErrorNotice, Field, useCommand } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';

/** Texts for the refusals of PATCH /workshop-slots/{id} (003.2 FR-001e); other errors use the shared texts. */
export const slotEditMessages = {
  vi: {
    'error.RESOURCE_NOT_FOUND': 'Không tìm thấy khung giờ này. Có thể nó đã bị xóa; hãy tải lại danh sách.',
    'error.FORBIDDEN': 'Chỉ Manager đang hoạt động mới được sửa khung giờ.',
  },
  en: {
    'error.RESOURCE_NOT_FOUND': 'This slot was not found. It may have been removed; reload the list.',
    'error.FORBIDDEN': 'Only an active Manager can edit a slot.',
  },
};

const hhmm = value => (value ? String(value).slice(0, 5) : '');

/**
 * 003.2 FR-001e: a Manager corrects a slot's label and same-day time window. Active/inactive stays with the
 * Activate/Deactivate action, so the slot's current active flag is sent unchanged. A time change only affects
 * sessions created later (server rule); the server also refuses overlap with another slot of the location.
 */
export function SlotEditForm({ api, slot, onSaved, onReload, onClose }) {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const [label, setLabel] = useState(slot.label ?? '');
  const [startsAtLocal, setStartsAtLocal] = useState(hhmm(slot.startsAtLocal));
  const [endsAtLocal, setEndsAtLocal] = useState(hhmm(slot.endsAtLocal));
  const [formError, setFormError] = useState('');
  const command = useCommand();

  const submit = async e => {
    e.preventDefault();
    const trimmed = label.trim();
    if (!trimmed) { setFormError(t('slot.labelRequired')); return; }
    if (!startsAtLocal || !endsAtLocal) { setFormError(t('slot.timesRequired')); return; }
    if (startsAtLocal >= endsAtLocal) { setFormError(t('slot.timeOrder')); return; }
    if (trimmed === (slot.label ?? '') && startsAtLocal === hhmm(slot.startsAtLocal) && endsAtLocal === hhmm(slot.endsAtLocal)) {
      setFormError(t('slot.noChange')); return;
    }
    setFormError('');
    const body = { label: trimmed, startsAtLocal, endsAtLocal, active: slot.active !== false };
    const outcome = await command.run(key => api.updateSlot(slot.id, body, key));
    if (outcome.ok) onSaved?.(outcome.result);
  };

  const retry = async () => { const outcome = await command.retry(); if (outcome?.ok) onSaved?.(outcome.result); };
  const reload = () => { command.reset(); onReload?.(); };

  return (
    <form className="wsp-evidence-grid" onSubmit={submit} aria-label={t('slot.aria', { label: slot.label })}>
      <Field label={t('slot.label')}><input value={label} onChange={e => setLabel(e.target.value)} /></Field>
      <Field label={t('slot.startsAt')}><input type="time" value={startsAtLocal} onChange={e => setStartsAtLocal(e.target.value)} /></Field>
      <Field label={t('slot.endsAt')}><input type="time" value={endsAtLocal} onChange={e => setEndsAtLocal(e.target.value)} /></Field>
      <div className="actions">
        <button className="button primary" disabled={command.pending}>{command.pending ? t('slot.saving') : t('slot.save')}</button>
        <button type="button" className="button outline" onClick={onClose} disabled={command.pending}>{ts('close')}</button>
      </div>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} messages={slotEditMessages} onRetry={retry} onReload={reload} />
    </form>
  );
}
