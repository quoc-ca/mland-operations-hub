import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import { formatDateTime, formatStars, LIMITS, validateReason, validateReply } from '../rules';

export default function ModerationDetailPage() {
  const t = useT(reviewManagementMessages);
  const { reviewId } = useParams();
  const api = useReviewManagementApi();

  const resource = useResource(() => api.getReview(reviewId), [reviewId]);
  const command = useCommand();
  const replyCommand = useCommand();

  const [reason, setReason] = useState('');
  const [reply, setReply] = useState('');
  const [clientError, setClientError] = useState('');
  const [replyError, setReplyError] = useState('');
  const [success, setSuccess] = useState('');

  if (resource.loading) {
    return (
      <div className="review-container">
        <Loading />
      </div>
    );
  }

  if (resource.error) {
    return (
      <div className="review-container">
        <ErrorNotice
          error={resource.error}
          messages={reviewManagementMessages}
          onReload={resource.reload}
        />
      </div>
    );
  }

  const review = resource.data;

  async function handleVisibility(status) {
    const reasonErr = validateReason(reason);
    if (reasonErr) {
      setClientError(t(reasonErr));
      return;
    }
    setClientError('');

    const result = await command.run(key =>
      api.changeVisibility(key, reviewId, { status, reason: reason.trim() }, review.version)
    );

    if (result.ok) {
      setSuccess(t('successModerated'));
      resource.reload();
    }
  }

  async function handleSaveReply(event) {
    event.preventDefault();
    const replyErr = validateReply(reply);
    if (replyErr) {
      setReplyError(t(replyErr));
      return;
    }
    setReplyError('');

    const result = await replyCommand.run(key =>
      review.reply
        ? api.editReply(key, reviewId, { bodyText: reply.trim() }, review.reply.version)
        : api.createReply(key, reviewId, { bodyText: reply.trim() })
    );

    if (result.ok) {
      setSuccess(t('successReply'));
      resource.reload();
    }
  }

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('moderationTitle')}</h1>
        <p className="muted">
          {review.maskedDisplayName} · {review.status}
        </p>
      </header>

      <SuccessNotice>{success}</SuccessNotice>

      <div className="review-panel">
        <div className="review-card-top" style={{ marginBottom: '0.75rem' }}>
          <span className="review-stars">
            {formatStars(review.rating)}
          </span>
          <span className={`badge-tag ${review.status === 'HIDDEN' ? 'badge-status-hidden' : review.status === 'REMOVED' ? 'badge-status-removed' : 'badge-status'}`}>
            {review.status}
          </span>
        </div>

        <p className="review-body" style={{ fontSize: '1.05rem', margin: '0.75rem 0' }}>
          {review.bodyText}
        </p>

        <div className="review-meta" style={{ marginBottom: '1.25rem' }}>
          <span>{review.targetType} ({review.targetReference})</span>
          <span>{formatDateTime(review.createdAt)}</span>
        </div>

        <Field
          label={t('reason')}
          hint={`${reason.length}/${LIMITS.reasonMax}`}
        >
          <textarea
            value={reason}
            onChange={e => setReason(e.target.value)}
            placeholder={t('reasonPlaceholder')}
            rows={3}
            disabled={command.pending}
          />
        </Field>

        {clientError && (
          <div className="notice error" role="alert">
            {clientError}
          </div>
        )}

        <ErrorNotice
          error={command.error}
          messages={reviewManagementMessages}
          onRetry={command.retry}
        />

        <div className="actions">
          {review.status !== 'HIDDEN' && (
            <button
              type="button"
              className="button outline"
              onClick={() => handleVisibility('HIDDEN')}
              disabled={command.pending}
            >
              {t('hide')}
            </button>
          )}

          {review.status === 'HIDDEN' && (
            <button
              type="button"
              className="button outline"
              onClick={() => handleVisibility('PUBLIC')}
              disabled={command.pending}
            >
              {t('restore')}
            </button>
          )}

          {review.status !== 'REMOVED' && (
            <button
              type="button"
              className="button danger"
              onClick={() => handleVisibility('REMOVED')}
              disabled={command.pending}
            >
              {t('remove')}
            </button>
          )}
        </div>
      </div>

      <form className="review-panel" onSubmit={handleSaveReply}>
        <h2>{t('replyTitle')}</h2>

        {review.reply && (
          <div className="manager-reply-box" style={{ marginBottom: '1rem' }}>
            <div className="manager-reply-header">
              <span>{t('managerReplyBadge')} (v{review.reply.version})</span>
              <span>{formatDateTime(review.reply.createdAt)}</span>
            </div>
            <div className="manager-reply-body">{review.reply.bodyText}</div>
          </div>
        )}

        <Field
          label={t('reply')}
          hint={`${reply.length}/${LIMITS.replyMax.toLocaleString()}`}
        >
          <textarea
            value={reply}
            onChange={e => setReply(e.target.value)}
            placeholder={t('replyPlaceholder')}
            rows={5}
            disabled={replyCommand.pending}
          />
        </Field>

        {replyError && (
          <div className="notice error" role="alert">
            {replyError}
          </div>
        )}

        <ErrorNotice
          error={replyCommand.error}
          messages={reviewManagementMessages}
          onRetry={replyCommand.retry}
        />

        <button
          type="submit"
          className="button primary"
          disabled={replyCommand.pending}
        >
          {t('saveReply')}
        </button>
      </form>
    </div>
  );
}
