import React from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';

/** 1–5 stars as a radio group: click a star to set the rating (session 2026-11-23, replaces the rating dropdown). */
export default function StarPicker({ value, onChange, disabled, label }) {
  const t = useT(reviewManagementMessages);
  return (
    <div className="star-picker" role="radiogroup" aria-label={label || t('rating')}>
      {[1, 2, 3, 4, 5].map(n => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={Number(value) === n}
          aria-label={t('starAria', { n })}
          className={`star-picker-star${n <= Number(value) ? ' on' : ''}`}
          onClick={() => onChange(n)}
          disabled={disabled}
        >
          ★
        </button>
      ))}
    </div>
  );
}
