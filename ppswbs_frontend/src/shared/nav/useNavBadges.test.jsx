import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { AuthProvider } from '../auth/AuthProvider';
import { fakeSession, json, mockFetch } from '../../test/render';
import { BADGE_REFRESH_MS, clearNavBadgeCache, useNavBadges } from './useNavBadges';

function Probe({ role }) {
  const badges = useNavBadges(role);
  return <output data-testid="badges">{JSON.stringify(badges)}</output>;
}

const read = () => JSON.parse(screen.getByTestId('badges').textContent);
const mount = (role, session = fakeSession()) => render(<AuthProvider source={session}><Probe role={role} /></AuthProvider>);

const counts = (overrides = {}) => ({
  'GET /api/v1/late-payment-reviews': json(200, [{}, {}, {}]),
  'GET /api/v1/workshop-change-requests': json(200, [{}, {}]),
  'GET /api/v1/design-review-requests': json(200, { items: [{}] }),
  'GET /api/v1/workshop-refund-requests/waiting-count': json(200, { waiting: 4 }),
  'GET /api/v1/workshop-cash-closes/awaiting-confirmation-count': json(200, { waiting: 1 }),
  // One of the two orders is paid and waiting for production.
  'GET /api/v1/custom-orders': json(200, [{ status: 'PAID', productionStatus: 'PENDING' }, { status: 'QUOTE_PENDING' }]),
  'GET /api/v1/consultations': json(200, { items: [{ claimantRole: null }, { claimantRole: 'STAFF' }], nextCursor: null, hasMore: false }),
  ...overrides,
});

