import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

/**
 * Filter state kept in the page URL, so the browser Back button (or a refresh) returns to the list as the user left it.
 * Same shape as `useState` for a flat object of strings: `[filters, setFilters]`, where `setFilters` takes the next
 * object or an updater function. A value equal to its default is left out of the URL; an empty value that differs from
 * its default (for example "All" when the default is "PENDING") is written as an empty parameter so it survives.
 * Changes replace the current history entry, so typing in a filter never needs extra Back presses.
 * Only filters belong here, never personal data such as an e-mail or a phone number.
 */
export function useUrlFilters(defaults) {
  const [searchParams, setSearchParams] = useSearchParams();
  const keys = Object.keys(defaults);
  const defaultsKey = JSON.stringify(defaults);

  const filters = useMemo(() => {
    const result = {};
    keys.forEach(key => { result[key] = searchParams.has(key) ? searchParams.get(key) : defaults[key]; });
    return result;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, defaultsKey]);

  const setFilters = useCallback(next => {
    setSearchParams(previous => {
      const current = {};
      keys.forEach(key => { current[key] = previous.has(key) ? previous.get(key) : defaults[key]; });
      const resolved = typeof next === 'function' ? next(current) : next;
      const params = new URLSearchParams(previous);
      keys.forEach(key => {
        const value = resolved[key] ?? '';
        if (String(value) === String(defaults[key])) params.delete(key);
        else params.set(key, String(value));
      });
      return params;
    }, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setSearchParams, defaultsKey]);

  return [filters, setFilters];
}
