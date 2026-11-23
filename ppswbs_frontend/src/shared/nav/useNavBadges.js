import { useEffect, useMemo, useState } from 'react';
import { useHttp } from '../api/useHttp';
import { useAuth } from '../auth/AuthProvider';
import { registerPrivateStateCleanup } from '../auth/privateStateCleanup';
import { createHomeReads } from '../../features/managerhome/infrastructure/homeReads';
import { readStaffNavCounts } from '../../features/operatorhome/staffWork';

/**
 * Waiting-work counts for the navigation rail (spec 013, navigation decision of 2026-11-22).
 *
 *   useNavBadges(role) -> { status: 'loading' | 'ready', counts }
 *   counts: { [groupId]: number, [`${groupId}/${tabId}`]: number }
 *
 * - Manager: reads the existing count functions once on entry, caches the result, refreshes on an interval.
 * - Staff: workshop tasks (`workshop/sessions`) and open branch orders (`retail/orders`), the two counts of the Staff
 *   home, plus pending change requests of the Staff's branches (`tickets/change-requests`), designs waiting for
 *   review (`customers/design-review`) and unclaimed open consultations (`customers/consultations`, read by walking
 *   the inbox list). Other roles: empty counts, status `ready`.
 *   Not counted for Staff: ticket folios and shop add-ons (no list endpoint, they need a count endpoint first).
 *   A group count is the sum of the counted tabs the Staff has in it, and exists only when all of them were read.
 * - A count that cannot be read is simply absent: no badge, no error, the rail keeps working. A group count exists
 *   only when every count of its tabs was read, so a badge never understates the waiting work.
 */
export const BADGE_CACHE_MS = 30 * 1000;
export const BADGE_REFRESH_MS = 60 * 1000;

// Which tab shows which count. Keys are `groupId/tabId` of navModel.
const MANAGER_COUNTS = [
  ['customers/design-review', 'countDesignReviews'],
  ['tickets/change-requests', 'countChangeRequests'],
  ['tickets/refunds', 'countRefundWaiting'],
  ['collect/cash-close', 'countCashCloseDifferences'],
  ['collect/late-payments', 'countLatePayments'],
  ['ota/reconciliation', 'countOtaNeedingAttention'],
];

const EMPTY = { status: 'ready', counts: {} };
const LOADING = { status: 'loading', counts: {} };

// One remembered result for the signed-in account; dropped on sign-out or session loss.
let cache = { userId: null, at: 0, counts: null };
const dropCache = () => { cache = { userId: null, at: 0, counts: null }; };
registerPrivateStateCleanup(dropCache);

/** For tests: forget the remembered counts. */
export const clearNavBadgeCache = dropCache;

async function readManagerCounts(reads) {
  const settled = await Promise.allSettled(MANAGER_COUNTS.map(([, read]) => reads[read]()));
  const counts = {};
  const failedGroups = new Set();
  settled.forEach((result, index) => {
    const [key] = MANAGER_COUNTS[index];
    const [groupId] = key.split('/');
    if (result.status === 'fulfilled' && Number.isInteger(result.value) && result.value >= 0) counts[key] = result.value;
    else failedGroups.add(groupId);
  });
  const groups = new Set(MANAGER_COUNTS.map(([key]) => key.split('/')[0]));
  groups.forEach(groupId => {
    if (failedGroups.has(groupId)) return;
    counts[groupId] = MANAGER_COUNTS.filter(([key]) => key.startsWith(`${groupId}/`)).reduce((sum, [key]) => sum + counts[key], 0);
  });
  return counts;
}

export function useNavBadges(role) {
  const http = useHttp();
  const { status: authStatus, userId } = useAuth();
  const reads = useMemo(() => createHomeReads(http), [http]);
  const who = userId ?? '__unknown__';
  const fresh = cache.userId === who && cache.counts && Date.now() - cache.at < BADGE_CACHE_MS;
  const counted = role === 'MANAGER' || role === 'STAFF';
  const [state, setState] = useState(() => (counted && fresh ? { status: 'ready', counts: cache.counts } : LOADING));

  useEffect(() => {
    if (authStatus !== 'signed-in') { setState(LOADING); return undefined; }
    if (!counted) { setState(role ? EMPTY : LOADING); return undefined; }

    let active = true;
    const load = async force => {
      if (!force && cache.userId === who && cache.counts && Date.now() - cache.at < BADGE_CACHE_MS) {
        setState({ status: 'ready', counts: cache.counts });
        return;
      }
      const counts = role === 'STAFF' ? await readStaffNavCounts(http) : await readManagerCounts(reads);
      if (!active) return;
      cache = { userId: who, at: Date.now(), counts };
      setState({ status: 'ready', counts });
    };
    load(false);
    const timer = setInterval(() => { if (typeof document === 'undefined' || !document.hidden) load(true); }, BADGE_REFRESH_MS);
    const dropped = registerPrivateStateCleanup(() => { if (active) setState(LOADING); });
    return () => { active = false; clearInterval(timer); dropped(); };
  }, [authStatus, role, counted, who, reads, http]);

  return state;
}
