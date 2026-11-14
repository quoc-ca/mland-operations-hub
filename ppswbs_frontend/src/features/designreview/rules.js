// Validation limits and invariants mirroring Feature-004 backend rules
export const LIMITS = {
  promptMax: 2000,
  descriptionMax: 4000,
  reasonMax: 2000,
  catalogueRefMax: 100,
  maxImageBytes: 10 * 1024 * 1024, // 10 MB
  maxDimension: 4096,
};

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const CONSENT_PURPOSE = 'INDEPENDENT_DESIGN_IMAGE_ANALYSIS';
export const CONSENT_VERSION = 'v1.0';

export function formatVnd(amount) {
  if (amount === undefined || amount === null || isNaN(Number(amount))) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(amount));
}

export function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
  } catch {
    return isoString;
  }
}

export function validateImageFile(file) {
  if (!file) return null;
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return 'validation.invalidImageType';
  }
  if (file.size > LIMITS.maxImageBytes) {
    return 'validation.imageTooLarge';
  }
  return null;
}

