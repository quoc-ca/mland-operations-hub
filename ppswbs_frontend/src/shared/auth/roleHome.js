/** Landing page of each role after sign-in; Members keep the general home page, Admin goes to account management, CARRIER to /carrier. */
export function roleHomePath(role) {
  if (role === 'STAFF') return '/staff/home';
  if (role === 'MANAGER') return '/manager/home';
  if (role === 'CARRIER') return '/carrier';
  if (role === 'ADMIN') return '/admin/accounts';
  return '/';
}

/**
 * A `returnTo` is followed only when it is a path on this site: it must start with a single "/" so a protocol-relative
 * or absolute URL can never send the user to another site.
 */
export function safeReturnTo(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null;
  return value;
}

/** Where to go after signing in: CARRIER goes straight to /carrier; others go to returnTo else the role's own home. */
export function afterSignInPath(role, returnTo) {
  if (role === 'CARRIER') return '/carrier';
  return safeReturnTo(returnTo) || roleHomePath(role);
}
