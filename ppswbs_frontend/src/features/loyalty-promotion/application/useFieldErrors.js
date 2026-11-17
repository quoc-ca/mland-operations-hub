import { useCallback, useState } from 'react';
import { serverFieldErrors } from '../domain/promotionRules';

/**
 * Field errors of a form: client hints (i18n keys) set on submit plus the server's fieldErrors of the last command.
 * An error disappears as soon as the Manager changes a field it belongs to, without waiting for the next submit
 * (`clear`); submitting again shows the new hints and the new server answer.
 */
export function useFieldErrors(serverError, translate) {
  const [hints, setHintState] = useState({});
  const [cleared, setCleared] = useState(() => new Set());
  const server = serverFieldErrors(serverError);
  const errorOf = field => (cleared.has(field) ? null : (server[field] || (hints[field] && translate(hints[field]))));
  const setHints = useCallback(found => { setHintState(found); setCleared(new Set()); }, []);
  const clear = useCallback((...fields) => setCleared(current => {
    if (fields.every(field => current.has(field))) return current;
    const next = new Set(current);
    fields.forEach(field => next.add(field));
    return next;
  }), []);
  return { errorOf, setHints, clear };
}
