import React, { useCallback, useEffect, useId, useRef, useState } from 'react';
import { newCommandId } from '../api/http';
import { LANGUAGES, useLanguage, useT } from '../i18n/I18nProvider';
import { sharedMessages } from './messages';

/** Message for an ApiError: the feature's text for its code first, then the shared fallback by status. */
export function useErrorText(featureMessages) {
  const tShared = useT(sharedMessages);
  const tFeature = useT(featureMessages || { vi: {}, en: {} });
  return useCallback(error => {
    if (!error) return '';
    // A lost answer to a read changed nothing; only a write may or may not have been recorded.
    if (error.code === 'NETWORK_ERROR' && error.method === 'GET') return tShared('error.networkRead');
    const key = `error.${error.code}`;
    const featureText = tFeature(key);
    if (featureText !== key) return featureText;
    const sharedText = tShared(key);
    if (sharedText !== key) return sharedText;
    if (error.retryable) return tShared('error.retryable');
    if (error.status === 409) return tShared('error.stale');
    if (error.status === 403) return tShared('error.forbidden');
    return error.message || tShared('error.generic');
  }, [tFeature, tShared]);
}

/** Error box with correlation id; Try again repeats the same action, Load latest re-reads after a 409. */
export function ErrorNotice({ error, messages, onRetry, onReload }) {
  const t = useT(sharedMessages);
  const text = useErrorText(messages);
  if (!error) return null;
  return (
    <div className="notice error" role="alert">
      <p>{text(error)}</p>
      {error.correlationId && <p className="muted small">{t('correlation', { id: error.correlationId })}</p>}
      <div className="notice-actions">
        {onRetry && error.retryable && <button type="button" className="button outline" onClick={onRetry}>{t('retry')}</button>}
        {onReload && error.status === 409 && <button type="button" className="button outline" onClick={onReload}>{t('reloadLatest')}</button>}
      </div>
    </div>
  );
}

export function SuccessNotice({ children }) {
  if (!children) return null;
  return <div className="notice success" role="status">{children}</div>;
}

export function Loading() {
  const t = useT(sharedMessages);
  return <div className="empty-state" role="status">{t('loading')}</div>;
}

export function Field({ label, error, hint, children }) {
  const id = useId();
  const errorId = `${id}-error`;
  const control = React.cloneElement(children, {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? errorId : undefined,
  });
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {control}
      {hint && <span className="muted small">{hint}</span>}
      {error && <span className="field-error" id={errorId}>{error}</span>}
    </div>
  );
}

/** Separate confirmation step; Cancel never calls the API. */
export function ConfirmDialog({ open, title, children, confirmLabel, onConfirm, onCancel, pending, danger }) {
  const t = useT(sharedMessages);
  const confirmRef = useRef(null);
  useEffect(() => { if (open) confirmRef.current?.focus(); }, [open]);
  if (!open) return null;
  return (
    <div className="dialog-backdrop">
      <div className="dialog" role="dialog" aria-modal="true" aria-label={title}
        onKeyDown={event => { if (event.key === 'Escape' && !pending) onCancel(); }}>
        <h2>{title}</h2>
        <div>{children}</div>
        <div className="dialog-actions">
          <button type="button" className="button outline" onClick={onCancel} disabled={pending}>{t('cancel')}</button>
          <button type="button" ref={confirmRef} className={`button primary${danger ? ' danger' : ''}`} onClick={onConfirm} disabled={pending}>
            {confirmLabel || t('confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

export function LanguageSwitch() {
  const t = useT(sharedMessages);
  const { language, setLanguage } = useLanguage();
  return (
    <label className="language-switch">
      <span className="sr-only">{t('language')}</span>
      <select value={language} onChange={event => setLanguage(event.target.value)} aria-label={t('language')}>
        {LANGUAGES.map(code => <option key={code} value={code}>{code.toUpperCase()}</option>)}
      </select>
    </label>
  );
}

/**
 * One write action. run(send) gives send a new Idempotency-Key; retry() sends the same request with the same key
 * (503 / lost response). The button is only locked while the request runs; nothing is retried automatically.
 */
export function useCommand() {
  const [state, setState] = useState({ pending: false, error: null });
  const last = useRef(null);
  const execute = useCallback(async (send, key) => {
    last.current = { send, key };
    setState({ pending: true, error: null });
    try {
      const result = await send(key);
      last.current = null;
      setState({ pending: false, error: null });
      return { ok: true, result };
    } catch (error) {
      setState({ pending: false, error });
      return { ok: false, error };
    }
  }, []);
  const run = useCallback(send => execute(send, newCommandId()), [execute]);
  const retry = useCallback(() => (last.current ? execute(last.current.send, last.current.key) : undefined), [execute]);
  const reset = useCallback(() => { last.current = null; setState({ pending: false, error: null }); }, []);
  return { ...state, run, retry, reset };
}

/** Loads data for a screen; reload() reads again (also after a 409). No caching between screens. */
export function useResource(load, deps) {
  const [state, setState] = useState({ loading: true, error: null, data: null });
  const loadRef = useRef(load);
  const sequence = useRef(0);
  loadRef.current = load;
  // Only the latest read may update the screen, so a slow older response never overwrites a newer one.
  const reload = useCallback(async () => {
    const mine = ++sequence.current;
    setState(current => ({ ...current, loading: true, error: null }));
    try {
      const data = await loadRef.current();
      if (mine === sequence.current) setState({ loading: false, error: null, data });
      return data;
    } catch (error) {
      if (mine === sequence.current) setState(current => ({ ...current, loading: false, error }));
      return null;
    }
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { reload(); }, deps);
  return { ...state, reload, setData: data => setState(current => ({ ...current, data })) };
}
