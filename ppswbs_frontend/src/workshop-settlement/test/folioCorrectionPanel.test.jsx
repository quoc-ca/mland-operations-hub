import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

useFreshRole();

// 003.3 FR-065: the correction panel sits on the folio of the open ticket, for a Manager, once the folio is FINALIZED.
function open({ role = 'MANAGER', status = 'FINALIZED', ...handlers } = {}) {
  const calls = mockFetch({
    [PROFILE]: asRole(role),
    'GET /api/v1/workshop-tickets/TK-100/folio': json(200, {
      ticketCode: 'TK-100', status, totalAmount: '450000', paidAmount: '450000', pendingAmount: '0', balanceDue: '0', collections: [],
    }),
    'GET /api/v1/workshop-tickets/TK-100/folio/add-ons': json(200, []),
    ...handlers,
  });
  renderRoutes(settlementRoutes, { language: 'en', path: '/settlement?ticket=TK-100' });
  return calls;
}

async function fillForm(user, amountVnd, reason = null) {
  await user.type(await screen.findByLabelText('Overcharge refund amount (VND)'), amountVnd);
  if (reason) await user.type(screen.getByLabelText('Correction reason'), reason);
}

const submit = user => user.click(screen.getByRole('button', { name: 'Submit bill correction' }));
const CREATE = 'POST /api/v1/workshop-tickets/TK-100/folio/corrections';
const created = json(201, {
  correctionReference: 'CORR-1', ticketCode: 'TK-100', overchargeAmount: '50000',
  refundId: 'R-1', status: 'PENDING_PROVIDER', refundStatus: 'PENDING_ACCOUNT',
});

describe('Manager folio correction on the ticket folio', () => {
  it('corrects the open ticket without asking for its code', async () => {
    const calls = open({ [CREATE]: created });
    const user = userEvent.setup();
    await fillForm(user, '50000', 'Counter overcharge');
    expect(screen.queryByLabelText('Ticket code (bill to correct)')).not.toBeInTheDocument();
    await submit(user);

    expect(await screen.findByText(/Bill correction CORR-1 created/)).toBeInTheDocument();
    const request = calls.find(call => call.path.endsWith('/folio/corrections'));
    expect(request.body).toEqual({ overchargeAmount: '50000', reason: 'Counter overcharge' });
    expect(request.headers['Idempotency-Key']).toBeTruthy();
    expect(screen.getByText(/paid by bank transfer after a Manager approves it/)).toBeInTheDocument();
  });

  it.each([
    ['a Staff on a finalized folio', { role: 'STAFF' }],
    ['an open folio', { status: 'OPEN' }],
    ['a closed folio', { status: 'CLOSED' }],
  ])('offers no correction to %s', async (_, options) => {
    open(options);
    expect(await screen.findByRole('heading', { name: 'TK-100' })).toBeInTheDocument();
    // Give the profile time to arrive before concluding the panel is absent.
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('heading', { name: 'Manager bill correction' })).not.toBeInTheDocument();
  });

  it('opens the read-only view of the correction just created (T127)', async () => {
    const calls = open({
      [CREATE]: created,
      'GET /api/v1/workshop-tickets/TK-100/folio/corrections/CORR-1': json(200, {
        correctionReference: 'CORR-1', ticketCode: 'TK-100', overchargeAmount: '50000', refundId: 'R-1',
        status: 'PENDING_PROVIDER', refundStatus: 'PENDING_ACCOUNT',
      }),
    });
    const user = userEvent.setup();
    await fillForm(user, '50000', 'Counter overcharge');
    await submit(user);
    // The page's own lookup has a button of the same name; take the one under the new correction.
    const result = (await screen.findByText('Correction: CORR-1')).closest('.folio-correction-result');
    await user.click(within(result).getByRole('button', { name: 'View correction' }));

    expect(await screen.findByLabelText('Bill correction')).toHaveTextContent('Pending account');
    expect(calls.some(call => call.method === 'GET' && call.path.endsWith('/folio/corrections/CORR-1'))).toBe(true);
  });

  it.each([
    ['rejects the command', json(409, { code: 'FOLIO_CORRECTION_ALREADY_IN_PROGRESS', message: 'Already in progress' })],
    ['times out', () => Promise.reject(new TypeError('Failed to fetch'))],
  ])('does not fabricate success when the backend %s', async (_, answer) => {
    open({ [CREATE]: answer });
    const user = userEvent.setup();
    await fillForm(user, '30000', 'Overcharge mistake');
    await submit(user);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Bill correction CORR-/)).not.toBeInTheDocument();
  });

  it('requires a reason before enabling the command', async () => {
    const calls = open();
    const user = userEvent.setup();
    await fillForm(user, '20000');
    expect(screen.getByText('A manager correction reason is required before submitting.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit bill correction' })).toBeDisabled();
    expect(calls.some(call => call.path.includes('/folio/corrections'))).toBe(false);
  });
});
