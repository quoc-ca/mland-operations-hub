import React, { useEffect, useState } from 'react';
import { apiBaseUrl } from '../../../shared/api/http';
import { useHttp } from '../../../shared/api/useHttp';

// One public product read per product for the whole session; the cart, checkout and order lists share it.
const cache = new Map();
const absolute = url => (!url ? null : url.startsWith('http') ? url : `${apiBaseUrl}${url}`);

/**
 * The product's first public image (FE-02 `GET /api/v1/products/{id}`), or the ◇ placeholder while it loads, when
 * the line has no product id, or when the product is no longer public (Session 2026-11-16).
 */
export function ProductThumb({ productId, className }) {
  const http = useHttp();
  const key = productId == null ? null : String(productId);
  const [url, setUrl] = useState(() => (key && cache.get(key)?.value) || null);

  useEffect(() => {
    if (!key) { setUrl(null); return undefined; }
    let alive = true;
    let entry = cache.get(key);
    if (!entry) {
      entry = {};
      entry.promise = http(`/api/v1/products/${encodeURIComponent(key)}`, { auth: false })
        .then(product => {
          const image = [...(product?.images || [])].sort((a, b) => a.position - b.position)[0];
          entry.value = absolute(image?.url);
          return entry.value;
        })
        .catch(() => { cache.delete(key); return null; });
      cache.set(key, entry);
    }
    entry.promise.then(value => { if (alive) setUrl(value); });
    return () => { alive = false; };
  }, [key, http]);

  return url
    ? <img className={className} src={url} alt="" loading="lazy" onError={() => setUrl(null)} />
    : <span className={className} aria-hidden="true">◇</span>;
}
