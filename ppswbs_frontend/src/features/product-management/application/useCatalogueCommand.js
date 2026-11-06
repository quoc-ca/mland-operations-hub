import { useCallback, useRef, useState } from 'react';
import { newCommandId } from '../../../shared/api/http';
import { useCatalogueApi } from './useCatalogueApi';

/** Backend action names of 002 writes (receipt lookup, FR-028); the UI reads them here, not from infrastructure. */
export { ACTIONS } from '../infrastructure/catalogueApi';

/**
 * The shared useCommand plus FR-028 for 002 writes. When a write got no answer (status 0) it may still have been saved,
 * so Retry first asks for the receipt of that key (GET command-receipts/{key}?action=…): a receipt is the outcome and
 * nothing is sent again; COMMAND_RESULT_NOT_FOUND means the same key is sent again (the backend replays, never doubles).
 * run(send, action, key?) — action is the backend action name (ACTIONS); key defaults to a fresh one.
 */
export function useCatalogueCommand() {
  const api = useCatalogueApi();
  const [state, setState] = useState({ pending: false, error: null });
  const last = useRef(null);

  const execute = useCallback(async (send, key, action) => {
    last.current = { send, key, action, lost: false };
    setState({ pending: true, error: null });
    try {
      const result = await send(key);
      last.current = null;
      setState({ pending: false, error: null });
      return { ok: true, result };
    } catch (error) {
      last.current.lost = error?.status === 0;
      setState({ pending: false, error });
      return { ok: false, error };
    }
  }, []);

  const run = useCallback((send, action, key = newCommandId()) => execute(send, key, action), [execute]);

  const retry = useCallback(async () => {
    const current = last.current;
    if (!current) return undefined;
    if (current.lost && current.action) {
      setState({ pending: true, error: null });
      try {
        const receipt = await api.getCommandReceipt(current.key, current.action);
        last.current = null;
        setState({ pending: false, error: null });
        return { ok: true, result: receipt.result, fromReceipt: true };
      } catch (error) {
        if (error?.code !== 'COMMAND_RESULT_NOT_FOUND') {
          setState({ pending: false, error });
          return { ok: false, error };
        }
      }
    }
    return execute(current.send, current.key, current.action);
  }, [api, execute]);

  const reset = useCallback(() => { last.current = null; setState({ pending: false, error: null }); }, []);
  return { ...state, run, retry, reset };
}
