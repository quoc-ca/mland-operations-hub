import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Cursor paging of 006 lists ({ items, nextCursor }). Changing the criteria (deps) starts again; "more" appends
 * the next page. Only the latest request may update the list. Nothing is cached between screens.
 */
export function useCursorList(load, deps) {
  const [state, setState] = useState({ items: [], nextCursor: null, loading: true, error: null });
  const loadRef = useRef(load);
  loadRef.current = load;
  const sequence = useRef(0);
  const lastCursor = useRef(undefined);

  const fetchPage = useCallback(async (cursor, append) => {
    const mine = ++sequence.current;
    lastCursor.current = { cursor, append };
    setState(current => ({ ...current, loading: true, error: null, items: append ? current.items : [] }));
    try {
      const page = await loadRef.current(cursor);
      if (mine !== sequence.current) return;
      setState(current => ({ items: append ? [...current.items, ...page.items] : page.items, nextCursor: page.nextCursor || null, loading: false, error: null }));
    } catch (error) {
      if (mine === sequence.current) setState(current => ({ ...current, loading: false, error }));
    }
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { fetchPage(undefined, false); }, deps);

  return {
    ...state,
    more: () => fetchPage(state.nextCursor, true),
    retry: () => fetchPage(lastCursor.current?.cursor, lastCursor.current?.append),
    reload: () => fetchPage(undefined, false),
  };
}
