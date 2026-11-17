import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';
import { ALL_READINESS_CHECKS } from '../api/otaConnectivityApi';

export function BookingEnableModal({ open, mapping, onClose, onSave }) {
  const [readinessRecordReference, setReadinessRecordReference] = useState('');
  const [reason, setReason] = useState('');
  const [checks, setChecks] = useState(() =>
    ALL_READINESS_CHECKS.map((check) => ({
      check,
      outcome: 'VERIFIED',
      evidenceReference: `EVID-${check}-VERIFIED`,
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
      errs.readinessRecordReference = 'Readiness record reference is required';
    }
    if (!reason.trim()) {
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
      <div className="dialog" role="dialog" aria-modal="true" aria-label="Admin Booking Enablement">
        <h2>Admin: Enable Booking for Mapping</h2>
        <p className="small muted">
          Synchronously enable bookings for <strong className="mono">{mapping.mappingId}</strong> ({mapping.provider}).
          Requires attestation that all 12 provider readiness gates have been verified.
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field
            label="Readiness Record Reference"
            hint="Controlled document reference for audited provider checklist"
            error={errors.readinessRecordReference || apiError?.fieldError('readinessRecordReference')}
          >
            <input
              type="text"
              name="readinessRecordReference"
              value={readinessRecordReference}
              onChange={(e) => setReadinessRecordReference(e.target.value)}
              placeholder="e.g. DOC-KLOOK-READINESS-REC-20261101"
              disabled={submitting}
            />
          </Field>

          <Field label="Attestation Reason" error={errors.reason || apiError?.fieldError('reason')}>
            <input
              type="text"
              name="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Reviewed and verified all 12 operational readiness checks"
              disabled={submitting}
            />
          </Field>

          <label style={{ fontWeight: 700, fontSize: '13px', marginTop: '12px', display: 'block' }}>
            Readiness Checklist (12 Gates)
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
                  <option value="VERIFIED">VERIFIED</option>
                  <option value="FAILED">FAILED</option>
                  <option value="MISSING">MISSING</option>
                </select>
              </div>
            ))}
          </div>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? 'Enabling...' : 'Enable Booking'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

