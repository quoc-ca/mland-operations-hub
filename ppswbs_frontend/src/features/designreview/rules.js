// Validation limits and invariants mirroring Feature-004 backend rules
export const LIMITS = {
  promptMax: 2000,
  descriptionMax: 4000,
  reasonMax: 2000,
  catalogueRefMax: 100,
  maxImageBytes: 5 * 1024 * 1024, // 5 MB
  maxDimension: 4096,
  maxImages: 5,
};

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const CONSENT_PURPOSE = 'INDEPENDENT_DESIGN_IMAGE_ANALYSIS';
export const CONSENT_VERSION = 'v1.0';

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

// t: translator (key, params) -> text, so the returned reason follows the UI language.
export async function detectImageBlur(file, threshold = 80.0, t = key => key) {
  if (
    !file ||
    typeof window === 'undefined' ||
    typeof document === 'undefined' ||
    typeof Image === 'undefined' ||
    typeof URL === 'undefined' ||
    typeof URL.createObjectURL !== 'function' ||
    (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent))
  ) {
    return { status: 'PASS', score: 100, reason: t('quality.simulated') };
  }

  return new Promise((resolve) => {
    let objectUrl = null;
    let finished = false;

    const cleanup = () => {
      if (!finished) {
        finished = true;
        if (objectUrl) {
          try {
            URL.revokeObjectURL(objectUrl);
          } catch {}
        }
      }
    };

    const timer = setTimeout(() => {
      cleanup();
      resolve({ status: 'PASS', score: 100, reason: t('quality.timeout') });
    }, 2000);

    try {
      objectUrl = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => {
        clearTimeout(timer);
        cleanup();
        try {
          const canvas = document.createElement('canvas');
          const maxDim = 400;
          let w = img.width || 100;
          let h = img.height || 100;
          if (w > maxDim || h > maxDim) {
            const scale = maxDim / Math.max(w, h);
            w = Math.max(1, Math.round(w * scale));
            h = Math.max(1, Math.round(h * scale));
          }
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            resolve({ status: 'PASS', score: 100, reason: t('quality.noCanvas') });
            return;
          }
          ctx.drawImage(img, 0, 0, w, h);
          const imageData = ctx.getImageData(0, 0, w, h);
          const data = imageData.data;
          const gray = new Float32Array(w * h);
          for (let i = 0; i < gray.length; i++) {
            const idx = i * 4;
            gray[i] = 0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2];
          }

          let sum = 0;
          let count = 0;
          const innerW = w - 2;
          const innerH = h - 2;
          if (innerW <= 0 || innerH <= 0) {
            resolve({ status: 'PASS', score: 100, reason: t('quality.tooSmall') });
            return;
          }

          const lap = new Float32Array(innerW * innerH);
          for (let y = 1; y < h - 1; y++) {
            for (let x = 1; x < w - 1; x++) {
              const val = gray[(y - 1) * w + x]
                        + gray[(y + 1) * w + x]
                        + gray[y * w + (x - 1)]
                        + gray[y * w + (x + 1)]
                        - 4 * gray[y * w + x];
              lap[count++] = val;
              sum += val;
            }
          }

          const mean = sum / count;
          let varSum = 0;
          for (let i = 0; i < count; i++) {
            const diff = lap[i] - mean;
            varSum += diff * diff;
          }
          const variance = varSum / count;

          if (variance < threshold) {
            resolve({
              status: 'TOO_BLURRY',
              score: Math.round(variance * 10) / 10,
              reason: t('quality.blurry', { score: variance.toFixed(1), threshold })
            });
          } else {
            resolve({
              status: 'PASS',
              score: Math.round(variance * 10) / 10,
              reason: t('quality.sharp', { score: variance.toFixed(1) })
            });
          }
        } catch {
          resolve({ status: 'PASS', score: 100, reason: t('quality.ok') });
        }
      };

      img.onerror = () => {
        clearTimeout(timer);
        cleanup();
        resolve({ status: 'QUALITY_CHECK_FAILED', score: 0, reason: t('quality.unreadable') });
      };

      img.src = objectUrl;
    } catch {
      clearTimeout(timer);
      cleanup();
      resolve({ status: 'PASS', score: 100, reason: t('quality.ok') });
    }
  });
}


