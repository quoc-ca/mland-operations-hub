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

export function formatFileSize(bytes) {
  if (!bytes || isNaN(bytes)) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export function validateImageFile(file) {
  if (!file) return null;
  if (file.size === 0) {
    return 'validation.imageEmpty';
  }
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return 'validation.invalidImageType';
  }
  if (file.size > LIMITS.maxImageBytes) {
    return 'validation.imageTooLarge';
  }
  return null;
}

export function validateImageDimensions(file) {
  return new Promise((resolve) => {
    // If not in a real browser or in jsdom test environment, skip async image decoding
    if (
      !file ||
      typeof window === 'undefined' ||
      typeof Image === 'undefined' ||
      typeof URL === 'undefined' ||
      typeof URL.createObjectURL !== 'function' ||
      (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent))
    ) {
      resolve(null);
      return;
    }

    try {
      const objectUrl = URL.createObjectURL(file);
      const img = new Image();
      let finished = false;

      const cleanup = () => {
        if (!finished) {
          finished = true;
          try {
            URL.revokeObjectURL(objectUrl);
          } catch {}
        }
      };

      // Safeguard timeout to never hang
      const timer = setTimeout(() => {
        cleanup();
        resolve(null);
      }, 1000);

      img.onload = () => {
        clearTimeout(timer);
        cleanup();
        if (img.width > LIMITS.maxDimension || img.height > LIMITS.maxDimension) {
          resolve('validation.imageDimensionTooLarge');
        } else {
          resolve(null);
        }
      };

      img.onerror = () => {
        clearTimeout(timer);
        cleanup();
        resolve(null);
      };

      img.src = objectUrl;
    } catch {
      resolve(null);
    }
  });
}

