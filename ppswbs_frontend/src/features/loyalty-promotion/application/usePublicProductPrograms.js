import { useEffect, useState } from 'react';
import { useLoyaltyApi } from './useLoyaltyApi';

const PAGE = 100;
const MAX_PAGES = 5;

/**
 * Active public programmes of one scope, read once per page that shows offers (at most MAX_PAGES × 100). A failure
 * leaves the list empty: pages then show their normal prices, never an error for an extra.
 */
function usePublicPrograms(scope) {
  const api = useLoyaltyApi();
  const [programs, setPrograms] = useState([]);
  useEffect(() => {
    let alive = true;
    (async () => {
      const found = [];
      let cursor;
      try {
        for (let page = 0; page < MAX_PAGES; page += 1) {
          const result = await api.listPublicPromotions({ scope, cursor, limit: PAGE });
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
  }, [api, scope]);
  return programs;
}

/** Product programmes for 002's public product pages (FR-042). */
export function usePublicProductPrograms() {
  return usePublicPrograms('PRODUCT');
}

/** Workshop programmes for 003.1's public package surfaces (FR-045); the API filters by scope, not by kind. */
export function usePublicWorkshopPrograms() {
  return usePublicPrograms('WORKSHOP');
}
