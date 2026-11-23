import { describe, expect, it } from 'vitest';
import { enumLabel, formatMoney, translate } from './I18nProvider';

describe('i18n', () => {
  const messages = { vi: { hello: 'Xin chào {name}', onlyVi: 'Chỉ có tiếng Việt' }, en: { hello: 'Hello {name}' } };

  it('translates with placeholders and falls back to Vietnamese, then to the key', () => {
    expect(translate(messages, 'en', 'hello', { name: 'Mland' })).toBe('Hello Mland');
    expect(translate(messages, 'en', 'onlyVi')).toBe('Chỉ có tiếng Việt');
    expect(translate(messages, 'vi', 'missing')).toBe('missing');
  });

  it('labels enum values and falls back to the raw value, never to a key', () => {
    const m = { vi: { 'status.ACTIVE': 'Đang hoạt động' }, en: { 'status.ACTIVE': 'Active' } };
    expect(enumLabel(m, 'en', 'status', 'ACTIVE')).toBe('Active');
    expect(enumLabel(m, 'vi', 'status', 'ACTIVE')).toBe('Đang hoạt động');
    expect(enumLabel(m, 'en', 'status', 'NEW_STATE')).toBe('NEW_STATE');
    expect(enumLabel(m, 'en', 'status', null)).toBe('—');
  });

  it('formats decimal-string VND amounts without losing digits', () => {
    expect(formatMoney('1250000', 'vi')).toBe('1.250.000\u00a0₫');
    expect(formatMoney('1250000', 'en')).toBe('1,250,000\u00a0₫');
    expect(formatMoney('9223372036854775807', 'vi')).toBe('9.223.372.036.854.775.807\u00a0₫');
    expect(formatMoney(null, 'vi')).toBe('—');
  });
});
