import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { registerPrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';
import { createHomeReads } from '../infrastructure/homeReads';
import { registeredQueues } from './queues';
import { hoursAgoInstant, todayInVietnam } from './vietnamTime';

const MAX_EVENTS = 5;
const idle = { state: 'loading', value: null, error: null };

/**
 * Loads every section of Manager Home on its own: a failure stays in its own section, a section is dropped on session
 * loss, nothing refreshes on a timer (spec FR-006, FR-013, FR-018). `branch` ('all' or a location id) only affects the
 * today strip (FR-008). Returns the state of each section ({ state: loading | ready | unavailable, value, error }).
 */
export function useHomeCards({ branch = 'all' } = {}) {
  const http = useHttp();
  const { status } = useAuth();
  const reads = useMemo(() => createHomeReads(http), [http]);
  const queues = useMemo(() => registeredQueues(), []);
  const [sections, setSections] = useState({});
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
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

  const loaders = useMemo(() => {
    const map = {};
    queues.forEach(queue => { map[`queue:${queue.id}`] = () => reads[queue.count](); });
    map.strip = () => reads.fetchDayStrip(todayInVietnam(), branchRef.current === 'all' ? undefined : branchRef.current);
    map.locations = () => reads.fetchLocations();
    map.revenue = () => reads.fetchMonthlyRevenue();
    map.events = async () => {
      const since = hoursAgoInstant(new Date(), 24);
      const results = await Promise.allSettled([
        reads.fetchOperationsDenials(since), reads.fetchPackageAvailabilityChanges(since), reads.fetchSettlementDenials(since),
      ]);
      const readable = results.filter(result => result.status === 'fulfilled');
      // No source could be read: the section is unavailable. Some failing leaves a note, not a blank.
      if (readable.length === 0) throw results[0].reason;
      const items = readable.flatMap(result => result.value)
        .sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0))
        .slice(0, MAX_EVENTS);
      return { items, partial: readable.length < results.length, settlementIncluded: results[2].status === 'fulfilled' };
    };
    return map;
  }, [queues, reads]);

  const reloadSection = useCallback(id => runSection(id, loaders[id]), [runSection, loaders]);

  const reloadAll = useCallback(async () => {
    await Promise.all(Object.keys(loaders).map(id => reloadSection(id)));
    setLastUpdatedAt(new Date());
  }, [loaders, reloadSection]);

  // Open: load everything once. A different `reads` object means a different session, so load again.
  useEffect(() => {
    if (status !== 'signed-in') return;
    reloadAll();
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
    const drop = () => { sequence.current = {}; setSections({}); setLastUpdatedAt(null); };
    const unregister = registerPrivateStateCleanup(drop);
    if (status !== 'signed-in') drop();
    return unregister;
  }, [status]);

  return {
    queues,
    sections,
    lastUpdatedAt,
    reloadAll,
    reloadSection,
    section: id => sections[id] || idle,
  };
}
