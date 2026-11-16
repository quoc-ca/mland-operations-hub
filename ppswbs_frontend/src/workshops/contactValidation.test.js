import { describe, expect, it } from 'vitest';
import {
  buildContactProof, contactForRequest, isValidEmail, normalizeEmail, splitPhone, toInternationalPhone, validateBookingContact,
} from './contactValidation';

const ok = { contactEmail: 'quochhhe187257@fpt.edu.vn', contactPhone: '0338905173', contactPhoneCountry: 'VN' };

describe('003.1 FR-030 booking contact format', () => {
  it('accepts a valid email and a Vietnamese mobile with or without the trunk 0', () => {
    expect(validateBookingContact(ok)).toEqual({});
    expect(validateBookingContact({ ...ok, contactPhone: '338 905 173' })).toEqual({});
    expect(validateBookingContact({ ...ok, contactPhone: '0338.905.173' })).toEqual({});
  });

  it.each(['abc', 'a@b', 'a@b.', '@b.com', 'a b@c.com', ''])('rejects the email %j', email => {
    expect(validateBookingContact({ ...ok, contactEmail: email }).contactEmail).toBeTruthy();
  });

  it.each(['', 'abc', '123456', '0123456789', '03389051', '033890517345', '+84338905173'])(
    'rejects the Vietnamese number %j', phone => {
      expect(validateBookingContact({ ...ok, contactPhone: phone }).contactPhone).toBeTruthy();
    });

  it('checks the number against the chosen country', () => {
    expect(validateBookingContact({ ...ok, contactPhoneCountry: 'US', contactPhone: '(415) 555-2671' })).toEqual({});
    expect(validateBookingContact({ ...ok, contactPhoneCountry: 'SG', contactPhone: '9123 4567' })).toEqual({});
    expect(validateBookingContact({ ...ok, contactPhoneCountry: 'US', contactPhone: '0338905173' }).contactPhone).toBeTruthy();
    expect(validateBookingContact({ ...ok, contactPhoneCountry: 'SG', contactPhone: '338905173' }).contactPhone).toBeTruthy();
  });

  it('rejects an email longer than the server accepts', () => {
    expect(validateBookingContact({ ...ok, contactEmail: `${'a'.repeat(250)}@b.com` }).contactEmail).toBeTruthy();
  });
});

describe('one contact proof (lookup, payment return, buyer verification, folio)', () => {
  it('normalizes the one contact given', () => {
    expect(buildContactProof({ email: '  A@B.vn ' })).toEqual({ proof: { contactEmail: 'a@b.vn' }, error: null });
    expect(buildContactProof({ phone: '0901 234 567', phoneCountry: 'VN' })).toEqual({ proof: { contactPhone: '+84901234567' }, error: null });
    expect(buildContactProof({ phone: '415 555 2671', phoneCountry: 'US' }).proof).toEqual({ contactPhone: '+14155552671' });
  });

  it('refuses none, both, or a malformed one', () => {
    expect(buildContactProof({}).proof).toBeNull();
    expect(buildContactProof({ email: 'a@b.vn', phone: '0901234567' }).proof).toBeNull();
    expect(buildContactProof({ email: 'nope' }).error).toMatch(/Email/);
    expect(buildContactProof({ phone: '123', phoneCountry: 'VN' }).error).toMatch(/Số điện thoại/);
  });

  it('checks the email format used for ticket recipients', () => {
    expect(isValidEmail(' Friend@Example.com ')).toBe(true);
    expect(isValidEmail('friend@')).toBe(false);
    expect(normalizeEmail(' Friend@Example.com ')).toBe('friend@example.com');
  });
});

describe('phone in international form', () => {
  it('drops the trunk 0 and prefixes the dial code', () => {
    expect(toInternationalPhone('VN', '0338 905 173')).toBe('+84338905173');
    expect(toInternationalPhone('VN', '338905173')).toBe('+84338905173');
    expect(toInternationalPhone('US', '415-555-2671')).toBe('+14155552671');
    expect(toInternationalPhone('VN', '')).toBe('');
  });

  it('sends the trimmed, lower-case email and the international phone', () => {
    expect(contactForRequest({ ...ok, contactEmail: '  A@B.vn ' })).toEqual({ contactEmail: 'a@b.vn', contactPhone: '+84338905173' });
  });

  it('splits a stored phone back into country and number for the form', () => {
    expect(splitPhone('0338905173')).toEqual({ contactPhoneCountry: 'VN', contactPhone: '0338905173' });
    expect(splitPhone('+84 338 905 173')).toEqual({ contactPhoneCountry: 'VN', contactPhone: '338905173' });
    expect(splitPhone('84338905173')).toEqual({ contactPhoneCountry: 'VN', contactPhone: '338905173' });
    expect(splitPhone('+1 415 555 2671')).toEqual({ contactPhoneCountry: 'US', contactPhone: '4155552671' });
    expect(splitPhone('')).toEqual({ contactPhoneCountry: 'VN', contactPhone: '' });
  });
});
