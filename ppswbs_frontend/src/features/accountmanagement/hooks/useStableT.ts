import { useCallback, useRef } from 'react';
import { translate, useLanguage } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';

export type Translate = (key: string, params?: Record<string, string | number>) => string;

/** Vietnamese translator for pure validators called without a React context (keeps their default output). */
export const viT: Translate = (key, params) => translate(accountMessages, 'vi', key, params);

/**
 * A translator whose identity never changes, so it can be used inside long-lived callbacks and effects
 * without refetching or re-running them on a language switch; it always reads the current language.
 */
export function useStableT(): Translate {
  const { language } = useLanguage();
  const languageRef = useRef(language);
  languageRef.current = language;
  return useCallback((key, params) => translate(accountMessages, languageRef.current, key, params), []);
}
