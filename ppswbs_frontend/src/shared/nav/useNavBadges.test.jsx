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
  'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(200, { waiting: 2 }),
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
      'customers/design-review': 1, customers: 1,
      'tickets/change-requests': 2, 'tickets/refunds': 4, tickets: 6,
      'collect/cash-close': 1, 'collect/late-payments': 3, collect: 4,
      'ota/reconciliation': 2, ota: 2,
    });
  });

  it('leaves out a count that cannot be read, and the group it would have completed, and stays ready', async () => {
    mockFetch(counts({
      'GET /api/v1/workshop-refund-requests/waiting-count': json(500, { code: 'X' }),
      'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(200, {}),
    }));
    mount('MANAGER');
    await waitFor(() => expect(read().status).toBe('ready'));
    const shown = read().counts;
    expect(shown['tickets/change-requests']).toBe(2);
    expect(shown).not.toHaveProperty('tickets/refunds');
    expect(shown).not.toHaveProperty('tickets');
    expect(shown).not.toHaveProperty('ota/reconciliation');
    expect(shown).not.toHaveProperty('ota');
    expect(shown.collect).toBe(4);
  });

  it('gives a Staff empty counts, ready, and reads nothing', async () => {
    const calls = mockFetch(counts());
    mount('STAFF');
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
    expect(calls).toHaveLength(6);
    await act(async () => { vi.advanceTimersByTime(BADGE_REFRESH_MS + 100); });
    await waitFor(() => expect(calls).toHaveLength(12));
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
    expect(calls).toHaveLength(6);
  });
});
