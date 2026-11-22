import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { registerPrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';
import { createHomeReads } from '../infrastructure/homeReads';
import { hoursAgoInstant, todayInVietnam } from './vietnamTime';

const MAX_EVENTS = 5;
const idle = { state: 'loading', value: null, error: null };

/**
 * Loads every block of the Manager dashboard's operations part on its own: a failure stays in its own block, a block is
 * dropped on session loss, nothing refreshes on a timer (spec 013 FR-006, FR-013, FR-018). `branch` ('all' or a
 * location id) only affects the today strip (FR-008). Waiting-work counts are not read here: the navigation rail shows
 * them as badges (`useNavBadges`). Returns the state of each block ({ state: loading | ready | unavailable, value, error }).
 */
export function useHomeCards({ branch = 'all' } = {}) {
  const http = useHttp();
  const { status } = useAuth();
  const reads = useMemo(() => createHomeReads(http), [http]);
  const [sections, setSections] = useState({});
  const sequence = useRef({});
  const branchRef = useRef(branch);
  branchRef.current = branch;

  const patch = useCallback((id, next) => setSections(current => ({ ...current, [id]: next })), []);

  const runSection = useCallback(async (id, load) => {
    const mine = (sequence.current[id] || 0) + 1;
    sequence.current[id] = mine;
    // Keep the previous value on screen while a newer read is in flight.
    setSections(current => ({ ...current, [id]: { ...(current[id] || idle), state: current[id]?.value != null ? 'ready' : 'loading', error: null } }));
    try {
      const value = await load();
      if (sequence.current[id] === mine) patch(id, { state: 'ready', value, error: null });
    } catch (error) {
      if (sequence.current[id] === mine) patch(id, { state: 'unavailable', value: null, error });
    }
  }, [patch]);

  const loaders = useMemo(() => ({
    strip: () => reads.fetchDayStrip(todayInVietnam(), branchRef.current === 'all' ? undefined : branchRef.current),
    locations: () => reads.fetchLocations(),
    events: async () => {
      const since = hoursAgoInstant(new Date(), 24);
      const results = await Promise.allSettled([
        reads.fetchOperationsDenials(since), reads.fetchPackageAvailabilityChanges(since), reads.fetchSettlementDenials(since),
      ]);
      const readable = results.filter(result => result.status === 'fulfilled');
      // No source could be read: the block is unavailable. Some failing leaves a note, not a blank.
      if (readable.length === 0) throw results[0].reason;
      const items = readable.flatMap(result => result.value)
        .sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0))
        .slice(0, MAX_EVENTS);
      return { items, partial: readable.length < results.length, settlementIncluded: results[2].status === 'fulfilled' };
    },
  }), [reads]);

  const reloadSection = useCallback(id => runSection(id, loaders[id]), [runSection, loaders]);

  // Open: load everything once. A different `reads` object means a different session, so load again.
  useEffect(() => {
    if (status !== 'signed-in') return;
    Object.keys(loaders).forEach(id => reloadSection(id));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, loaders]);

  // Another branch: only the today strip is read again.
  const firstBranch = useRef(true);
  useEffect(() => {
    if (firstBranch.current) { firstBranch.current = false; return; }
    if (status === 'signed-in') reloadSection('strip');
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branch]);

  // Session lost or signed out: nothing from this visit stays in memory.
  useEffect(() => {
    const drop = () => { sequence.current = {}; setSections({}); };
    const unregister = registerPrivateStateCleanup(drop);
    if (status !== 'signed-in') drop();
    return unregister;
  }, [status]);

  return { sections, reloadSection, section: id => sections[id] || idle };
}
