/**
 * Booking contact (003.1 FR-030): the email, and the phone as a country code plus the national number. The phone is
 * checked against the length rules of the chosen country and sent to the server in international form
 * (+<dial><national>, e.g. +84338905173). The server still has the last word: it requires a valid email up to 254
 * characters and a phone up to 32 characters.
 *
 * The country list is a short, hand-kept one (no phone library). `national` checks the number WITHOUT the trunk "0"
 * that people type at home (0338… for +84); `trunk` says whether that leading 0 is dropped.
 */
import { translate } from '../shared/i18n/I18nProvider';
import { messages } from './messages';

const L = (lang, key, params) => translate(messages, lang || 'vi', key, params);

export const PHONE_COUNTRIES = [
  { iso: 'VN', dial: '84', name: L('vi', 'country.VN'), trunk: true, national: /^[35789]\d{8}$/ },
  { iso: 'US', dial: '1', name: L('vi', 'country.US'), trunk: false, national: /^[2-9]\d{9}$/ },
  { iso: 'JP', dial: '81', name: L('vi', 'country.JP'), trunk: true, national: /^[1-9]\d{8,9}$/ },
  { iso: 'KR', dial: '82', name: L('vi', 'country.KR'), trunk: true, national: /^[1-9]\d{7,9}$/ },
  { iso: 'CN', dial: '86', name: L('vi', 'country.CN'), trunk: true, national: /^1[3-9]\d{9}$/ },
  { iso: 'SG', dial: '65', name: 'Singapore', trunk: false, national: /^[3689]\d{7}$/ },
  { iso: 'TH', dial: '66', name: L('vi', 'country.TH'), trunk: true, national: /^[1-9]\d{7,8}$/ },
  { iso: 'AU', dial: '61', name: L('vi', 'country.AU'), trunk: true, national: /^[2-478]\d{8}$/ },
  { iso: 'GB', dial: '44', name: 'Anh', trunk: true, national: /^[1-9]\d{9}$/ },
  { iso: 'FR', dial: '33', name: L('vi', 'country.FR'), trunk: true, national: /^[1-9]\d{8}$/ },
  { iso: 'DE', dial: '49', name: L('vi', 'country.DE'), trunk: true, national: /^[1-9]\d{9,10}$/ },
];

export const DEFAULT_PHONE_COUNTRY = 'VN';
export const EMPTY_CONTACT = { contactEmail: '', contactPhone: '', contactPhoneCountry: DEFAULT_PHONE_COUNTRY };

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const SEPARATORS = /[\s.\-()]/g;
const countryOf = iso => PHONE_COUNTRIES.find(item => item.iso === iso) || PHONE_COUNTRIES[0];

/** The digits of a national number as typed: separators removed, trunk "0" dropped when the country uses one; null if not digits. */
function nationalDigits(country, typed) {
  const cleaned = String(typed ?? '').trim().replace(SEPARATORS, '');
  if (!/^\d+$/.test(cleaned)) return null;
  return country.trunk && cleaned.startsWith('0') ? cleaned.slice(1) : cleaned;
}

/** `+<dial><national>` for a country and the number as typed, or '' when it is empty or not digits. */
export function toInternationalPhone(iso, typed) {
  const country = countryOf(iso);
  const digits = nationalDigits(country, typed);
  return digits ? `+${country.dial}${digits}` : '';
}

/**
 * Splits a stored phone ("0338905173", "+84 338 905 173", "84338905173") into { contactPhoneCountry, contactPhone }
 * for the form. A number that names no listed country stays as it is under the default country.
 */
