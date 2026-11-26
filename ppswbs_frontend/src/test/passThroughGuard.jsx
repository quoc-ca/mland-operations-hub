import React from 'react';

/**
 * Stand-in for RouteAccessGuard in page tests that render the real route table to test one page, not who may open it.
 * The allowlists themselves are tested in workshop-operations/test/staffAreaSignIn.test.jsx.
 */
export function RouteAccessGuard({ children }) {
  return <>{children}</>;
}

export default RouteAccessGuard;