describe('useNavBadges', () => {
  beforeEach(() => clearNavBadgeCache());
  afterEach(() => vi.useRealTimers());

  it('gives a Manager the counts by tab and by group', async () => {
    mockFetch(counts());
    mount('MANAGER');
    expect(read()).toEqual({ status: 'loading', counts: {} });
    await waitFor(() => expect(read().status).toBe('ready'));
    expect(read().counts).toEqual({
      'customers/design-review': 1, 'customers/custom-orders-ops': 1, 'customers/consultations': 1, customers: 3,
      'tickets/change-requests': 2, 'tickets/refunds': 4, tickets: 6,
      'collect/cash-close': 1, 'collect/late-payments': 3, collect: 4,
    });
  });

  it('leaves out a count that cannot be read, and the group it would have completed, and stays ready', async () => {
    mockFetch(counts({
      'GET /api/v1/workshop-refund-requests/waiting-count': json(500, { code: 'X' }),
      'GET /api/v1/workshop-cash-closes/awaiting-confirmation-count': json(200, {}),
    }));
    mount('MANAGER');
    await waitFor(() => expect(read().status).toBe('ready'));
    const shown = read().counts;
    expect(shown['tickets/change-requests']).toBe(2);
    expect(shown).not.toHaveProperty('tickets/refunds');
    expect(shown).not.toHaveProperty('tickets');
    expect(shown).not.toHaveProperty('collect/cash-close');
    expect(shown).not.toHaveProperty('collect');
    expect(shown['collect/late-payments']).toBe(3);
  });

  const hour = 60 * 60 * 1000;
  const staffWork = (overrides = {}) => ({
    'GET /api/v1/workshop-sessions': json(200, [
      // Ended an hour ago, attendance not finalized: counts. In progress now: counts. Cancelled: needs nothing.
      { id: 's1', state: 'IN_PROGRESS', scheduledStartAt: new Date(Date.now() - 3 * hour).toISOString(), scheduledEndAt: new Date(Date.now() - hour).toISOString() },
      { id: 's2', state: 'IN_PROGRESS', scheduledStartAt: new Date(Date.now() - hour).toISOString(), scheduledEndAt: new Date(Date.now() + hour).toISOString() },
      { id: 's3', state: 'CANCELLED', scheduledStartAt: new Date(Date.now() + hour).toISOString(), scheduledEndAt: new Date(Date.now() + 2 * hour).toISOString() },
    ]),
    'GET /api/v1/staff/retail-pickups': json(200, [
      { order: { paymentState: 'ACCEPTED_UNPAID' }, fulfilmentState: 'CREATED', pickupReadyConfirmed: false },
      { order: { paymentState: 'PAID' }, fulfilmentState: 'PICKED_UP', pickupReadyConfirmed: true },
    ]),
    'GET /api/v1/staff/retail-shipments': json(200, { items: [{ status: 'PREPARING' }] }),
    'GET /api/v1/workshop-change-requests': json(200, [{}, {}, {}]),
    'GET /api/v1/design-review-requests': json(200, { items: [{}] }),
    // Two of the three open conversations are unclaimed.
    'GET /api/v1/consultations': json(200, { items: [{ claimantRole: null }, { claimantRole: 'STAFF' }, { claimantRole: null }], nextCursor: null, hasMore: false }),
    'GET /api/v1/custom-orders': json(200, [{ status: 'PAID', productionStatus: 'PENDING' }, { status: 'IN_PRODUCTION', productionStatus: 'IN_PRODUCTION' }]),
    ...overrides,
  });

  it('gives a Staff their work counts, by tab and by group, and sends only GET requests', async () => {
    const calls = mockFetch(staffWork());
    mount('STAFF');
    await waitFor(() => expect(read().status).toBe('ready'));
    expect(read().counts).toEqual({
      'workshop/sessions': 2, workshop: 2,
      'retail/orders': 2, retail: 2,
      'tickets/change-requests': 3, tickets: 3,
      'customers/design-review': 1, 'customers/custom-orders-ops': 1, 'customers/consultations': 2, customers: 4,
    });
    expect(calls).toHaveLength(7);
    expect(calls.find(call => call.path === '/api/v1/consultations').query).toEqual({ status: 'OPEN', unclaimed: 'true', limit: '50' });
    expect(calls.find(call => call.path === '/api/v1/workshop-change-requests').query).toEqual({ status: 'PENDING' });
    expect(calls.every(call => call.method === 'GET')).toBe(true);
  });

  it('leaves out the Staff count that cannot be read and keeps the other', async () => {
    mockFetch(staffWork({
      'GET /api/v1/workshop-sessions': json(500, { code: 'X' }),
      'GET /api/v1/design-review-requests': json(403, { code: 'REVIEW_NOT_AUTHORIZED' }),
    }));
    mount('STAFF');
    await waitFor(() => expect(read().status).toBe('ready'));
    // The design queue failed, so the customers group (design review plus consultations) has no count, but its readable tab keeps its own.
    expect(read().counts).toEqual({ 'retail/orders': 2, retail: 2, 'tickets/change-requests': 3, tickets: 3, 'customers/custom-orders-ops': 1, 'customers/consultations': 2 });
  });

  it('gives an Admin or a Carrier empty counts, ready, and reads nothing', async () => {
    const calls = mockFetch(counts());
    mount('ADMIN');
    await waitFor(() => expect(read()).toEqual({ status: 'ready', counts: {} }));
    expect(calls).toHaveLength(0);
  });

  it('stays loading while the role is unknown and when there is no session', async () => {
    const calls = mockFetch(counts());
    mount(null);
    mount('MANAGER', fakeSession('signed-out'));
    await new Promise(resolve => setTimeout(resolve, 30));
    screen.getAllByTestId('badges').forEach(node => expect(JSON.parse(node.textContent)).toEqual({ status: 'loading', counts: {} }));
    expect(calls).toHaveLength(0);
  });

  it('reads once on entry, then again on the interval, and sends only GET requests', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const calls = mockFetch(counts());
    mount('MANAGER');
    await waitFor(() => expect(read().status).toBe('ready'));
    expect(calls).toHaveLength(7);
    await act(async () => { vi.advanceTimersByTime(BADGE_REFRESH_MS + 100); });
    await waitFor(() => expect(calls).toHaveLength(14));
    expect(calls.every(call => call.method === 'GET')).toBe(true);
  });

  it('reuses a fresh cached result when another mount opens within the cache time', async () => {
    const calls = mockFetch(counts());
    const first = mount('MANAGER');
    await waitFor(() => expect(read().status).toBe('ready'));
    first.unmount();
    mount('MANAGER');
    expect(read().status).toBe('ready');
    await new Promise(resolve => setTimeout(resolve, 30));
    expect(calls).toHaveLength(7);
  });
});
