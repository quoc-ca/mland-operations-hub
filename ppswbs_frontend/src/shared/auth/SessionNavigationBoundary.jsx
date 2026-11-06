import React, { useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router';
import { useAuth } from './AuthProvider';
import { executePrivateStateCleanup } from './privateStateCleanup';

/**
 * Router-level session navigation boundary (FR-041 / spec-general.md §2 / T070).
 * It is the ONLY client unit permitted to perform terminal session navigation.
 * - USER_SIGN_OUT: executes registered private-state cleanups, navigates to / with replace and 'Đã đăng xuất'.
 * - SESSION_LOST: executes registered private-state cleanups once, navigates to / with replace and 'Phiên đã kết thúc, vui lòng đăng nhập lại'.
 * - ACCESS_DENIED (403): retains session, does NOT clear drafts, navigates to / with replace and generic safe notice.
 */
export function SessionNavigationBoundary() {
  const navigate = useNavigate();
  const { subscribeOutcome } = useAuth();

  useEffect(() => {
    if (!subscribeOutcome) return;

    return subscribeOutcome(outcome => {
      if (!outcome || !outcome.type) return;

      if (outcome.type === 'USER_SIGN_OUT') {
        executePrivateStateCleanup();
        navigate('/', {
          replace: true,
          state: { sessionNotice: 'Đã đăng xuất' },
        });
      } else if (outcome.type === 'SESSION_LOST') {
        executePrivateStateCleanup();
        navigate('/', {
          replace: true,
          state: { sessionNotice: 'Phiên đã kết thúc, vui lòng đăng nhập lại' },
        });
      } else if (outcome.type === 'ACCESS_DENIED') {
        // Safe generic notice; no role, lifecycle, or Workshop-scope detail is disclosed
        navigate('/', {
          replace: true,
          state: { sessionNotice: 'Bạn không có quyền truy cập chức năng này.' },
        });
      }
    });
  }, [navigate, subscribeOutcome]);

  return <Outlet />;
}

export default SessionNavigationBoundary;

