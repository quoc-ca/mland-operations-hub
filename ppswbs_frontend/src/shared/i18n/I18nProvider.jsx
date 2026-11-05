import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

// Shared vi/en mechanism (proposal §5): each feature keeps its own { vi, en } dictionary and reads it with
// useT(messages). Vietnamese is the default; the chosen language is a per-browser convenience only.
export const LANGUAGES = ['vi', 'en'];
const STORAGE_KEY = 'mland.language';
const LOCALES = { vi: 'vi-VN', en: 'en-US' };

function storedLanguage() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return LANGUAGES.includes(value) ? value : 'vi';
  } catch {
    return 'vi';
  }
}

const I18nContext = createContext({ language: 'vi', setLanguage: () => {} });

export function I18nProvider({ children, initialLanguage }) {
  const [language, setLanguageState] = useState(() => initialLanguage || storedLanguage());
  const setLanguage = useCallback(next => {
    if (!LANGUAGES.includes(next)) return;
    setLanguageState(next);
    try { window.localStorage.setItem(STORAGE_KEY, next); } catch { /* storage unavailable */ }
  }, []);
  const value = useMemo(() => ({ language, setLanguage }), [language, setLanguage]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useLanguage() {
  return useContext(I18nContext);
}

/** Replaces {name} placeholders; a missing key shows the key itself so gaps are visible. */
export function translate(messages, language, key, params) {
  const template = messages[language]?.[key] ?? messages.vi?.[key] ?? key;
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name) => (params[name] === undefined ? match : String(params[name])));
}

export function useT(messages) {
  const { language } = useLanguage();
  return useCallback((key, params) => translate(messages, language, key, params), [messages, language]);
}

/** Groups the digits of a decimal-string integer (optionally signed) without converting it through a JS number. */
export function formatNumber(value, language) {
  if (value === null || value === undefined || value === '') return '—';
  const text = String(value);
  const match = /^([+-]?)(\d+)$/.exec(text);
  if (!match) return text;
  const separator = language === 'en' ? ',' : '.';
  return `${match[1]}${match[2].replace(/\B(?=(\d{3})+(?!\d))/g, separator)}`;
}

/** Formats a decimal-string VND amount without converting it through a JS number. */
export function formatMoney(amount, language) {
  if (amount === null || amount === undefined || amount === '') return '—';
  const text = String(amount);
  if (!/^\d+$/.test(text)) return text;
  return `${formatNumber(text, language)} ₫`;
}

export function formatDateTime(value, language) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat(LOCALES[language] || LOCALES.vi, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function useFormat() {
  const { language } = useLanguage();
  return useMemo(() => ({
    money: amount => formatMoney(amount, language),
    number: value => formatNumber(value, language),
    dateTime: value => formatDateTime(value, language),
  }), [language]);
}
