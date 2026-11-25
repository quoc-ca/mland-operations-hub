import React, { useState, useEffect } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { otaMessages } from '../messages';

export function MappingModal({ open, channelId, mapping, onClose, onSave }) {
  const t = useT(otaMessages);
  const tShared = useT(sharedMessages);
  const isEdit = Boolean(mapping);

  const [form, setForm] = useState({
    providerProductReference: mapping?.providerProductReference || '',
    providerOptionReference: mapping?.providerOptionReference || '',
    workshopOfferReference: mapping?.workshopOfferReference || '',
    sessionReference: mapping?.sessionReference || '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  useEffect(() => {
    if (open) {
      setForm({
        providerProductReference: mapping?.providerProductReference || '',
        providerOptionReference: mapping?.providerOptionReference || '',
        workshopOfferReference: mapping?.workshopOfferReference || '',
        sessionReference: mapping?.sessionReference || '',
      });
      setErrors({});
      setApiError(null);
    }
  }, [open, mapping]);

  if (!open) return null;

  const validate = () => {
    const errs = {};
    if (!form.providerProductReference.trim()) errs.providerProductReference = t('mapping.productRef.required');
    if (!form.providerOptionReference.trim()) errs.providerOptionReference = t('mapping.optionRef.required');
    if (!form.workshopOfferReference.trim()) errs.workshopOfferReference = t('mapping.offerRef.required');
    if (!form.sessionReference.trim()) errs.sessionReference = t('mapping.sessionRef.required');
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
        ...form,
        version: isEdit ? mapping.version : undefined,
      });
      onClose();
    } catch (err) {
      setApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const example = (value) => t('field.example', { value });
  const title = isEdit ? t('mapping.edit') : t('mapping.create');

  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field label={t('mapping.productRef')} error={errors.providerProductReference || apiError?.fieldError('providerProductReference')}>
            <input
              type="text"
              name="providerProductReference"
              value={form.providerProductReference}
              onChange={(e) => setForm({ ...form, providerProductReference: e.target.value })}
              placeholder={example('KLOOK-PROD-BASICRING')}
              disabled={submitting}
            />
          </Field>

          <Field label={t('mapping.optionRef')} error={errors.providerOptionReference || apiError?.fieldError('providerOptionReference')}>
            <input
              type="text"
              name="providerOptionReference"
              value={form.providerOptionReference}
              onChange={(e) => setForm({ ...form, providerOptionReference: e.target.value })}
              placeholder={example('KLOOK-OPT-MORNING')}
              disabled={submitting}
            />
          </Field>

          <Field label={t('mapping.offerRef')} error={errors.workshopOfferReference || apiError?.fieldError('workshopOfferReference')}>
            <input
              type="text"
              name="workshopOfferReference"
              value={form.workshopOfferReference}
              onChange={(e) => setForm({ ...form, workshopOfferReference: e.target.value })}
              placeholder={example('offer-basic-ring-standard')}
              disabled={submitting}
            />
          </Field>

          <Field label={t('mapping.sessionRef')} error={errors.sessionReference || apiError?.fieldError('sessionReference')}>
            <input
              type="text"
              name="sessionReference"
              value={form.sessionReference}
              onChange={(e) => setForm({ ...form, sessionReference: e.target.value })}
              placeholder={example('sess-d1f8a85e-7a42-4b2a-874e-5a52e9871f30')}
              disabled={submitting}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              {tShared('cancel')}
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? t('mapping.saving') : isEdit ? t('mapping.update') : t('mapping.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
