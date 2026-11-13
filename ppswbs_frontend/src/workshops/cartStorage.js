/**
 * The unquoted workshop cart (003.1 FR-045, decided 2026-11-13): it lives in this browser, per device, and is cleared
 * once a booking group is created. There is no server cart resource, so nothing here is authoritative: a line only
 * records what the customer picked, and every price, discount and collection comes from
 * `POST /api/v1/workshop-cart-quotes` when the cart is quoted.
 */

const STORAGE_KEY = 'mland.workshop.cart.v1';

function read() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isUsableLine) : [];
  } catch {
    // Private mode, blocked storage or damaged content: the cart is simply empty.
    return [];
  }
}

function write(lines) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
  } catch {
    // A cart that cannot be stored is kept only for this screen; booking still works through "Đặt ngay".
  }
  notify(lines);
}

function isUsableLine(line) {
  return Boolean(line)
    && line.packageId !== undefined && line.packageId !== null
    && Array.isArray(line.sessionReferences) && line.sessionReferences.length > 0
    && Number(line.participantCount) >= 1;
}

const listeners = new Set();

function notify(lines) {
  listeners.forEach(listener => {
    try { listener(lines); } catch { /* a broken listener must not stop the others */ }
  });
}

export function subscribeToCart(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function readCart() {
  return read();
}

export function cartLineCount() {
  return read().length;
}

/** Adds one line (a package with its complete required-session set and a participant count) and returns the new cart. */
export function addCartLine(line) {
  if (!isUsableLine(line)) throw new Error('CART_LINE_INCOMPLETE');
  const lines = read();
  const next = [...lines, {
    lineId: (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `line-${Date.now()}-${lines.length}`),
    packageId: Number(line.packageId),
    packageName: line.packageName || '',
    pricePerParticipantVnd: Number(line.pricePerParticipantVnd || 0),
    locationId: line.locationId ?? null,
    locationName: line.locationName || '',
    sessionReferences: [...line.sessionReferences],
    sessionLabels: Array.isArray(line.sessionLabels) ? [...line.sessionLabels] : [],
    participantCount: Number(line.participantCount),
    minParticipants: Number(line.minParticipants || 1),
    maxParticipants: Number(line.maxParticipants || 0) || null,
  }];
  write(next);
  return next;
}

export function updateCartLineParticipants(lineId, participantCount) {
  const next = read().map(line => (line.lineId === lineId
    ? { ...line, participantCount: Math.max(1, Number(participantCount) || 1) }
    : line));
  write(next);
  return next;
}

export function removeCartLine(lineId) {
  const next = read().filter(line => line.lineId !== lineId);
  write(next);
  return next;
}

/** Called once a booking group exists: the cart it came from is gone (FR-045). */
export function clearCart() {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch { /* nothing to clear */ }
  notify([]);
}

/** The quote request body of the stored cart; the server allocates benefits and prices it. */
export function cartQuoteLines(lines) {
  return (lines || []).map(line => ({
    packageId: line.packageId,
    sessionReferences: line.sessionReferences,
    participantCount: line.participantCount,
  }));
}
