import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';

export function PriceTermsModal({ open, mapping, onClose, onSave }) {
  const [form, setForm] = useState({
    retailAmount: '',
    retailCurrency: 'VND',
    targetNetAmount: '',
    targetNetCurrency: 'VND',
    providerConfirmationEvidenceReference: '',
    effectiveFrom: new Date().toISOString().slice(0, 16),
    effectiveTo: '',
    reason: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  if (!open || !mapping) return null;

  const validate = () => {
    const errs = {};
    if (!form.retailAmount || isNaN(Number(form.retailAmount)) || Number(form.retailAmount) <= 0) {
      errs.retailAmount = 'Retail amount must be a positive number';
    }
    if (!form.targetNetAmount || isNaN(Number(form.targetNetAmount)) || Number(form.targetNetAmount) <= 0) {
      errs.targetNetAmount = 'Target net amount must be a positive number';
    }
    if (!form.providerConfirmationEvidenceReference.trim()) {
      errs.providerConfirmationEvidenceReference = 'Provider confirmation evidence reference is required';
    }
    if (!form.reason.trim()) {
      errs.reason = 'Reason is required';
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
        retailAmount: form.retailAmount,
        retailCurrency: form.retailCurrency,
        targetNetAmount: form.targetNetAmount,
        targetNetCurrency: form.targetNetCurrency,
        providerConfirmationEvidenceReference: form.providerConfirmationEvidenceReference,
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
        reason: form.reason,
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
      <div className="dialog" role="dialog" aria-modal="true" aria-label="Record Confirmed Price Terms">
        <h2>Record Confirmed Price Terms</h2>
        <p className="small muted">
          Record externally confirmed commercial price terms for mapping <strong className="mono">{mapping.mappingId}</strong>.
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
            <Field label="Retail Amount" error={errors.retailAmount || apiError?.fieldError('retailAmount')}>
              <input
                type="text"
                name="retailAmount"
                value={form.retailAmount}
                onChange={(e) => setForm({ ...form, retailAmount: e.target.value })}
                placeholder="e.g. 500000"
                disabled={submitting}
              />
            </Field>
            <Field label="Currency" error={apiError?.fieldError('retailCurrency')}>
              <input
                type="text"
                name="retailCurrency"
                value={form.retailCurrency}
                onChange={(e) => setForm({ ...form, retailCurrency: e.target.value.toUpperCase() })}
                disabled={submitting}
              />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
            <Field label="Target Net Amount (Payout)" error={errors.targetNetAmount || apiError?.fieldError('targetNetAmount')}>
              <input
                type="text"
                name="targetNetAmount"
                value={form.targetNetAmount}
                onChange={(e) => setForm({ ...form, targetNetAmount: e.target.value })}
                placeholder="e.g. 400000"
                disabled={submitting}
              />
            </Field>
            <Field label="Currency" error={apiError?.fieldError('targetNetCurrency')}>
              <input
                type="text"
                name="targetNetCurrency"
                value={form.targetNetCurrency}
                onChange={(e) => setForm({ ...form, targetNetCurrency: e.target.value.toUpperCase() })}
                disabled={submitting}
              />
            </Field>
          </div>

          <Field
            label="Provider Confirmation Evidence Reference"
            hint="Safe reference to external signed terms (no secrets or PII)"
            error={errors.providerConfirmationEvidenceReference || apiError?.fieldError('providerConfirmationEvidenceReference')}
          >
            <input
              type="text"
              name="providerConfirmationEvidenceReference"
              value={form.providerConfirmationEvidenceReference}
              onChange={(e) => setForm({ ...form, providerConfirmationEvidenceReference: e.target.value })}
              placeholder="e.g. CONF-KLOOK-PRICE-20261101"
              disabled={submitting}
            />
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Field label="Effective From" error={apiError?.fieldError('effectiveFrom')}>
              <input
                type="datetime-local"
                name="effectiveFrom"
                value={form.effectiveFrom}
                onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
                disabled={submitting}
              />
            </Field>
            <Field label="Effective To (Optional)" error={apiError?.fieldError('effectiveTo')}>
              <input
                type="datetime-local"
                name="effectiveTo"
                value={form.effectiveTo}
                onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })}
                disabled={submitting}
              />
            </Field>
          </div>

          <Field label="Reason / Commercial Notes" error={errors.reason || apiError?.fieldError('reason')}>
            <textarea
              name="reason"
              value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
              placeholder="Record why these price terms are approved..."
              disabled={submitting}
              rows={3}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? 'Saving...' : 'Record Terms'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

