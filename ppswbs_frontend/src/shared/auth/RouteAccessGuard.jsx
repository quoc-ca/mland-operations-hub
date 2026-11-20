import React from 'react';
import { Navigate, Outlet } from 'react-router';
import { useCurrentAccount } from '../../features/accountmanagement/hooks/useCurrentAccount';
import { Loading } from '../ui/components';

/**
 * RouteAccessGuard (FR-046 / R-404 / route-access-matrix.md / T074).
 * Enforces role allowlists and active account lifecycle state before protected DOM or API work.
 * - Source: Only existing Account bootstrap (useCurrentAccount).
 * - loading: neutral non-PII loading indicator (aria-busy="true").
 * - unauthenticated: replaces to /login.
 * - error / bootstrap technical failure: keeps current URL, does not mount children,
 *   renders neutral unavailable state with explicit retry button. No cached role or /404.
 * - SUSPENDED or PENDING_SETUP: replaces to /404.
 * - role outside allowedRoles: replaces to /404.
 * - allowed: renders children or <Outlet />.
 */
export function RouteAccessGuard({ allowedRoles, children }) {
  const { account, status, retry, reloadAccount } = useCurrentAccount();

  if (status === 'loading') {
    return (
      <div className="session-loading-state" aria-busy="true">
        <Loading />
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />;
  }

  if (status === 'error' || !account) {
    return (
      <div className="empty-state session-unavailable-state" role="status">
        <p>Không thể kết nối hoặc xác thực thông tin tài khoản. Vui lòng thử lại.</p>
        <button
          type="button"
          className="button secondary account-btn-retry"
          onClick={() => {
            if (typeof retry === 'function') {
              retry();
            } else if (typeof reloadAccount === 'function') {
              reloadAccount();
            }
          }}
        >
          Thử lại
        </button>
      </div>
    );
  }

  // Inactive Account status check: SUSPENDED or PENDING_SETUP accounts are masked to /404
  if (account.status === 'SUSPENDED' || account.status === 'PENDING_SETUP') {
    return <Navigate to="/404" replace />;
  }

  // Exact role allowlist check
  if (allowedRoles && allowedRoles.length > 0) {
    if (!account.role || !allowedRoles.includes(account.role)) {
      return <Navigate to="/404" replace />;
    }
  }

  return children ? <>{children}</> : <Outlet />;
}

export default RouteAccessGuard;
