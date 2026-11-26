import { useCallback, useRef, useState } from 'react';
import { serverFieldErrors } from '../domain/promotionRules';

/**
 * Field errors of a form: client hints (i18n keys) plus the server's fieldErrors of the last command.
 * An error disappears as soon as the Manager changes a field it belongs to, without waiting for the next submit
 * (`clear`); submitting again shows the new hints and the new server answer.
 *
 * `check(keys, found)` validates while typing: the given error keys count as touched, and the hints of every touched
 * key are taken from `found` (the form's full check of the new value), so a wrong value shows its hint at once and a
 * fixed one loses it. Once the form was submitted every key counts as touched.
 */
export function useFieldErrors(serverError, translate) {
  const [hints, setHintState] = useState({});
  const [cleared, setCleared] = useState(() => new Set());
  const touched = useRef(new Set());
  const submitted = useRef(false);
  const server = serverFieldErrors(serverError);
  const errorOf = field => (!cleared.has(field) && server[field]) || (hints[field] && translate(hints[field])) || null;
  const setHints = useCallback(found => { submitted.current = true; setHintState(found); setCleared(new Set()); }, []);
  const clear = useCallback((...fields) => {
    setCleared(current => {
      if (fields.every(field => current.has(field))) return current;
      const next = new Set(current);
      fields.forEach(field => next.add(field));
      return next;
    });
    setHintState(current => {
      if (!fields.some(field => field in current)) return current;
      const next = { ...current };
      fields.forEach(field => { delete next[field]; });
      return next;
    });
  }, []);
  const check = useCallback((keys, found) => {
    keys.forEach(key => touched.current.add(key));
    if (submitted.current) { setHintState(found); return; }
    setHintState(current => {
      const next = { ...current };
      touched.current.forEach(key => {
        if (found[key]) next[key] = found[key];
        else delete next[key];
      });
      return next;
    });
  }, []);
  /** Starts a fresh form: no hints, nothing touched, not submitted. */
  const reset = useCallback(() => {
    touched.current = new Set();
    submitted.current = false;
    setHintState({});
    setCleared(new Set());
  }, []);
  return { errorOf, setHints, clear, check, reset };
}
