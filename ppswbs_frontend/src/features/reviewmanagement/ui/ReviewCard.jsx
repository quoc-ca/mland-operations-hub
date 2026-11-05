import React from 'react';

export default function ReviewCard({ review, onOpen }) {
  return (
    <article className="review-card">
      <div className="review-card-top">
        <span className="review-stars" aria-label={`${review.rating} out of 5 stars`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
        <span className="badge-tag badge-status">{review.status || 'PUBLIC'}</span>
      </div>
      <strong>{review.maskedDisplayName || 'Mland customer'}</strong>
      {review.bodyText && <p>{review.bodyText}</p>}
      <small className="muted">{review.targetType || 'Verified source'}</small>
      {onOpen && <button type="button" className="button outline small" onClick={() => onOpen(review)}>View</button>}
    </article>
  );
}
