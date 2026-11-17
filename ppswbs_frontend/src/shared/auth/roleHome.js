/** Landing page of each internal role after sign-in; Members and Admins keep the general home page. */
export function roleHomePath(role) {
  if (role === 'STAFF') return '/staff/home';
  if (role === 'MANAGER') return '/manager/home';
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

/** Where to go after signing in: the page the user came from, else the role's own home. */
export function afterSignInPath(role, returnTo) {
  return safeReturnTo(returnTo) || roleHomePath(role);
}
