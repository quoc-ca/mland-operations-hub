import React, { useState } from 'react';
import { ErrorNotice, Field, useCommand } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';

/** Texts for the refusals of PATCH /workshop-locations/{id} (003.2 T135); other errors use the shared texts. */
export const locationEditMessages = {
  vi: {
    'error.INVALID_STATE': 'Không thể ngừng hoạt động địa điểm này vì vẫn còn buổi workshop chưa kết thúc (khác ATTENDANCE_FINALIZED/CANCELLED) dùng địa điểm. Hãy chờ các buổi kết thúc hoặc hủy chúng trước.',
    'error.RESOURCE_NOT_FOUND': 'Không tìm thấy địa điểm này. Có thể nó đã bị xóa; hãy tải lại danh sách.',
    'error.FORBIDDEN': 'Chỉ Manager đang hoạt động mới được sửa địa điểm.',
  },
  en: {
    'error.INVALID_STATE': 'This location cannot be deactivated while a session that has not ended (anything but ATTENDANCE_FINALIZED or CANCELLED) still uses it. Wait for those sessions to end or cancel them first.',
    'error.RESOURCE_NOT_FOUND': 'This location was not found. It may have been removed; reload the list.',
    'error.FORBIDDEN': 'Only an active Manager can edit a location.',
  },
};

/**
 * 003.2 T136 / FR-012c: a Manager renames a location, changes its address or deactivates/reactivates it.
 * Sends only the fields that changed (the code is immutable and never sent). The server decides authority and
 * the deactivation rule; the screen only shows the refusal.
 */
export function LocationEditForm({ api, location, onSaved, onReload, onClose }) {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const [name, setName] = useState(location.name ?? '');
  const [address, setAddress] = useState(location.address ?? '');
  const [active, setActive] = useState(location.active !== false);
  const [formError, setFormError] = useState('');
  const command = useCommand();

  const submit = async e => {
    e.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) { setFormError(t('location.nameRequired')); return; }
    const body = {};
    if (trimmedName !== (location.name ?? '')) body.name = trimmedName;
    if (address.trim() !== (location.address ?? '')) body.address = address.trim();
    if (active !== (location.active !== false)) body.active = active;
    if (Object.keys(body).length === 0) { setFormError(t('location.noChange')); return; }
    setFormError('');
    const outcome = await command.run(key => api.updateLocation(location.id, body, key));
    if (outcome.ok) onSaved?.(outcome.result);
  };

  const retry = async () => { const outcome = await command.retry(); if (outcome?.ok) onSaved?.(outcome.result); };
  const reload = () => { command.reset(); onReload?.(); };

  return (
    <form className="wsp-evidence-grid" onSubmit={submit} aria-label={t('location.aria', { code: location.code })}>
      <Field label={t('location.name')}><input value={name} onChange={e => setName(e.target.value)} /></Field>
      <Field label={t('location.address')}><input value={address} onChange={e => setAddress(e.target.value)} /></Field>
      <label className="package-checkbox"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> {t('location.active')}</label>
      <div className="actions">
        <button className="button primary" disabled={command.pending}>{command.pending ? t('location.saving') : t('location.save')}</button>
        <button type="button" className="button outline" onClick={onClose} disabled={command.pending}>{ts('close')}</button>
      </div>
      <ErrorNotice error={formError ? new Error(formError) : null} />
      <ErrorNotice error={command.error} messages={locationEditMessages} onRetry={retry} onReload={reload} />
    </form>
  );
}
