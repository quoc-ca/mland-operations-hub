/**
 * The choice a customer makes on the package detail page (branch, the required sessions, number of tickets) and carries
 * to the checkout screen of "Đặt ngay" (003.1 FR-045). It travels in the URL so a refresh or the back button keeps it;
 * it is only a selection, never a price or a quote: the checkout asks the server for the quote.
 */

export function buildCheckoutPath(packageId, { locationId, sessionReferences, participantCount, from, to }) {
  const params = new URLSearchParams();
  if (locationId) params.set('locationId', String(locationId));
  (sessionReferences || []).forEach(reference => params.append('session', reference));
  if (participantCount) params.set('participants', String(participantCount));
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return `/workshops/${packageId}/book?${params.toString()}`;
}

export function parseSelection(searchParams) {
  const locationId = Number(searchParams.get('locationId')) || null;
  const participants = Number(searchParams.get('participants'));
  return {
    locationId,
    sessionReferences: searchParams.getAll('session').filter(Boolean),
    participantCount: Number.isFinite(participants) && participants >= 1 ? Math.floor(participants) : 1,
    from: searchParams.get('from') || null,
    to: searchParams.get('to') || null,
  };
}

/** The first reason the current choice cannot be booked, or null. The server still decides when it is quoted. */
export function selectionProblem({ requiredSessions, selectedCount, effectiveMax, minParticipants, participantCount }) {
  if (selectedCount < requiredSessions) return `Vui lòng chọn đủ ${requiredSessions} ca workshop.`;
  if (effectiveMax < minParticipants) return 'Các ca đã chọn không còn đủ chỗ cho số vé tối thiểu của gói. Vui lòng chọn ca khác.';
  if (participantCount < minParticipants) return `Số vé tối thiểu là ${minParticipants}.`;
  if (participantCount > effectiveMax) return `Các ca đã chọn chỉ còn đủ ${effectiveMax} vé.`;
  return null;
}
