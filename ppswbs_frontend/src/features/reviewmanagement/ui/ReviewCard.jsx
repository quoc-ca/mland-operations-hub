import React from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';
import { formatDateTime, formatStars } from '../rules';

export default function ReviewCard({ review, onOpen }) {
  const t = useT(reviewManagementMessages);
  if (!review) return null;

  const statusClass = review.status === 'HIDDEN'
    ? 'badge-status-hidden'
    : review.status === 'REMOVED'
      ? 'badge-status-removed'
      : 'badge-status';

  return (
    <article className="review-card">
      <div className="review-card-top">
        <span className="review-stars" aria-label={`${review.rating} / 5 stars`}>
          {formatStars(review.rating)}
        </span>
        <span className={`badge-tag ${statusClass}`}>
          {review.status || 'PUBLIC'}
        </span>
      </div>

      <div className="review-author">
        {review.maskedDisplayName || t('customerBadge')}
      </div>

      {review.bodyText && (
        <div className="review-body">{review.bodyText}</div>
      )}

      {review.reply && (
        <div className="manager-reply-box">
          <div className="manager-reply-header">
            <span>{t('managerReplyBadge')}</span>
            <span>{formatDateTime(review.reply.createdAt)}</span>
          </div>
          <div className="manager-reply-body">{review.reply.bodyText}</div>
        </div>
      )}

      {review.packageName && (
        <div className="review-package">
          {t('packageLabel')}: <strong>{review.packageName}</strong>
        </div>
      )}

      <div className="review-meta">
        <span className="badge-tag badge-verified">
          {review.targetType === 'WORKSHOP_SESSION'
            ? t('sourceWorkshop')
            : review.targetType === 'RETAIL_PRODUCT'
              ? t('sourceRetail')
              : (review.targetType || t('verified'))}
        </span>
        <span>{formatDateTime(review.createdAt)}</span>
      </div>

      {onOpen && (
        <button
          type="button"
          className="button outline small"
          onClick={() => onOpen(review)}
        >
          {t('open')}
        </button>
      )}
    </article>
  );
}
