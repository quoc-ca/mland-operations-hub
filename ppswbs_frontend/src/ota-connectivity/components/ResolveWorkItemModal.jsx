import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';
import { useT, useEnumLabel } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { otaMessages } from '../messages';

const RESOLUTIONS = ['RESYNC_REQUESTED', 'ACCEPTED', 'REJECTED', 'ESCALATED'];

export function ResolveWorkItemModal({ open, workItem, onClose, onResolve }) {
  const t = useT(otaMessages);
  const tShared = useT(sharedMessages);
  const resolutionLabel = useEnumLabel(otaMessages, 'resolution');
  const [resolution, setResolution] = useState('RESYNC_REQUESTED');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  if (!open || !workItem) return null;

  const validate = () => {
    const errs = {};
    if (!reason.trim()) {
      errs.reason = t('resolve.reason.required');
    } else if (reason.trim().length > 1000) {
      errs.reason = t('resolve.reason.tooLong');
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError(null);
    if (!validate()) return;

    try {
      setSubmitting(true);
      await onResolve({
        resolution,
        reason: reason.trim(),
      });
      onClose();
    } catch (err) {
      setApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={t('resolve.dialog')}>
        <h2>{t('resolve.title')}</h2>
        <p className="small muted">
          {t('resolve.intro')} <strong className="mono">{workItem.workItemId}</strong>
          {workItem.mappingId && <> ({t('resolve.mappingLabel')} <span className="mono">{workItem.mappingId}</span>)</>}.
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field label={t('resolve.action')} error={apiError?.fieldError('resolution')}>
            <select
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              disabled={submitting}
            >
              {RESOLUTIONS.map((value) => (
                <option key={value} value={value}>{resolutionLabel(value)}</option>
              ))}
            </select>
          </Field>

          <Field label={t('resolve.reason')} hint={t('resolve.reasonHint')} error={errors.reason || apiError?.fieldError('reason')}>
            <textarea
              name="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('resolve.reasonPlaceholder')}
              rows={4}
              disabled={submitting}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              {tShared('cancel')}
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? t('resolve.resolving') : t('resolve.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
