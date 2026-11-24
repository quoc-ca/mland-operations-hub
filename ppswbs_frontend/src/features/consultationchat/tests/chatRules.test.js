import { describe, it, expect } from 'vitest';
import { translate } from '../../../shared/i18n/I18nProvider';
import { consultationMessages } from '../messages';
import { countCodePoints, validateMessageText, MAX_MESSAGE_LENGTH } from '../domain/chatRules';

describe('Feature-013 Chat Domain Rules', () => {
  it('correctly counts Unicode code points and emoji', () => {
    expect(countCodePoints('')).toBe(0);
    expect(countCodePoints('Xin chào Mland')).toBe(14);
    // 💍 is one code point U+1F48D
    expect(countCodePoints('Nhẫn cưới 💍')).toBe(11);
    expect(countCodePoints('Tiếng Việt có dấu: ắ, ằ, ẳ, ẵ, ặ')).toBe(32);
  });

  it('rejects empty or whitespace-only messages', () => {
    expect(validateMessageText('')).toContain('không được để trống');
    expect(validateMessageText('   \n\t  ')).toContain('không được để trống');
    expect(validateMessageText(null)).toContain('không được để trống');
  });

  it('validates maximum 2000 Unicode characters', () => {
    const valid2000 = 'a'.repeat(2000);
    expect(validateMessageText(valid2000)).toBeNull();

    const invalid2001 = 'a'.repeat(2001);
    expect(validateMessageText(invalid2001)).toContain('vượt quá giới hạn 2.000 ký tự');
  });

  it('returns English validation messages when given the English translator', () => {
    const t = (key, params) => translate(consultationMessages, 'en', key, params);
    expect(validateMessageText('', t)).toBe('The message cannot be empty.');
    expect(validateMessageText('a'.repeat(2001), t)).toContain('2,000 character limit (current: 2001)');
  });
});
