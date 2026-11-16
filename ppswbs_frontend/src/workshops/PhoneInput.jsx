import React from 'react';
import { DEFAULT_PHONE_COUNTRY, PHONE_COUNTRIES } from './contactValidation';

/**
 * A phone number as a country code plus the national number (see contactValidation.js). The caller owns both values
 * and the label: give the input an `id` and point a `<label htmlFor>` at it. The label must not wrap this component,
 * or the country select would carry the same accessible name.
 */
export function PhoneInput({ id, country, value, onCountryChange, onValueChange, disabled, required, placeholder, countryLabel = 'Mã quốc gia' }) {
  return (
    <span style={{ display: 'flex', gap: '8px' }}>
      <select
        aria-label={countryLabel}
        value={country || DEFAULT_PHONE_COUNTRY}
        onChange={event => onCountryChange(event.target.value)}
        disabled={disabled}
        style={{ flex: '0 0 auto', maxWidth: '45%' }}
      >
        {PHONE_COUNTRIES.map(item => (
          <option key={item.iso} value={item.iso}>+{item.dial} {item.name}</option>
        ))}
      </select>
      <input
        id={id}
        type="tel"
        value={value}
        placeholder={placeholder}
        onChange={event => onValueChange(event.target.value)}
        disabled={disabled}
        required={required}
        style={{ flex: '1 1 auto', minWidth: 0 }}
      />
    </span>
  );
}
