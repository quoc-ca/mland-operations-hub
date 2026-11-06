import { describe, expect, it } from 'vitest';
import { parseMagicLinkToken } from './magicLinkToken';

describe('parseMagicLinkToken', () => {
  it('reads the token alone, the form the booking emails use', () => {
    expect(parseMagicLinkToken('#c4c9404b-473c-4880-aa4c-4ad2651ccc31')).toBe('c4c9404b-473c-4880-aa4c-4ad2651ccc31');
  });

  it('still reads the named forms', () => {
    expect(parseMagicLinkToken('#token=abc123')).toBe('abc123');
    expect(parseMagicLinkToken('#/workshop-booking/access?token=abc123')).toBe('abc123');
    expect(parseMagicLinkToken('#a=1&token=abc123&b=2')).toBe('abc123');
  });

  it('reads the token after a nested "#"', () => {
    expect(parseMagicLinkToken('#/workshop-booking/access#abc123')).toBe('abc123');
  });

  it('decodes an encoded token', () => {
    expect(parseMagicLinkToken('#ab%2Dcd')).toBe('ab-cd');
  });

  it('returns null when there is no token', () => {
    expect(parseMagicLinkToken('')).toBeNull();
    expect(parseMagicLinkToken('#')).toBeNull();
    expect(parseMagicLinkToken(undefined)).toBeNull();
    expect(parseMagicLinkToken('#/workshop-booking/access')).toBeNull();
    expect(parseMagicLinkToken('#two words')).toBeNull();
  });
});
