import { useEffect, useState } from 'react';
import { productNameKey, sameNameProducts } from '../domain/productRules';

const PAGES = 3;
const PARALLEL = 4;
const MAX_NAMES = 60;

/**
 * Existing, not deleted products with exactly this name (productNameKey), read through the management list search
 * (q matches names, so a page holds every product whose name contains it; up to PAGES pages are read). Only products
 * this account can see are found. A failed read gives [] — the warning is advice and never blocks saving.
 */
export async function findSameName(api, name) {
  if (!productNameKey(name)) return [];
  const found = [];
  try {
    for (let page = 0; page < PAGES; page += 1) {
      const result = await api.listProducts({ q: name.trim(), page: page || undefined });
      found.push(...sameNameProducts(result?.items, name));
      const total = Number(result?.totalElements || 0);
      if ((page + 1) * Number(result?.pageSize || 20) >= total) break;
    }
  } catch {
    return [];
  }
  return found;
}

/** findSameName for many names (the products of an import file), a few at a time: { nameKey: [products] }. */
export async function findSameNames(api, names) {
  const keys = new Map();
  (names || []).forEach(name => { const key = productNameKey(name); if (key && !keys.has(key)) keys.set(key, name); });
  const queue = [...keys.entries()].slice(0, MAX_NAMES);
  const result = {};
  const worker = async () => {
    while (queue.length) {
      const [key, name] = queue.shift();
      const found = await findSameName(api, name);
      if (found.length) result[key] = found;
    }
  };
  await Promise.all(Array.from({ length: PARALLEL }, worker));
  return result;
}

/** Same-name products of the name being typed, looked up 400 ms after the last key stroke. */
export function useSameNameProducts(api, name) {
  const [found, setFound] = useState([]);
  useEffect(() => {
    if (!productNameKey(name)) { setFound([]); return undefined; }
    let alive = true;
    const timer = setTimeout(async () => {
      const result = await findSameName(api, name);
      if (alive) setFound(result);
    }, 400);
    return () => { alive = false; clearTimeout(timer); };
  }, [api, name]);
  return found;
}

/** The same-name products of an import preview (VALIDATED batch), read once per batch. */
export function useSameNamesOfBatch(api, batch) {
  const [found, setFound] = useState({});
  const names = batch?.state === 'VALIDATED' ? (batch.preview || []).map(item => item.name) : [];
  const signature = `${batch?.batchId || ''}:${names.join('|')}`;
  useEffect(() => {
    if (!names.length) { setFound({}); return undefined; }
    let alive = true;
    findSameNames(api, names).then(result => { if (alive) setFound(result); });
    return () => { alive = false; };
  }, [api, signature]); // eslint-disable-line react-hooks/exhaustive-deps
  return found;
}
