import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { sharedMessages } from '../../../shared/ui/messages';

// T085: every 002 screen text exists in Vietnamese and English, every key the screens use is defined, and every error
// code of the 002 contract (interfaces.md "Error contract") has its own text.
const FEATURE_DIR = path.resolve(__dirname, '..');
const ERROR_CODES = [
  'VALIDATION_ERROR', 'UNSUPPORTED_FILTER', 'INVALID_SORT', 'UNKNOWN_FIELD', 'IMPORT_FILE_INVALID', 'IMPORT_ROW_LIMIT_EXCEEDED',
  'CATALOGUE_ACCESS_DENIED', 'PRODUCT_NOT_FOUND', 'VARIANT_NOT_FOUND', 'CATEGORY_NOT_FOUND', 'MEDIA_NOT_FOUND', 'PROPOSAL_NOT_FOUND',
  'IMPORT_BATCH_NOT_FOUND', 'STALE_VERSION', 'DUPLICATE_CATEGORY_NAME', 'PENDING_PRICE_PROPOSAL_EXISTS', 'REVIEW_NOT_CURRENT',
  'PRICE_NOT_APPROVED', 'PRICE_TERMS_CHANGED', 'PRICE_APPLICATION_STALE', 'PUBLICATION_PREREQUISITES_MISSING', 'QUANTITY_BELOW_HELD',
  'SIZE_LIST_IMMUTABLE', 'PRODUCT_DELETED', 'IDEMPOTENCY_KEY_REUSED', 'IMPORT_HAS_ERRORS', 'IMPORT_BATCH_EXPIRED', 'IMPORT_PREVIEW_STALE',
  'MEDIA_TOO_LARGE', 'IMPORT_FILE_TOO_LARGE', 'MEDIA_TYPE_UNSUPPORTED', 'IMPORT_FILE_TYPE_UNSUPPORTED',
  'IDENTITY_DEPENDENCY_UNAVAILABLE', 'AVAILABILITY_DEPENDENCY_UNAVAILABLE', 'MEDIA_DEPENDENCY_UNAVAILABLE', 'CATALOGUE_SAVE_UNAVAILABLE',
  'CATALOGUE_BUSY',
];
// 401 codes are worded once for the whole app in the shared dictionary.
const SHARED_CODES = ['AUTHENTICATION_REQUIRED', 'INVALID_CREDENTIAL'];

function sourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === 'test' ? [] : sourceFiles(full);
    return /\.(jsx?|tsx?)$/.test(entry.name) && entry.name !== 'messages.js' ? [full] : [];
  });
}

describe('002 vi/en texts (T085)', () => {
  it('Vietnamese and English have the same keys and no empty text', () => {
    expect(Object.keys(messages.en).sort()).toEqual(Object.keys(messages.vi).sort());
    for (const language of ['vi', 'en']) {
      for (const [key, text] of Object.entries(messages[language])) expect(text.trim(), `${language}.${key}`).not.toBe('');
    }
  });

  it('every static key used by the screens is defined', () => {
    const used = new Set();
    for (const file of sourceFiles(FEATURE_DIR)) {
      for (const match of fs.readFileSync(file, 'utf8').matchAll(/\bt\(['"]([A-Za-z0-9_.-]+)['"]/g)) used.add(match[1]);
    }
    const missing = [...used].filter(key => !(key in messages.vi) && !(key in sharedMessages.vi));
    expect(missing).toEqual([]);
    expect(used.size).toBeGreaterThan(100);
  });

  it('every contract error code has its own text in both languages', () => {
    for (const code of ERROR_CODES) {
      expect(messages.vi[`error.${code}`], code).toBeTruthy();
      expect(messages.en[`error.${code}`], code).toBeTruthy();
    }
    for (const code of SHARED_CODES) expect(sharedMessages.vi[`error.${code}`] && sharedMessages.en[`error.${code}`], code).toBeTruthy();
  });
});
