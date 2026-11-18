import { useCallback, useEffect, useRef, useState } from 'react';
import { registerPrivateStateCleanup } from '../../shared/auth/privateStateCleanup';

const INITIAL = { rows: [], nextCursor: null, loading: false, loadingMore: false, error: null, loaded: false };

/**
 * One keyset-paged audit list (FR-050). `load(cursor)` reads one page; the first page is read again whenever
 * `queryKey` changes. Only the latest read may update the screen, rows already shown never repeat, and a failed
 * "load more" keeps the rows. Whatever was held is dropped on session loss (shared private-state cleanup) and on
 * any failed first read, so a refused caller never keeps audit rows in memory.
 */
export function useAuditFeed({ load, queryKey, enabled }) {
  const [state, setState] = useState({ ...INITIAL, loading: Boolean(enabled) });
  const loadRef = useRef(load);
  const sequence = useRef(0);
  const cursorRef = useRef(null);
  loadRef.current = load;

  const clear = useCallback(() => {
    sequence.current += 1;
    cursorRef.current = null;
    setState(INITIAL);
  }, []);

  const reload = useCallback(async () => {
    const mine = ++sequence.current;
    cursorRef.current = null;
    setState({ ...INITIAL, loading: true });
    try {
      const page = await loadRef.current(null);
      if (mine !== sequence.current) return;
      cursorRef.current = page?.nextCursor || null;
      setState({ ...INITIAL, rows: page?.items || [], nextCursor: cursorRef.current, loaded: true });
    } catch (error) {
      if (mine === sequence.current) setState({ ...INITIAL, error, loaded: true });
    }
  }, []);

  const loadMore = useCallback(async () => {
    const cursor = cursorRef.current;
    if (!cursor) return;
    const mine = ++sequence.current;
    setState(current => ({ ...current, loadingMore: true, error: null }));
    try {
      const page = await loadRef.current(cursor);
      if (mine !== sequence.current) return;
      cursorRef.current = page?.nextCursor || null;
      setState(current => {
        const seen = new Set(current.rows.map(row => row.id));
        const fresh = (page?.items || []).filter(row => !seen.has(row.id));
        return { ...current, rows: [...current.rows, ...fresh], nextCursor: cursorRef.current, loadingMore: false };
      });
    } catch (error) {
      if (mine === sequence.current) setState(current => ({ ...current, loadingMore: false, error }));
    }
  }, []);

  useEffect(() => {
    if (enabled) reload();
    else clear();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryKey, enabled]);

  useEffect(() => registerPrivateStateCleanup(clear), [clear]);

  return { ...state, reload, loadMore, clear };
}
