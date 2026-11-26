import { useCallback, useRef, useState } from 'react';

/**
 * Client hints (i18n keys per field) that are checked while typing, not only on submit.
 *
 * - `submit(found)`: the form was sent; every field now shows its hint, and from now on each change re-checks all.
 * - `change(keys, found)`: the Manager/Staff changed the fields `keys`; `found` is the full check of the new values.
 *   Before the first submit only fields already changed show a hint, so an untouched empty field stays quiet.
 * - `reset()`: a fresh form (cancel, reopen).
 */
export function useLiveHints() {
  const [hints, setHintState] = useState({});
  const touched = useRef(new Set());
  const submitted = useRef(false);
  const submit = useCallback(found => { submitted.current = true; setHintState(found); }, []);
  const change = useCallback((keys, found) => {
    (Array.isArray(keys) ? keys : [keys]).forEach(key => touched.current.add(key));
    if (submitted.current) { setHintState(found); return; }
    setHintState(Object.fromEntries(Object.entries(found).filter(([key]) => touched.current.has(key))));
  }, []);
  const reset = useCallback(() => {
    touched.current = new Set();
    submitted.current = false;
    setHintState({});
  }, []);
  return { hints, submit, change, reset };
}

/**
 * Field keys changed between two lists of rows, as the checks name them: `prefix[i].field`, and
 * `prefix[i].quantities.<id>` for a per-branch quantity. Rows are matched by `idOf` so adding or removing a row does
 * not mark the rows around it; a changed row count adds `prefix` itself (e.g. "at least one size").
 */
export function changedRowKeys(prefix, before, after, idOf) {
  const keys = [];
  if (before.length !== after.length) keys.push(prefix);
  const old = new Map(before.map(row => [idOf(row), row]));
  after.forEach((row, index) => {
    const previous = old.get(idOf(row));
    if (!previous || previous === row) return;
    Object.keys(row).forEach(field => {
      if (field === 'quantities') {
        Object.keys(row.quantities || {}).forEach(id => {
          if (row.quantities[id] !== previous.quantities?.[id]) keys.push(`${prefix}[${index}].quantities.${id}`);
        });
      } else if (row[field] !== previous[field]) keys.push(`${prefix}[${index}].${field}`);
    });
  });
  return keys;
}
