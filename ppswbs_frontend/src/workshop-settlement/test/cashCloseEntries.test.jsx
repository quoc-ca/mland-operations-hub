import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

useFreshRole();

const LOCATIONS = [{ id: 3, name: 'MLAND Tây Hồ' }, { id: 4, name: 'MLAND Hoàn Kiếm' }];
const expectedFor = call => (call.query.locationId === '3'
  ? json(200, {
    locationId: 3, workingDate: '2026-11-27', expectedCashVnd: 150000,
    entries: [
      { type: 'COLLECTION', ticketCode: 'TK-1', amountVnd: 200000, recordedAt: '2026-11-27T02:00:00Z' },
      { type: 'REVERSAL', ticketCode: 'TK-2', amountVnd: -50000, recordedAt: '2026-11-27T03:00:00Z' },
    ],
  })
  : json(200, { locationId: 4, workingDate: '2026-11-27', expectedCashVnd: 0, entries: [] }));

/** 003.3 FR-036a / FR-036b: the figure and its entries are there on opening and follow the branch. */
describe('Cash close expected cash', () => {
  it('shows the figure and its entries on opening, then reads the other branch and drops the draft count', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-locations': json(200, LOCATIONS),
      'GET /api/v1/workshop-cash-closes/expected-cash': expectedFor,
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close' });

    const table = await screen.findByRole('table', { name: 'Cash entries of the day' });
    expect(within(table).getByText('TK-1')).toBeInTheDocument();
    expect(within(table).getByText('200,000 ₫')).toBeInTheDocument();
    expect(within(table).getByText('TK-2 · Reversal')).toBeInTheDocument();
    expect(within(table).getByText('-50,000 ₫')).toBeInTheDocument();
    expect(screen.getByText('150,000 ₫')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Counted cash (VND)'), '150000');

    await user.selectOptions(screen.getByLabelText('Branch'), '4');
    expect(await screen.findByText('No cash entry on this day.')).toBeInTheDocument();
    expect(screen.queryByText('TK-1')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Counted cash (VND)')).toHaveValue('');
    expect(calls.filter(call => call.path.endsWith('/expected-cash')).map(call => call.query.locationId)).toEqual(['3', '4']);
    expect(calls.some(call => call.method === 'POST')).toBe(false);
  });

  it('shows no figure when the read fails and offers a retry', async () => {
    let fail = true;
    mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-locations': json(200, LOCATIONS),
      'GET /api/v1/workshop-cash-closes/expected-cash': call => (fail ? json(503, { code: 'DEPENDENCY_UNAVAILABLE' }) : expectedFor(call)),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close' });

    const retry = await screen.findByRole('button', { name: /try again/i });
    expect(screen.queryByLabelText('Counted cash (VND)')).not.toBeInTheDocument();
    fail = false;
    await user.click(retry);
    expect(await screen.findByText('150,000 ₫')).toBeInTheDocument();
  });
});
