import React, { useEffect, useState } from 'react';
import { useCatalogueApi } from '../../application/useCatalogueApi';

/**
 * A product image for staff screens. Management media needs the bearer, so it cannot be an <img src> to the API:
 * the bytes are fetched once and shown from a blob URL that is released on unmount. While loading or if the image
 * cannot be read, the placeholder is shown instead.
 */
export function StaffImage({ mediaId, alt = '', className, placeholder }) {
  const api = useCatalogueApi();
  const [url, setUrl] = useState(null);

  useEffect(() => {
    if (!mediaId) return undefined;
    let objectUrl = null;
    let active = true;
    api.getMediaPreview(mediaId)
      .then(result => {
        if (!active || !result?.blob) return;
        objectUrl = URL.createObjectURL(result.blob);
        setUrl(objectUrl);
      })
      .catch(() => {});
    return () => {
      active = false;
      setUrl(null);
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [api, mediaId]);

  return url ? <img className={className} src={url} alt={alt} /> : placeholder;
}
