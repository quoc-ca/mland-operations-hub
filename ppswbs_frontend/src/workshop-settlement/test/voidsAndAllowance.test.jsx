import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

useFreshRole();

const FOLIO = '/api/v1/workshop-tickets/TK-1/folio';
const folio = (status = 'OPEN') => json(200, {
  ticketCode: 'TK-1', status, totalAmount: '200000', paidAmount: '100000', pendingAmount: '0', balanceDue: '100000',
  collections: [{ collectionId: 'c-cash', method: 'CASH', amount: '100000', status: 'CONFIRMED' }],
  charges: [
    { chargeReference: 'CH-1', description: 'Ring box', amount: '100000', status: 'CONFIRMED' },
    { chargeReference: 'CH-2', description: 'Wrong item', amount: '30000', status: 'VOIDED' },
  ],
});

async function openFolio(user) {
  await user.type(await screen.findByPlaceholderText('GRP-…-L0-T1'), 'TK-1');
  await user.click(screen.getByRole('button', { name: 'Open bill' }));
}

describe('Manager void of a folio line or collection (FR-053)', () => {
  it('voids a line only after a confirmation with a reason, sending an Idempotency-Key', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []),
      [`POST ${FOLIO}/lines/CH-1/voids`]: folio(),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await openFolio(user);
    const lines = await screen.findByRole('table', { name: 'Bill lines' });
    expect(within(lines).getAllByRole('button', { name: 'Void line' })).toHaveLength(1); // the VOIDED line has none
    await user.click(within(lines).getByRole('button', { name: 'Void line' }));
    const dialog = screen.getByRole('dialog');
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Entered twice');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('Line voided.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ reason: 'Entered twice' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('voids a collection and explains a refusal for a cash day that is already closed (409)', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []),
      [`POST ${FOLIO}/collections/c-cash/voids`]: json(409, { code: 'CASH_DAY_CLOSED', correlationId: 'c-v' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await openFolio(user);
    const cashRow = (await screen.findByText('Cash', { selector: 'td' })).closest('tr');
    await user.click(within(cashRow).getByRole('button', { name: 'Void' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Wrong amount typed');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText(/Reload the latest data/)).toBeInTheDocument();
    expect(screen.getByText(/CASH_DAY_CLOSED/)).toBeInTheDocument();
  });

  it('offers no void to Staff, and none once the folio is finalized', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []) });
    const user = userEvent.setup();
    const { unmount } = renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await openFolio(user);
    await screen.findByRole('table', { name: 'Bill lines' });
    expect(screen.queryByRole('button', { name: 'Void line' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Void' })).not.toBeInTheDocument();
    unmount();

    mockFetch({ [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio('FINALIZED') });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await openFolio(userEvent.setup());
    await screen.findByRole('table', { name: 'Bill lines' });
    expect(screen.queryByRole('button', { name: 'Void line' })).not.toBeInTheDocument();
  });
});

describe('Manager extra change allowance (FR-054)', () => {
  it('grants one extra change with a reason and an Idempotency-Key, and says the change is not performed', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'POST /api/v1/workshop-tickets/TK-1/change-allowances': json(201, { allowanceReference: 'AL-1', ticketCode: 'TK-1', reason: 'Goodwill', grantedAt: '2026-11-15T01:00:00Z' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    const panel = await screen.findByRole('region', { name: 'Extra change allowance' });
    await user.type(within(panel).getByLabelText('Ticket code'), 'TK-1');
    await user.click(within(panel).getByRole('button', { name: 'Grant extra change' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Grant' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Goodwill');
    await user.click(within(dialog).getByRole('button', { name: 'Grant' }));
    expect(await screen.findByText(/One extra change granted for TK-1 \(AL-1\)/)).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ reason: 'Goodwill' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('is not shown to Staff', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF') });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    await screen.findByLabelText('Booking code');
    expect(screen.queryByRole('region', { name: 'Extra change allowance' })).not.toBeInTheDocument();
  });
});
