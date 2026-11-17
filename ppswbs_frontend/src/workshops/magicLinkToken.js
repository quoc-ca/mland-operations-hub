/**
 * Reads the Magic Link token from the address fragment (the part after "#", which a browser never sends to a server).
 * The booking emails carry the link as "/workshop-booking/access#<token>" (the token alone). Older forms with a name
 * ("#token=<token>", "#/workshop-booking/access?token=<token>") are still read. Returns null when there is no token.
 */
export function parseMagicLinkToken(hash) {
  const fragment = String(hash || '').replace(/^#/, '').trim();
  if (!fragment) return null;

  const named = fragment.match(/(?:^|[&?#])token=([^&#\s]+)/);
  if (named) return safeDecode(named[1]);

  // The token alone: one opaque piece, no path, query or other fields.
  if (/^[^\s/?&=#]+$/.test(fragment)) return safeDecode(fragment);

  // "#/workshop-booking/access#<token>": the part after the last "#".
  const lastHash = fragment.lastIndexOf('#');
  if (lastHash >= 0) {
    const tail = fragment.slice(lastHash + 1);
    if (/^[^\s/?&=#]+$/.test(tail)) return safeDecode(tail);
  }
  return null;
}

function safeDecode(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
