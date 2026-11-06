// Feature-011 Review Management Rules & Invariants

export const LIMITS = {
  ratingMin: 1,
  ratingMax: 5,
  bodyTextMax: 2000,
  reasonMax: 500,
  replyMax: 2000,
  contactValueMax: 254,
  handleExpiryMinutes: 30,
};

export const TARGET_TYPES = {
  WORKSHOP_SESSION: 'WORKSHOP_SESSION',
  RETAIL_PRODUCT: 'RETAIL_PRODUCT',
};

export const SOURCE_TYPES = {
  WORKSHOP_ATTENDANCE: 'WORKSHOP_ATTENDANCE',
  RETAIL_ORDER_LINE: 'RETAIL_ORDER_LINE',
};

export const REVIEW_STATUSES = {
  PUBLIC: 'PUBLIC',
  HIDDEN: 'HIDDEN',
  REMOVED: 'REMOVED',
};

export function formatDateTime(isoString, locale = 'vi-VN') {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleString(locale === 'vi' ? 'vi-VN' : 'en-US', {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  } catch {
    return isoString;
  }
}

export function formatStars(rating) {
  const valid = Math.max(1, Math.min(5, Math.round(Number(rating) || 5)));
  return '★'.repeat(valid) + '☆'.repeat(5 - valid);
}

export function validateRating(rating) {
  const num = Number(rating);
  if (!rating || isNaN(num) || num < LIMITS.ratingMin || num > LIMITS.ratingMax || !Number.isInteger(num)) {
    return 'validationRating';
  }
  return null;
}

export function validateBodyText(text) {
  if (text && text.length > LIMITS.bodyTextMax) {
    return 'validationLength';
  }
  return null;
}

export function validateReason(reason) {
  if (reason && reason.length > LIMITS.reasonMax) {
    return 'validationReasonLength';
  }
  return null;
}

export function validateReply(reply) {
  if (!reply || !reply.trim()) {
    return 'validationRequired';
  }
  if (reply.length > LIMITS.replyMax) {
    return 'validationReplyLength';
  }
  return null;
}

export function validateContact(contactType, contactValue) {
  if (!contactValue || !contactValue.trim()) {
    return 'validationRequired';
  }
  const val = contactValue.trim();
  if (val.length > LIMITS.contactValueMax) {
    return 'validationLength';
  }
  if (contactType === 'EMAIL') {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(val)) {
      return 'validationInvalidEmail';
    }
  } else if (contactType === 'PHONE') {
    const phoneRegex = /^[0-9+\s\-()]{8,20}$/;
    if (!phoneRegex.test(val)) {
      return 'validationInvalidPhone';
    }
  }
  return null;
}

