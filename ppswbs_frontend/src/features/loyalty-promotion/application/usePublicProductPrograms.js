import { useEffect, useState } from 'react';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';
import { useLoyaltyApi } from './useLoyaltyApi';

const PAGE = 100;
const MAX_PAGES = 5;

/**
 * Active public programmes of one scope, read once per page that shows offers (at most MAX_PAGES × 100). A failure
 * leaves the list empty: pages then show their normal prices, never an error for an extra.
 *
 * Session 2026-11-22 (FR-046, FR-048): every distribution is read (`ALL`), so a CLAIM_REQUIRED voucher can be shown as
 * "Lưu mã giảm …"; and for a signed-in Member the wallet is merged in, each voucher marked `saved` with its
 * `walletAvailability`, so the displayed price can count a saved code that beats every public one. A wallet read that
 * fails, or a visitor who is not a Member, simply gives the Guest view.
 */
function usePublicPrograms(scope) {
  const api = useLoyaltyApi();
  const { status } = useAuth();
  const role = useRole();
  const member = status === 'signed-in' && role === 'MEMBER';
  const [programs, setPrograms] = useState([]);
  useEffect(() => {
    let alive = true;
    (async () => {
      const found = [];
      let cursor;
      try {
        for (let page = 0; page < MAX_PAGES; page += 1) {
          const result = await api.listPublicPromotions({ scope, cursor, limit: PAGE, distribution: 'ALL' });
          found.push(...(result?.items || []));
          cursor = result?.nextCursor;
          if (!cursor) break;
        }
      } catch {
        // keep what was read; prices stay as they are
      }
      const wallet = member ? await readWallet(api, scope) : [];
      if (alive) setPrograms(mergeWallet(found, wallet));
    })();
    return () => { alive = false; };
  }, [api, scope, member]);
  return programs;
}

/** The Member's saved vouchers of this scope; [] when the wallet cannot be read (FR-048: the Guest view then). */
async function readWallet(api, scope) {
  const items = [];
  let cursor;
  try {
    for (let page = 0; page < MAX_PAGES; page += 1) {
      const result = await api.listSavedVouchers({ cursor, limit: PAGE });
      items.push(...(result?.items || []));
      cursor = result?.nextCursor;
      if (!cursor) break;
    }
  } catch {
    return [];
  }
  return items.filter(item => item?.promotion?.scope === scope);
}

/**
 * Public programmes with the wallet's state on each saved voucher. A saved voucher that is ACTIVE in the wallet but not
 * in the public list (the list is capped) is added, so it can still be counted.
 */
export function mergeWallet(programs, wallet) {
  const byId = new Map(wallet.map(item => [String(item.promotion.id), item]));
  const merged = programs.map(program => {
    const item = byId.get(String(program.id));
    return item ? { ...program, saved: true, walletAvailability: item.availability } : program;
  });
  const listed = new Set(programs.map(program => String(program.id)));
  wallet.filter(item => !listed.has(String(item.promotion.id)) && item.availability === 'ACTIVE')
    .forEach(item => merged.push({ ...item.promotion, saved: true, walletAvailability: item.availability }));
  return merged;
}

/** Product programmes for 002's public product pages (FR-042). */
export function usePublicProductPrograms() {
  return usePublicPrograms('PRODUCT');
}

/** Workshop programmes for 003.1's public package surfaces (FR-045); the API filters by scope, not by kind. */
export function usePublicWorkshopPrograms() {
  return usePublicPrograms('WORKSHOP');
}
