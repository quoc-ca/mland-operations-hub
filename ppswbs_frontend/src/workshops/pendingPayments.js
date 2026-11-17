import { useEffect, useMemo, useState } from 'react';
import { listMemberBookingGroups } from '../api/workshops';

/**
 * The Member's booking groups that still wait for payment inside their payment deadline (003.1 FR-058). The only
 * source is the Member's own list of groups (FR-046): it is read when the header loads and at most once every
 * 60 seconds afterwards, and read again at once when a screen says the list changed (a group was just held,
 * released or refreshed). The 2-minute buffer of FR-032 is never used here: only `paymentDeadlineAt` counts.
 */
export const PENDING_PAYMENT_READ_MS = 60 * 1000;

const cache = { key: null, readAt: 0, groups: [] };
const listeners = new Set();

/** Tells every header that the Member's groups changed, so the next render reads them again. */
export function invalidatePendingPayments() {
  cache.readAt = 0;
  listeners.forEach(listener => { try { listener(); } catch { /* one broken listener must not stop the others */ } });
}

export function pendingGroupsOf(groups, now = Date.now()) {
  return (Array.isArray(groups) ? groups : [])
    .filter(group => group?.groupStatus === 'PENDING_PAYMENT')
    .map(group => ({ groupCode: group.groupCode, deadline: Date.parse(group.paymentDeadlineAt) }))
    .filter(item => item.groupCode && Number.isFinite(item.deadline) && item.deadline > now)
    .sort((left, right) => left.deadline - right.deadline);
}

/** `{ count, nearest, secondsLeft }` for the header; `count` is 0 when nothing waits for payment. */
export function usePendingPayments({ enabled, userKey, getIdToken }) {
  const [groups, setGroups] = useState(() => (cache.key === userKey ? cache.groups : []));
  const [now, setNow] = useState(() => Date.now());
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!enabled) { setGroups([]); return undefined; }
    let active = true;
    const read = async force => {
      if (!force && cache.key === userKey && Date.now() - cache.readAt < PENDING_PAYMENT_READ_MS) {
        setGroups(cache.groups);
        return;
      }
      try {
        const token = await getIdToken();
        if (!token) return;
        const result = await listMemberBookingGroups({ authorization: `Bearer ${token}` });
        cache.key = userKey;
        cache.readAt = Date.now();
        cache.groups = Array.isArray(result) ? result : [];
        if (active) setGroups(cache.groups);
      } catch {
        // The notice is a convenience: a failed read keeps what was last known and tries again at the next tick.
      }
    };
    read(false);
    const timer = setInterval(() => read(true), PENDING_PAYMENT_READ_MS);
    const onInvalidate = () => { read(true); setVersion(value => value + 1); };
    listeners.add(onInvalidate);
    return () => { active = false; clearInterval(timer); listeners.delete(onInvalidate); };
  }, [enabled, userKey, getIdToken]);

  const pending = pendingGroupsOf(groups, now);
  const hasPending = pending.length > 0;
  useEffect(() => {
    if (!hasPending) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, [hasPending]);

  return useMemo(() => ({
    count: pending.length,
    nearest: pending[0]?.groupCode || null,
    secondsLeft: pending[0] ? Math.max(0, Math.floor((pending[0].deadline - now) / 1000)) : null,
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [groups, now, version]);
}
