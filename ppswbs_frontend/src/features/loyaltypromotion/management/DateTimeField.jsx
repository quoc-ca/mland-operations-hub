import React, { useId } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));
const MINUTES = Array.from({ length: 12 }, (_, step) => String(step * 5).padStart(2, '0'));

/**
 * Date plus a 24-hour time (00–23 h, minutes in steps of 5) instead of <input type="datetime-local">, whose time part
 * follows the browser's locale (AM/PM in an English Chrome). The value stays the same local "YYYY-MM-DDTHH:mm"
 * string, so the form rules are unchanged; a minute outside the 5-minute steps (an older programme) is kept as is.
 */
export function DateTimeField({ label, value, onChange, error, hint }) {
  const t = useT(messages);
  const id = useId();
  const [date = '', time = ''] = (value || '').split('T');
  const [hour = '', minute = ''] = time.split(':');
  const minutes = minute && !MINUTES.includes(minute) ? [...MINUTES, minute].sort() : MINUTES;
  const update = (nextDate, nextHour, nextMinute) => {
    onChange(nextDate ? `${nextDate}T${nextHour || '00'}:${nextMinute || '00'}` : '');
  };
  const described = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined;
  const invalid = error ? true : undefined;

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="date-time">
        <input id={id} type="date" value={date} aria-invalid={invalid} aria-describedby={described}
          onChange={event => update(event.target.value, hour, minute)} />
        <select aria-label={t('time.hour', { label })} value={hour} aria-invalid={invalid} onChange={event => update(date, event.target.value, minute)}>
          {!hour && <option value="">--</option>}
          {HOURS.map(item => <option key={item} value={item}>{item} h</option>)}
        </select>
        <span aria-hidden="true">:</span>
        <select aria-label={t('time.minute', { label })} value={minute} aria-invalid={invalid} onChange={event => update(date, hour, event.target.value)}>
          {!minute && <option value="">--</option>}
          {minutes.map(item => <option key={item} value={item}>{item}</option>)}
        </select>
      </div>
      {hint && <span className="muted small" id={`${id}-hint`}>{hint}</span>}
      {error && <span className="field-error" id={`${id}-error`}>{error}</span>}
    </div>
  );
}
