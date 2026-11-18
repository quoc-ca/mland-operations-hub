import { useEffect, useRef } from 'react';

const GROUP_POLL_MS = 20000;

/**
 * Reads the shown group again every GROUP_POLL_MS while the tab is visible, and right away when the tab becomes visible
 * again, until the group is final (expired or cancelled). It replaces the manual "Cập nhật trạng thái" button on the
 * booking and cart result screens, like the payment-return and lookup pages already do. Renders nothing.
 */
export function GroupAutoRefresh({ onRefresh, groupStatus }) {
  const refreshRef = useRef(onRefresh);
  refreshRef.current = onRefresh;
  const running = useRef(false);
  const final = groupStatus === 'EXPIRED' || groupStatus === 'CANCELLED';

  useEffect(() => {
    if (final) return undefined;
    const poll = async () => {
      if (document.visibilityState === 'hidden' || running.current) return;
      running.current = true;
      try { await refreshRef.current?.(); } finally { running.current = false; }
    };
    const timer = setInterval(poll, GROUP_POLL_MS);
    document.addEventListener('visibilitychange', poll);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', poll); };
  }, [final]);

  return null;
}
