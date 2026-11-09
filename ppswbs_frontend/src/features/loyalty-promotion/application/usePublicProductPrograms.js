import { useEffect, useState } from 'react';
import { useLoyaltyApi } from './useLoyaltyApi';

const PAGE = 100;
const MAX_PAGES = 5;

/**
 * Active public product programmes, read once per page that shows products (at most MAX_PAGES × 100). A failure
 * leaves the list empty: product pages then show their normal prices, never an error for an extra.
 */
export function usePublicProductPrograms() {
  const api = useLoyaltyApi();
  const [programs, setPrograms] = useState([]);
  useEffect(() => {
    let alive = true;
    (async () => {
      const found = [];
      let cursor;
      try {
        for (let page = 0; page < MAX_PAGES; page += 1) {
          const result = await api.listPublicPromotions({ scope: 'PRODUCT', cursor, limit: PAGE });
          found.push(...(result?.items || []));
          cursor = result?.nextCursor;
          if (!cursor) break;
        }
      } catch {
        // keep what was read; prices stay as they are
      }
      if (alive) setPrograms(found);
    })();
    return () => { alive = false; };
  }, [api]);
  return programs;
}
