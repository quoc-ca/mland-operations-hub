import { useCallback } from 'react';
import { useLanguage } from '../../../shared/i18n/I18nProvider';

/**
 * FE-08 screens follow the VI/EN switch (Session 2026-11-23): `t(vi, en)` gives the wording of the chosen language.
 * A `[vi, en]` pair is accepted too, for the label maps that live outside components.
 */
export function useText() {
  const { language } = useLanguage();
  return useCallback((vi, en) => {
    if (Array.isArray(vi)) return language === 'en' ? vi[1] : vi[0];
    return language === 'en' ? en : vi;
  }, [language]);
}

/** The language-free form of a label pair, for code that has no React context (defaults to Vietnamese). */
export const pick = (pair, language = 'vi') => (Array.isArray(pair) ? (language === 'en' ? pair[1] : pair[0]) : pair);
