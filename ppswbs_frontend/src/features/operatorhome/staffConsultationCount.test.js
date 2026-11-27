import { describe, expect, it } from 'vitest';
import { CONSULTATION_MAX_PAGES, countUnclaimedConsultations } from './staffWork';

const row = claimantRole => ({ claimantRole });
const rows = (unclaimed, claimed = 0) => [...Array(unclaimed).fill(null).map(() => row(null)), ...Array(claimed).fill(null).map(() => row('STAFF'))];

/** A fake `http` over pages: page i is returned for the cursor `p<i>` (the first call has no cursor). */
function pagedHttp(pages) {
  const calls = [];
  const http = async (path, { query }) => {
    calls.push({ path, query });
    const index = query.cursor ? Number(query.cursor.slice(1)) : 0;
    const last = index === pages.length - 1;
    return { items: pages[index], nextCursor: last ? null : `p${index + 1}`, hasMore: !last };
  };
  return { http, calls };
}

describe('countUnclaimedConsultations', () => {
  it('counts only the open conversations nobody has claimed', async () => {
    const { http, calls } = pagedHttp([rows(3, 2)]);
    expect(await countUnclaimedConsultations(http)).toBe(3);
    expect(calls).toEqual([{ path: '/api/v1/consultations', query: { status: 'OPEN', unclaimed: true, limit: 50 } }]);
  });

  it('walks every page by cursor and adds the unclaimed ones', async () => {
    const { http, calls } = pagedHttp([rows(2, 48), rows(1, 49), rows(4, 1)]);
    expect(await countUnclaimedConsultations(http)).toBe(7);
    expect(calls.map(call => call.query.cursor)).toEqual([undefined, 'p1', 'p2']);
  });

  it('is zero when everything is claimed or there is nothing open', async () => {
    expect(await countUnclaimedConsultations(pagedHttp([rows(0, 5)]).http)).toBe(0);
    expect(await countUnclaimedConsultations(pagedHttp([[]]).http)).toBe(0);
  });

  it('refuses to understate: pages remain and fewer than 100 were found, so the count fails', async () => {
    const pages = Array(CONSULTATION_MAX_PAGES + 2).fill(null).map(() => rows(5, 45));
    const { http, calls } = pagedHttp(pages);
    await expect(countUnclaimedConsultations(http)).rejects.toThrow('CONSULTATION_COUNT_INCOMPLETE');
    expect(calls).toHaveLength(CONSULTATION_MAX_PAGES);
  });

  it('returns a lower bound when pages remain but it already reaches the badge ceiling (shown as 99+)', async () => {
    const pages = Array(CONSULTATION_MAX_PAGES + 2).fill(null).map(() => rows(50));
    expect(await countUnclaimedConsultations(pagedHttp(pages).http)).toBe(50 * CONSULTATION_MAX_PAGES);
  });
});
