import React from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/**
 * A quantity field with − and + buttons beside it: the number can still be typed, and each button moves it by one
 * (never below zero). The value stays a string, as the forms keep it, so a half-typed value is not lost; a value that
 * is not a whole number counts as 0 when a button is pressed.
 */
export function QuantityStepper({ value, onChange, label, disabled, invalid, min = 0 }) {
  const t = useT(messages);
  const current = /^\d+$/.test(String(value ?? '').trim()) ? Number(String(value).trim()) : null;
  const step = delta => onChange(String(Math.max(min, (current ?? min) + delta)));
  return (
    <span className="qty-stepper">
      <button type="button" className="qty-step" aria-label={t('qty.decrease', { label })} disabled={disabled || current === null || current <= min}
        onClick={() => step(-1)}>−</button>
      <input inputMode="numeric" aria-label={label} value={value ?? ''} disabled={disabled} aria-invalid={invalid ? true : undefined}
        onChange={event => onChange(event.target.value)} />
      <button type="button" className="qty-step" aria-label={t('qty.increase', { label })} disabled={disabled} onClick={() => step(1)}>+</button>
    </span>
  );
}
