import React, { useState } from 'react';
import { apiBaseUrl } from '../shared/api/http';

/** Public address of a published package's cover image; the media ID only changes the browser's cache key. */
export const packageImageUrl = (packageId, imageMediaId) =>
  `${apiBaseUrl}/api/v1/workshop-packages/${encodeURIComponent(packageId)}/image?v=${encodeURIComponent(imageMediaId)}`;

/** 003.2 FR-073: the package's cover image, or `fallback` when there is none or it cannot be loaded. */
export function PackageImage({ packageId, imageMediaId, alt, className = '', fallback = null }) {
  const [failedId, setFailedId] = useState(null);
  if (!packageId || !imageMediaId || failedId === imageMediaId) return fallback;
  return (
    <img
      className={`package-image ${className}`.trim()}
      src={packageImageUrl(packageId, imageMediaId)}
      alt={alt}
      loading="lazy"
      onError={() => setFailedId(imageMediaId)}
    />
  );
}