export function splitPhone(value) {
  const raw = String(value ?? '').trim();
  const cleaned = raw.replace(SEPARATORS, '');
  if (cleaned.startsWith('+')) {
    const digits = cleaned.slice(1);
    const match = [...PHONE_COUNTRIES].sort((a, b) => b.dial.length - a.dial.length)
      .find(item => digits.startsWith(item.dial));
    if (match) return { contactPhoneCountry: match.iso, contactPhone: digits.slice(match.dial.length) };
    return { contactPhoneCountry: DEFAULT_PHONE_COUNTRY, contactPhone: raw };
  }
  if (/^84\d{9}$/.test(cleaned)) return { contactPhoneCountry: 'VN', contactPhone: cleaned.slice(2) };
  return { contactPhoneCountry: DEFAULT_PHONE_COUNTRY, contactPhone: raw };
}

/** The one stored and compared form of an email: trimmed, lower case. */
export function normalizeEmail(value) {
  return String(value ?? '').trim().toLowerCase();
}

export function isValidEmail(value) {
  const email = normalizeEmail(value);
  return EMAIL.test(email) && email.length <= 254;
}

/** True when `typed` is a number of the chosen country (see PHONE_COUNTRIES). */
export function isValidPhone(iso, typed) {
  const country = countryOf(iso);
  const digits = nationalDigits(country, typed);
  return digits !== null && country.national.test(digits);
}

export const EMAIL_ERROR = L('vi', 'helper.emailInvalid');
export const phoneError = (iso, lang = 'vi') => L(lang, 'helper.phoneInvalid', { country: countryName(iso, lang), dial: countryOf(iso).dial });
export const emailError = lang => L(lang, 'helper.emailInvalid');

/** Country name for the select in the given language (`name` itself stays the Vietnamese one). */
export const countryName = (iso, lang = 'vi') => L(lang, `country.${countryOf(iso).iso}`);

/** { contactEmail?, contactPhone? } with a message per invalid field; empty when the contact can be sent. */
export function validateBookingContact(contact, lang = 'vi') {
  const errors = {};
  if (!isValidEmail(contact?.contactEmail)) errors.contactEmail = emailError(lang);
  if (!isValidPhone(contact?.contactPhoneCountry, contact?.contactPhone)) {
    errors.contactPhone = phoneError(contact?.contactPhoneCountry, lang);
  }
  return errors;
}

/** The contact as the confirm call sends it: normalized email and the phone in international form. */
export function contactForRequest(contact) {
  return {
    contactEmail: normalizeEmail(contact?.contactEmail),
    contactPhone: toInternationalPhone(contact?.contactPhoneCountry, contact?.contactPhone),
  };
}

/**
 * One contact proof (Guest lookup, payment return, buyer verification, folio): the email OR the phone, never both.
 * Returns { proof, error } where `proof` is { contactEmail } or { contactPhone } in normalized form, or null with a
 * message when it is empty, both are filled, or the one given is malformed. A proof is checked for format only; the
 * server decides whether it matches the booking.
 */
export function buildContactProof({ email, phone, phoneCountry }, lang = 'vi') {
  const hasEmail = Boolean(String(email ?? '').trim());
  const hasPhone = Boolean(String(phone ?? '').trim());
  if (hasEmail === hasPhone) return { proof: null, error: L(lang, 'helper.proofExactlyOne') };
  if (hasEmail) {
    return isValidEmail(email) ? { proof: { contactEmail: normalizeEmail(email) }, error: null } : { proof: null, error: emailError(lang) };
  }
  return isValidPhone(phoneCountry, phone)
    ? { proof: { contactPhone: toInternationalPhone(phoneCountry, phone) }, error: null }
    : { proof: null, error: phoneError(phoneCountry, lang) };
}

/**
 * The one contact a Guest proves a group with (the server refuses two): the e-mail when there is one, else the phone in
 * international form. Empty object when the contact holds neither.
 */
export function guestContactProof(contact) {
  const email = normalizeEmail(contact?.contactEmail);
  if (email) return { contactEmail: email };
  const phone = toInternationalPhone(contact?.contactPhoneCountry, contact?.contactPhone);
  return phone ? { contactPhone: phone } : {};
}
