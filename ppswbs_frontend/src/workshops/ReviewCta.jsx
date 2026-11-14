import React from 'react';

const norm = value => String(value ?? '').trim().toLowerCase();

/**
 * The eligible review source (Feature-011) for ONE ticket booking, or undefined. Eligibility comes only from the
 * backend list (GET /reviews/eligible-sources). A source is one ticket (its `sourceReference`, the opaque
 * `ticketReference` the owning Member's group projection carries, FR-049) attending one session (its target), so it
 * belongs to this ticket only when both match: several groups can hold tickets for the same session, and only the
 * ticket that was checked in may be reviewed. Nothing is inferred from attendance or group status.
 */
export function reviewSourceFor(ticket, sources) {
  if (!ticket || !Array.isArray(sources) || sources.length === 0) return undefined;
  const sessions = new Set((ticket.sessions || []).map(session => norm(session.sessionReference)).filter(Boolean));
  const reference = norm(ticket.ticketReference);
  if (sessions.size === 0 || !reference) return undefined;
  return sources.find(source => source?.eligible !== false
    && source?.targetType === 'WORKSHOP_SESSION'
    && sessions.has(norm(source.targetReference))
    && norm(source.sourceReference) === reference);
}

/**
 * "Write a review" for a workshop session this ticket attended. The review target is the session, never the branch. A
 * confirmed ticket that has not attended yet shows the same action greyed out; no source identifier, contact or
 * transaction detail is shown.
 */
export function ReviewCta({ ticket, sources, onReview }) {
  const source = reviewSourceFor(ticket, sources);
  if (source) {
    return (
      <button
        type="button"
        className="button primary small"
        onClick={() => onReview(source)}
        aria-label={`Viết đánh giá cho vé ${ticket.ticketCode}`}
      >
        Viết đánh giá
      </button>
    );
  }
  const notAttendedYet = ticket?.ticketStatus === 'CONFIRMED' && !ticket.attendanceStatus;
  if (!notAttendedYet) return null;
  return (
    <button
      type="button"
      className="button outline small"
      disabled
      title="Bạn có thể đánh giá ca workshop sau khi đã tham dự."
      aria-label={`Đánh giá vé ${ticket.ticketCode} sau khi tham dự`}
    >
      Đánh giá sau khi tham dự
    </button>
  );
}
