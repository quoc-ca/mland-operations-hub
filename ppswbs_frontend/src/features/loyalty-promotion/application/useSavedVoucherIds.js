import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';

const PAGE_LIMIT = 100;
const MAX_PAGES = 10;

/**
 * IDs of the vouchers the signed-in Member already saved (GET saved-vouchers, walked page by page), so a public
 * card can say "Đã lưu" after a reload. A Guest has none. A failed read leaves the set empty: the card then offers
 * "Lưu" again, which is harmless because saving is idempotent (FR-012). Nothing is cached between screens.
 * Only a Member has a wallet: a signed-in Staff, Manager or Admin gets 403 there, which the app treats as "no access"
 * and sends home (T115), so the wallet is read only once the role is known to be MEMBER.
 */
export function useSavedVoucherIds(api) {
  const { status } = useAuth();
  const role = useRole();
  const member = status === 'signed-in' && role === 'MEMBER';
  // promotion id -> availability from the wallet (ACTIVE, NO_USES_LEFT, MEMBER_LIMIT_REACHED...).
  const [saved, setSaved] = useState(() => new Map());

  useEffect(() => {
    if (!member) { setSaved(new Map()); return undefined; }
    let active = true;
    (async () => {
      const found = new Map();
      let cursor;
      try {
        for (let page = 0; page < MAX_PAGES; page += 1) {
          const result = await api.listSavedVouchers({ cursor, limit: PAGE_LIMIT });
          (result?.items || []).forEach(item => item?.promotion?.id != null && found.set(String(item.promotion.id), item.availability));
          cursor = result?.nextCursor;
          if (!cursor) break;
        }
      } catch {
        // Saved state unknown: keep what was read so far.
      }
      if (active) setSaved(found);
    })();
    return () => { active = false; };
  }, [api, member]);

  const isSaved = useCallback(id => saved.has(String(id)), [saved]);
  /** The wallet's availability of a saved voucher; undefined when not saved (a public card does not say). */
  const availabilityOf = useCallback(id => saved.get(String(id)), [saved]);
  const markSaved = useCallback(id => setSaved(current => new Map(current).set(String(id), current.get(String(id)) || 'ACTIVE')), []);
  return { isSaved, availabilityOf, markSaved };
}
