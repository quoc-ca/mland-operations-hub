import React from 'react';

const norm = value => String(value ?? '').trim().toLowerCase();

/**
 * The eligible review source (Feature-011) for this booking, or undefined. Eligibility comes only from the backend list
 * (GET /reviews/eligible-sources). A source is ONE ticket (its sourceReference) attending ONE session (its target), so it
 * belongs to this booking only when both match: several bookings can hold tickets for the same session, and only the one
 * whose ticket was checked in may be reviewed. Nothing is inferred from attendance or booking status.
 */
export function reviewSourceFor(booking, sources) {
  if (!booking || !Array.isArray(sources) || sources.length === 0) return undefined;
  const sessions = new Set((booking.sessions || []).map(session => norm(session.sessionReference)).filter(Boolean));
  const tickets = new Set((booking.ticketReferences || []).map(norm).filter(Boolean));
  if (sessions.size === 0 || tickets.size === 0) return undefined;
  return sources.find(source => source?.eligible !== false
    && source?.targetType === 'WORKSHOP_SESSION'
    && sessions.has(norm(source.targetReference))
    && tickets.has(norm(source.sourceReference)));
}

/**
 * "Write a review" for a workshop session the Member attended. The review target is the session, never the branch. A
 * booking that is confirmed but not attended yet shows the same action greyed out; no source identifier, contact or
 * transaction detail is shown.
 */
export function ReviewCta({ booking, sources, onReview }) {
  const source = reviewSourceFor(booking, sources);
  if (source) {
    return (
      <button type="button" className="button primary small" onClick={() => onReview(source)}
        aria-label={`Viết đánh giá cho ${booking.bookingCode}`}>
        Viết đánh giá
      </button>
    );
  }
  const notAttendedYet = booking?.bookingStatus === 'CONFIRMED' && !booking.attendanceStatus;
  if (!notAttendedYet) return null;
  return (
    <button type="button" className="button outline small" disabled
      title="Bạn có thể đánh giá ca workshop sau khi đã tham dự." aria-label={`Đánh giá ${booking.bookingCode} sau khi tham dự`}>
      Đánh giá sau khi tham dự
    </button>
  );
}
