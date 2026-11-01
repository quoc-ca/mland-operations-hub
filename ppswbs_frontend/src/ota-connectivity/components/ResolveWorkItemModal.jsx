import React, { useState } from 'react';
import { Field, ErrorNotice } from '../../shared/ui/components';

export function ResolveWorkItemModal({ open, workItem, onClose, onResolve }) {
  const [resolution, setResolution] = useState('RESYNC_REQUESTED');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  if (!open || !workItem) return null;

  const validate = () => {
    const errs = {};
    if (!reason.trim()) {
      errs.reason = 'Reason is required';
    } else if (reason.trim().length > 1000) {
      errs.reason = 'Reason must not exceed 1000 characters';
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
      <div className="dialog" role="dialog" aria-modal="true" aria-label="Resolve Reconciliation Work Item">
        <h2>Resolve Reconciliation Item</h2>
        <p className="small muted">
          Record an auditable resolution decision for Work Item <strong className="mono">{workItem.workItemId}</strong>
          {workItem.mappingId && <> (Mapping: <span className="mono">{workItem.mappingId}</span>)</>}.
        </p>

        <ErrorNotice error={apiError} />

        <form onSubmit={handleSubmit}>
          <Field label="Resolution Action" error={apiError?.fieldError('resolution')}>
            <select
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              disabled={submitting}
            >
              <option value="RESYNC_REQUESTED">RESYNC_REQUESTED (Queue Authoritative Operations Resync)</option>
              <option value="ACCEPTED">ACCEPTED (Accept Reconciliation Outcome)</option>
              <option value="REJECTED">REJECTED (Reject / Close Item)</option>
              <option value="ESCALATED">ESCALATED (Escalate for Merchant Investigation)</option>
            </select>
          </Field>

          <Field label="Decision Reason" hint="Provide clear operational reason for audit record" error={errors.reason || apiError?.fieldError('reason')}>
            <textarea
              name="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Requested authoritative resync to reconcile gap version..."
              rows={4}
              disabled={submitting}
            />
          </Field>

          <div className="dialog-actions">
            <button type="button" className="button outline" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? 'Resolving...' : 'Record Decision'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

