import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { otaMessages } from '../messages';

export function PriceTermsModal({ open, mapping, onClose, onSave }) {
  const t = useT(otaMessages);
  const tShared = useT(sharedMessages);
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
      errs.retailAmount = t('price.retail.invalid');
    }
    if (!form.targetNetAmount || isNaN(Number(form.targetNetAmount)) || Number(form.targetNetAmount) <= 0) {
      errs.targetNetAmount = t('price.net.invalid');
    }
    if (!form.providerConfirmationEvidenceReference.trim()) {
      errs.providerConfirmationEvidenceReference = t('price.evidence.required');
    }
    if (!form.reason.trim()) {
      errs.reason = t('price.reason.required');
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

  const example = (value) => t('field.example', { value });

  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={t('price.title')}>
        <h2>{t('price.title')}</h2>
        <p className="small muted">
          {t('price.intro')} <strong className="mono">{mapping.mappingId}</strong>.
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
            <Field label={t('price.retailAmount')} error={errors.retailAmount || apiError?.fieldError('retailAmount')}>
              <input
                type="text"
                name="retailAmount"
                value={form.retailAmount}
                onChange={(e) => setForm({ ...form, retailAmount: e.target.value })}
                placeholder={example('500000')}
                disabled={submitting}
              />
            </Field>
            <Field label={t('price.currency')} error={apiError?.fieldError('retailCurrency')}>
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
            <Field label={t('price.netAmount')} error={errors.targetNetAmount || apiError?.fieldError('targetNetAmount')}>
              <input
                type="text"
                name="targetNetAmount"
                value={form.targetNetAmount}
                onChange={(e) => setForm({ ...form, targetNetAmount: e.target.value })}
                placeholder={example('400000')}
                disabled={submitting}
              />
            </Field>
            <Field label={t('price.currency')} error={apiError?.fieldError('targetNetCurrency')}>
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
            label={t('price.evidence')}
            hint={t('price.evidenceHint')}
            error={errors.providerConfirmationEvidenceReference || apiError?.fieldError('providerConfirmationEvidenceReference')}
          >
            <input
              type="text"
              name="providerConfirmationEvidenceReference"
              value={form.providerConfirmationEvidenceReference}
              onChange={(e) => setForm({ ...form, providerConfirmationEvidenceReference: e.target.value })}
              placeholder={example('CONF-KLOOK-PRICE-20261101')}
              disabled={submitting}
            />
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Field label={t('price.effectiveFrom')} error={apiError?.fieldError('effectiveFrom')}>
              <input
                type="datetime-local"
                name="effectiveFrom"
                value={form.effectiveFrom}
                onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
                disabled={submitting}
              />
            </Field>
            <Field label={t('price.effectiveTo')} error={apiError?.fieldError('effectiveTo')}>
              <input
                type="datetime-local"
                name="effectiveTo"
                value={form.effectiveTo}
                onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })}
                disabled={submitting}
              />
            </Field>
          </div>

          <Field label={t('price.reason')} error={errors.reason || apiError?.fieldError('reason')}>
            <textarea
              name="reason"
              value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
              placeholder={t('price.reasonPlaceholder')}
              disabled={submitting}
              rows={3}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              {tShared('cancel')}
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? t('price.saving') : t('price.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
