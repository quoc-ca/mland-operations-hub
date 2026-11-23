// Mapping for typed backend error codes to localized error messages
import { viT } from './designFormat';

const KNOWN_CODES = [
  'DESIGN_INPUT_INVALID',
  'DESIGN_OPTION_INVALID',
  'DESIGN_PACKAGE_INCOMPATIBLE',
  'DESIGN_BRANCH_INCOMPATIBLE',
  'DESIGN_SESSION_EXPIRED',
  'DESIGN_ACCESS_DENIED',
  'DESIGN_VERSION_CONFLICT',
  'DESIGN_LOCKED',
  'IDEMPOTENCY_CONFLICT',
  'DESIGN_RULES_UNAVAILABLE',
  'AUTHENTICATION_REQUIRED',
];

/** `t` is the translator from useT(messages); it defaults to Vietnamese for callers outside React. */
export function getErrorMessage(error, t = viT) {
  if (!error) return t('error.unknown');
  const code = error.code || '';
  if (KNOWN_CODES.includes(code)) return t(`error.${code}`);
  return error.message || t('error.unknown');
}
