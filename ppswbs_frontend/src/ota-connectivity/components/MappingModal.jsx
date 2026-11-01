import React, { useState, useEffect } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';

export function MappingModal({ open, channelId, mapping, onClose, onSave }) {
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
    if (!form.providerProductReference.trim()) errs.providerProductReference = 'Provider Product Reference is required';
    if (!form.providerOptionReference.trim()) errs.providerOptionReference = 'Provider Option Reference is required';
    if (!form.workshopOfferReference.trim()) errs.workshopOfferReference = 'Workshop Offer Reference is required';
    if (!form.sessionReference.trim()) errs.sessionReference = 'Session Reference is required';
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

  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={isEdit ? 'Edit Channel Mapping' : 'Create Channel Mapping'}>
        <h2>{isEdit ? 'Edit Channel Mapping' : 'Create Channel Mapping'}</h2>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field label="Provider Product Reference" error={errors.providerProductReference || apiError?.fieldError('providerProductReference')}>
            <input
              type="text"
              name="providerProductReference"
              value={form.providerProductReference}
              onChange={(e) => setForm({ ...form, providerProductReference: e.target.value })}
              placeholder="e.g. KLOOK-PROD-BASICRING"
              disabled={submitting}
            />
          </Field>

          <Field label="Provider Option Reference" error={errors.providerOptionReference || apiError?.fieldError('providerOptionReference')}>
            <input
              type="text"
              name="providerOptionReference"
              value={form.providerOptionReference}
              onChange={(e) => setForm({ ...form, providerOptionReference: e.target.value })}
              placeholder="e.g. KLOOK-OPT-MORNING"
              disabled={submitting}
            />
          </Field>

          <Field label="Workshop Offer Reference" error={errors.workshopOfferReference || apiError?.fieldError('workshopOfferReference')}>
            <input
              type="text"
              name="workshopOfferReference"
              value={form.workshopOfferReference}
              onChange={(e) => setForm({ ...form, workshopOfferReference: e.target.value })}
              placeholder="e.g. offer-basic-ring-standard"
              disabled={submitting}
            />
          </Field>

          <Field label="Session Reference" error={errors.sessionReference || apiError?.fieldError('sessionReference')}>
            <input
              type="text"
              name="sessionReference"
              value={form.sessionReference}
              onChange={(e) => setForm({ ...form, sessionReference: e.target.value })}
              placeholder="e.g. sess-d1f8a85e-7a42-4b2a-874e-5a52e9871f30"
              disabled={submitting}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? 'Saving...' : isEdit ? 'Update Mapping' : 'Create Mapping'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
