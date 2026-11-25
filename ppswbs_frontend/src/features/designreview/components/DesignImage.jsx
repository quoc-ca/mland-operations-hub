import React, { useEffect, useState } from 'react';
import { useDesignReviewApi } from '../api/useDesignReviewApi';

/**
 * A private design reference image. The endpoint needs the session token, which a plain <img src> cannot send, so the
 * bytes are fetched through the shared client and shown from an object URL that is released on unmount.
 */
export default function DesignImage({ image, style, ...props }) {
  const api = useDesignReviewApi();
  const reference = image?.imageReference;
  const directUrl = image?.imageUrl;
  const [loaded, setLoaded] = useState({ reference: null, url: null, failed: false });

  useEffect(() => {
    if (directUrl || !reference) return undefined;
    let active = true;
    let objectUrl = null;
    api
      .getDesignImage(reference)
      .then(({ blob }) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setLoaded({ reference, url: objectUrl, failed: false });
      })
      .catch(() => {
        if (active) setLoaded({ reference, url: null, failed: true });
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [api, reference, directUrl]);

  const current = loaded.reference === reference ? loaded : { url: null, failed: false };
  return (
    <img
      {...props}
      src={directUrl || current.url || undefined}
      style={current.failed ? { ...style, opacity: 0.5 } : style}
    />
  );
}
