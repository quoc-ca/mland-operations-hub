// Client-side hints mirroring LoyaltyValues / LoyaltyScalarCodec; the backend stays the authority.
export const LIMITS = { nameMax: 200, codeMax: 128 };

const NONNEGATIVE = /^(0|[1-9][0-9]{0,18})$/;
const POSITIVE = /^[1-9][0-9]{0,18}$/;
const PERCENT = /^(0|[1-9][0-9]{0,2})(\.[0-9]{1,6})?$/;

export const isAmount = value => NONNEGATIVE.test(String(value ?? ''));
export const isPositive = value => POSITIVE.test(String(value ?? ''));
export const isPercent = value => PERCENT.test(String(value ?? '')) && Number(value) > 0 && Number(value) <= 100;

/** A value of <input type="datetime-local"> (local time) as the UTC instant the API expects ("…Z"). */
export function toInstant(local) {
  if (!local) return '';
  const date = new Date(local);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}

/** An API instant shown in <input type="datetime-local"> (local time, minutes). */
export function toLocalInput(instant) {
  if (!instant) return '';
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return '';
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// No 0/O, 1/I/L: a code is read aloud and typed by customers.
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

/**
 * A new voucher code, "ML" + 8 random characters from a browser CSPRNG (31^8 ≈ 8.5·10^11 values). Uniqueness is still
 * decided by the backend (CODE_EXISTS); the create form draws a new code and sends again when that happens.
 */
export function generateVoucherCode(random = values => crypto.getRandomValues(values)) {
  const bytes = random(new Uint32Array(8));
  return 'ML' + Array.from(bytes, value => CODE_ALPHABET[value % CODE_ALPHABET.length]).join('');
}

/** Default window of a new programme: from the next full quarter hour, for 30 days (local time). */
export function defaultWindow(now = new Date()) {
  const start = new Date(now);
  start.setSeconds(0, 0);
  start.setMinutes(Math.ceil((start.getMinutes() + 1) / 15) * 15);
  const end = new Date(start);
  end.setDate(end.getDate() + 30);
  return { startAt: toLocalInput(start.toISOString()), endAt: toLocalInput(end.toISOString()) };
}

/** "a, b , c" → ['a','b','c'] (empty items dropped, duplicates kept so the backend reports them). */
export function splitList(text) {
  return String(text || '').split(',').map(item => item.trim()).filter(Boolean);
}

/** Server field errors by form field; "targets.variantIds[16]" is shown on its list field, prefixed by the item. */
export function serverFieldErrors(error) {
  const byField = {};
  (error?.fieldErrors || []).forEach(({ field, message }) => {
    const item = /^(.*)\[([^\]]*)\]$/.exec(field);
    const key = item ? item[1] : field;
    const text = item ? `${item[2]}: ${message}` : message;
    byField[key] = byField[key] ? `${byField[key]} ${text}` : text;
  });
  return byField;
}

/** Request body of createPromotionProgram from the form. */
export function createBody(form) {
  const body = {
    kind: form.kind,
    scope: form.scope,
    name: form.name.trim(),
    startAt: toInstant(form.startAt),
    endAt: toInstant(form.endAt),
    discount: form.discountKind === 'PERCENT'
      ? { kind: 'PERCENT', percentage: form.percentage.trim(), maxDiscountAmount: form.maxDiscountAmount.trim() }
      : { kind: 'FIXED', amount: form.amount.trim(), maxDiscountAmount: form.maxDiscountAmount.trim() },
    minimumEligibleAmount: form.minimumEligibleAmount.trim(),
    targets: {},
  };
  if (form.kind === 'VOUCHER') {
    body.allowPoints = form.allowPoints;
    body.code = form.code.trim();
    body.totalLimit = form.totalLimit.trim();
    body.perMemberLimit = form.perMemberLimit.trim();
  }
  const lists = form.scope === 'PRODUCT'
    ? { productIds: form.productIds, variantIds: form.variantIds }
    : { packageIds: form.packageIds, branchIds: form.branchIds, workshopDates: form.workshopDates, sessionIds: form.sessionIds };
  Object.entries(lists).forEach(([key, text]) => { const items = splitList(text); if (items.length) body.targets[key] = items; });
  return body;
}

/** Hints (i18n keys) for the create form. */
export function checkCreate(form) {
  const errors = {};
  if (!form.name.trim()) errors.name = 'validation.required';
  else if ([...form.name.trim()].length > LIMITS.nameMax) errors.name = 'validation.nameTooLong';
  if (!form.startAt) errors.startAt = 'validation.required';
  if (!form.endAt) errors.endAt = 'validation.required';
  if (form.startAt && form.endAt && new Date(form.startAt) >= new Date(form.endAt)) errors.endAt = 'validation.endAfterStart';
  if (form.discountKind === 'PERCENT' && !isPercent(form.percentage.trim())) errors['discount.percentage'] = 'validation.percent';
  if (form.discountKind === 'FIXED' && !isPositive(form.amount.trim())) errors['discount.amount'] = 'validation.positive';
  if (!isPositive(form.maxDiscountAmount.trim())) errors['discount.maxDiscountAmount'] = 'validation.positive';
  if (!isAmount(form.minimumEligibleAmount.trim())) errors.minimumEligibleAmount = 'validation.amount';
  if (form.kind === 'VOUCHER') {
    if (!form.code.trim()) errors.code = 'validation.required';
    if (!isPositive(form.totalLimit.trim())) errors.totalLimit = 'validation.positive';
    if (!isPositive(form.perMemberLimit.trim())) errors.perMemberLimit = 'validation.positive';
  }
  if (form.scope === 'PRODUCT' && !splitList(form.productIds).length && !splitList(form.variantIds).length) errors.targets = 'validation.productTarget';
  if (form.scope === 'WORKSHOP' && !splitList(form.packageIds).length) errors['targets.packageIds'] = 'validation.packageTarget';
  return errors;
}
