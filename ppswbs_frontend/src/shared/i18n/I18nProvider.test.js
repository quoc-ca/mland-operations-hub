import { describe, expect, it } from 'vitest';
import { formatMoney, translate } from './I18nProvider';

describe('i18n', () => {
  const messages = { vi: { hello: 'Xin chào {name}', onlyVi: 'Chỉ có tiếng Việt' }, en: { hello: 'Hello {name}' } };

  it('translates with placeholders and falls back to Vietnamese, then to the key', () => {
    expect(translate(messages, 'en', 'hello', { name: 'Mland' })).toBe('Hello Mland');
    expect(translate(messages, 'en', 'onlyVi')).toBe('Chỉ có tiếng Việt');
    expect(translate(messages, 'vi', 'missing')).toBe('missing');
  });

  it('formats decimal-string VND amounts without losing digits', () => {
    expect(formatMoney('1250000', 'vi')).toBe('1.250.000 ₫');
    expect(formatMoney('1250000', 'en')).toBe('1,250,000 ₫');
    expect(formatMoney('9223372036854775807', 'vi')).toBe('9.223.372.036.854.775.807 ₫');
    expect(formatMoney(null, 'vi')).toBe('—');
  });
});
