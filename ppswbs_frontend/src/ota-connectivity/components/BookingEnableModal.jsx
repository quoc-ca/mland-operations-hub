import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';
import { useT, useEnumLabel } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { ALL_READINESS_CHECKS } from '../api/otaConnectivityApi';
import { otaMessages } from '../messages';

const OUTCOMES = ['VERIFIED', 'FAILED', 'MISSING'];

export function BookingEnableModal({ open, mapping, onClose, onSave }) {
  const t = useT(otaMessages);
  const tShared = useT(sharedMessages);
  const outcomeLabel = useEnumLabel(otaMessages, 'outcome');
  const [readinessRecordReference, setReadinessRecordReference] = useState('');
  const [reason, setReason] = useState('');
  const [checks, setChecks] = useState(() =>
    ALL_READINESS_CHECKS.map((check) => ({
      check,
      outcome: 'VERIFIED',
      evidenceReference: `EVID-${check}`,
    }))
  );

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  if (!open || !mapping) return null;

  const handleOutcomeChange = (index, outcome) => {
    setChecks((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], outcome };
      return updated;
    });
  };

  const validate = () => {
    const errs = {};
    if (!readinessRecordReference.trim()) {
      errs.readinessRecordReference = t('enable.recordRef.required');
    }
    if (!reason.trim()) {
      errs.reason = t('enable.reason.required');
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
      await onSave({
        readinessRecordReference: readinessRecordReference.trim(),
        checks,
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
      <div className="dialog" role="dialog" aria-modal="true" aria-label={t('enable.dialog')}>
        <h2>{t('enable.title')}</h2>
        <p className="small muted">
          {t('enable.introBefore')} <strong className="mono">{mapping.mappingId}</strong> ({mapping.provider}).{' '}
          {t('enable.introAfter')}
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field
            label={t('enable.recordRef')}
            hint={t('enable.recordRefHint')}
            error={errors.readinessRecordReference || apiError?.fieldError('readinessRecordReference')}
          >
            <input
              type="text"
              name="readinessRecordReference"
              value={readinessRecordReference}
              onChange={(e) => setReadinessRecordReference(e.target.value)}
              placeholder={t('field.example', { value: 'DOC-KLOOK-READINESS-REC-20261101' })}
              disabled={submitting}
            />
          </Field>

          <Field label={t('enable.reason')} error={errors.reason || apiError?.fieldError('reason')}>
            <input
              type="text"
              name="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('enable.reasonPlaceholder')}
              disabled={submitting}
            />
          </Field>

          <label style={{ fontWeight: 700, fontSize: '13px', marginTop: '12px', display: 'block' }}>
            {t('enable.checklist')}
          </label>
          <div className="readiness-grid">
            {checks.map((item, idx) => (
              <div key={item.check} className="check-item">
                <label className="mono" style={{ fontSize: '12px' }}>{item.check}</label>
                <select
                  value={item.outcome}
                  onChange={(e) => handleOutcomeChange(idx, e.target.value)}
                  disabled={submitting}
                >
                  {OUTCOMES.map((value) => (
                    <option key={value} value={value}>{outcomeLabel(value)}</option>
                  ))}
                </select>
              </div>
            ))}
          </div>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              {tShared('cancel')}
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? t('enable.submitting') : t('enable.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
