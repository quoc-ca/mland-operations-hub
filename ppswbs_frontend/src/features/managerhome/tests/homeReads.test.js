import { describe, expect, it, vi } from 'vitest';
import { createHomeReads } from '../infrastructure/homeReads';

describe('homeReads', () => {
  it('counts the items of the owner lists, bare or wrapped', async () => {
    const http = vi.fn(async path => (path.includes('design') ? { items: [1, 2] } : [1, 2, 3]));
    const reads = createHomeReads(http);
    expect(await reads.countLatePayments()).toBe(3);
    expect(await reads.countChangeRequests()).toBe(3);
    expect(await reads.countDesignReviews()).toBe(2);
  });

  it('counts sessions of the day with cancelled ones included and counted apart', async () => {
    const http = vi.fn(async () => [{ state: 'PUBLISHED' }, { state: 'CANCELLED' }, { state: 'PUBLISHED' }]);
    const strip = await createHomeReads(http).fetchSessionsOfDay('2026-11-18', 'l1');
    expect(strip).toEqual({ date: '2026-11-18', sessions: 3, cancelledSessions: 1 });
    expect(http).toHaveBeenCalledWith('/api/v1/workshop-sessions', { query: { from: '2026-11-18', to: '2026-11-18', locationId: 'l1' } });
  });

  it('never lets an audit reason or metadata through', async () => {
    const http = vi.fn(async () => ({ items: [{ id: 'e', occurredAt: 't', actor: { accountId: 5, displayName: 'A' }, action: 'X', reason: 'private', metadata: { a: 1 } }] }));
    const [row] = await createHomeReads(http).fetchOperationsDenials('since');
    expect(row).toEqual({ id: 'e', time: 't', actor: 'A #5', kind: 'REFUSED_ATTEMPT', source: 'operations', action: 'X' });
    expect(JSON.stringify(row)).not.toMatch(/private|metadata/);
  });

});
