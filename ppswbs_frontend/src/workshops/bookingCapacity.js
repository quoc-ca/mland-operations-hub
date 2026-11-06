/**
 * Calculate the customer-facing ticket limit for the current selection.
 *
 * The booking page keeps the input at one disabled ticket until a session is
 * selected. Once sessions are selected, one booking must fit every selected
 * session, so the lowest remaining capacity becomes the effective upper bound
 * (still subject to the published Package policy). The backend remains
 * authoritative and re-checks capacity atomically.
 */
export function getEffectiveParticipantMax(pkg, sessions, selectedSessionRefs) {
  const packageMax = Number(pkg?.maxParticipants);
  const safePackageMax = Number.isFinite(packageMax) && packageMax > 0 ? packageMax : 1;
  const selectedRefs = new Set(selectedSessionRefs || []);
  const selectedSessions = (sessions || []).filter(session => selectedRefs.has(session.sessionReference));

  if (selectedSessions.length === 0) {
    return safePackageMax;
  }

  return selectedSessions.reduce((currentMax, session) => {
    const remaining = Number(session.remainingCapacity);
    const safeRemaining = Number.isFinite(remaining) && remaining >= 0 ? remaining : 0;
    return Math.min(currentMax, safeRemaining);
  }, safePackageMax);
}
