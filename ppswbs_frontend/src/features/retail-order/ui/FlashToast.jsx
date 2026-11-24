import React, { useEffect } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { messages as retailMessages } from '../messages';
import './flash-toast.css';

/**
 * A short message at the top of the screen that goes away by itself after about 5 seconds (Thinh, 2026-11-21).
 * Used for success and information messages in FE-08; errors stay in place until the user acts.
 * `message` is a string, or { text, kind } with kind 'success' | 'info' | 'warn'.
 */
export function FlashToast({ message, onDone, duration = 5000 }) {
  const t = useT(retailMessages);
  const text = typeof message === 'string' ? message : message?.text;
  const kind = typeof message === 'string' ? 'success' : message?.kind || 'success';
  useEffect(() => {
    if (!text) return undefined;
    const timer = setTimeout(() => onDone?.(), duration);
    return () => clearTimeout(timer);
  }, [text, duration]); // eslint-disable-line react-hooks/exhaustive-deps
  if (!text) return null;
  return <div className={`flash-toast flash-toast-${kind}`} role="status" aria-live="polite">
    <span>{text}</span>
    <button type="button" className="flash-toast-close" aria-label={t('toast.closeNotice')} onClick={() => onDone?.()}>×</button>
  </div>;
}
