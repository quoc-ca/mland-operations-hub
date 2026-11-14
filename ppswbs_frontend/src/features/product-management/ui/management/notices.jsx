import React, { createContext, useContext } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/**
 * Outcome notices of the management pages. A notice is a message key, or { key, params, at, tone }: `params` fill
 * the message (size, amount, count), `at` names the place under the button that was used, and tone 'error' marks a
 * batch where some sizes failed. The same text shows at the top of the page and, through NoticeAt, right under the
 * button, until the next action replaces it.
 */
export const asNotice = notice => (typeof notice === 'string' ? { key: notice } : notice || null);

export function useNoticeText() {
  const t = useT(messages);
  return notice => {
    const value = asNotice(notice);
    return value ? t(value.key, value.params) : null;
  };
}

export function PageNotice({ notice }) {
  const text = useNoticeText();
  const value = asNotice(notice);
  if (!value) return null;
  return <div className={`notice ${value.tone === 'error' ? 'error' : 'success'}`} role="status">{text(value)}</div>;
}

export const NoticeContext = createContext(null);

/** The current notice again, under the button it belongs to (the page top already announces it). */
export function NoticeAt({ at }) {
  const text = useNoticeText();
  const value = asNotice(useContext(NoticeContext));
  if (!value || !value.at || value.at !== at || value.tone === 'error') return null;
  return <p className="notice success notice-at">{text(value)}</p>;
}
