import React, { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { useRole } from '../../shared/auth/useRole';
import { Loading, useCommand } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { decideLatePaymentReview, listLatePaymentReviews } from '../../api/workshops';
import '../../shared/ui/workspace.css';
import '../workshop-operations.css';

// Same standardized reasons as the session page recovery panel (003.1 FR-040a decisions need a reason).
const REASONS = [
  { code: 'PROVIDER_CONFIRMED_LATE_DEPOSIT' },
  { code: 'CUSTOMER_PRESENTED_RECEIPT' },
  { code: 'DISPUTE_ESCALATION_FINANCE' },
  { code: 'OTHER_REQUIRES_NOTE' },
];

/** Manager queue of late payments waiting for a decision (003.1 FR-040a); the backend decides who may read or decide. */
export default function LatePaymentReviewsPage() {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();
  const conditionLabel = useEnumLabel(messages, 'lateCondition');
  const { status, user } = useAuth();
  const role = useRole();
  const command = useCommand();
  const authorization = user?.token || user?.accessToken;
  const [reviews, setReviews] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [reasonCode, setReasonCode] = useState(REASONS[0].code);
  const [note, setNote] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setReviews(await listLatePaymentReviews({ status: 'PENDING_MANAGER', authorization }));
    } catch (e) {
      setReviews(null);
      setError(e?.detail || e?.message || t('late.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [authorization]);

  useEffect(() => {
    if (status === 'signed-in' && role === 'MANAGER') load();
  }, [status, role, load]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return <OperationsAuthWorkspace topline={t('topline.operations')} message={t('late.signIn')} />;
  }

  const noteMissing = reasonCode === 'OTHER_REQUIRES_NOTE' && !note.trim();
  const fullReason = () => (reasonCode === 'OTHER_REQUIRES_NOTE' ? note.trim()
    : (note.trim() ? `${reasonCode}: ${note.trim()}` : reasonCode));

  async function decide(review, decision) {
    setMessage('');
    const result = await command.run(key => decideLatePaymentReview(review.groupCode, review.reviewId, {
      decision, reason: fullReason(), authorization, idempotencyKey: key,
    }));
    if (result.ok) {
      setMessage(decision === 'CONFIRM' ? t('late.confirmed') : t('late.refundOpened'));
      load();
    }
  }

  return <div className="wsp-workspace"><main className="wsp-main">
    <div className="wsp-topline">{t('topline.latePayments')} <span>· {t('authorized')}</span></div>
    <header className="wsp-page-heading">
      <div>
        <p className="muted">
          {t('late.intro')}
        </p>
      </div>
    </header>
    <article className="wsp-panel">
      <div className="wsp-form-grid" style={{ marginBottom: '12px' }}>
        <label className="full-width">
          {t('late.reasonLabel')}
          <select value={reasonCode} onChange={e => setReasonCode(e.target.value)}>
            {REASONS.map(r => <option key={r.code} value={r.code}>{r.code} — {t(`late.reason.${r.code}`)}</option>)}
          </select>
        </label>
        <label className="full-width">
          {t('late.noteLabel', { mark: reasonCode === 'OTHER_REQUIRES_NOTE' ? '*' : t('late.noteOptional') })}
          <textarea value={note} onChange={e => setNote(e.target.value)} />
        </label>
      </div>
      <div className="ops-actions">
        <button className="button outline" disabled={loading} onClick={load}>
          {loading ? ts('loading') : t('late.reload')}
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {error && <p role="alert" style={{ color: '#b00' }}>{error}</p>}
      {Array.isArray(reviews) && reviews.length === 0 && (
        <p className="muted">{t('late.empty')}</p>
      )}
      {Array.isArray(reviews) && reviews.length > 0 && (
        <ul style={{ listStyle: 'none', margin: '12px 0 0', padding: 0, display: 'grid', gap: '10px' }}>
          {reviews.map(review => (
            <li key={review.reviewId} style={{ border: '1px solid #dcdfe6', padding: '12px' }}>
              <strong>{review.groupCode}</strong>
              <p className="muted" style={{ margin: '4px 0', fontSize: '13px' }}>
                {t('late.amount', { amount: fmt.money(review.amount) })} · {t('late.failedConditions', { list: (review.failedConditions || []).map(conditionLabel).join(', ') || '—' })}
                {review.resultDueAt ? ` · ${t('late.due', { time: fmt.dateTime(review.resultDueAt) })}` : ''}
              </p>
              <div className="ops-actions">
                <button className="button outline" disabled={command.pending || !review.confirmAllowed || noteMissing}
                  onClick={() => decide(review, 'CONFIRM')}>{t('late.confirm')}</button>
                <button className="button warning" disabled={command.pending || noteMissing}
                  onClick={() => decide(review, 'REFUND')}>{t('late.refund')}</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  </main></div>;
}
