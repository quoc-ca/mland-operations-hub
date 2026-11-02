import React from 'react';
import { Link, useLocation } from 'react-router';

/** Path of 001's sign-in page (router `/login`) carrying where to come back to. */
export function signInPath(returnTo) {
  return returnTo ? `/login?returnTo=${encodeURIComponent(returnTo)}` : '/login';
}

/**
 * Link to 001's sign-in page. It carries the current page (or `returnTo`) so the page can come back after signing in;
 * whether it does is up to 001's sign-in page.
 */
export function SignInLink({ returnTo, className = 'button primary', children }) {
  const location = useLocation();
  return <Link className={className} to={signInPath(returnTo ?? `${location.pathname}${location.search}`)}>{children}</Link>;
}
