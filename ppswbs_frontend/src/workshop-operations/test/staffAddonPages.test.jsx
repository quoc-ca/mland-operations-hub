import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// The folio correction panel is a Manager panel: it appears once the profile says the viewer is a Manager.
const asManager = handlers => mockFetch({
  'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }), 'GET /api/v1/accounts': json(200, { data: [] }), ...handlers,
});
beforeEach(() => clearRoleCache());

const session = id => json(200, { id, state: 'IN_PROGRESS', locationId: 4, capacity: 10 });

async function fillForm(user, bookingCode, amountVnd, reason = null) {
  await user.type(screen.getByLabelText('Ticket code (bill to correct)'), bookingCode);
  await user.type(screen.getByLabelText('Overcharge refund amount (VND)'), amountVnd);
  if (reason) {
    await user.type(screen.getByLabelText('Correction reason'), reason);
  }
}

describe('Manager folio correction operations screen', () => {
  it('creates a folio correction for finalized direct booking', async () => {
    const calls = asManager({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'POST /api/v1/workshop-tickets/TK-100/folio/corrections': json(201, {
        correctionReference: 'CORR-1', ticketCode: 'TK-100', overchargeAmount: '50000',
        refundId: 'R-1', status: 'PENDING_PROVIDER', refundStatus: 'PENDING_ACCOUNT',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('heading', { name: 'Manager bill correction' })).toBeInTheDocument();
    await fillForm(user, 'TK-100', '50000', 'Counter overcharge');
    await user.click(screen.getByRole('button', { name: 'Submit bill correction' }));

    expect(await screen.findByText(/Bill correction CORR-1 created/)).toBeInTheDocument();
    const request = calls.find(call => call.path.endsWith('/folio/corrections'));
    expect(request.body).toEqual({ overchargeAmount: '50000', reason: 'Counter overcharge' });
    expect(request.headers['Idempotency-Key']).toBeTruthy();
    expect(screen.getByText(/paid by bank transfer after a Manager approves it/)).toBeInTheDocument();
  });

  it('opens the read-only view of the correction just created (T127)', async () => {
    const calls = asManager({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'POST /api/v1/workshop-tickets/TK-100/folio/corrections': json(201, {
        correctionReference: 'CORR-1', ticketCode: 'TK-100', overchargeAmount: '50000', status: 'PENDING_PROVIDER',
      }),
      'GET /api/v1/workshop-tickets/TK-100/folio/corrections/CORR-1': json(200, {
        correctionReference: 'CORR-1', ticketCode: 'TK-100', overchargeAmount: '50000', refundId: 'R-1',
        status: 'PENDING_PROVIDER', refundStatus: 'PENDING_ACCOUNT',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await screen.findByRole('heading', { name: 'Manager bill correction' });
    await fillForm(user, 'TK-100', '50000', 'Counter overcharge');
    await user.click(screen.getByRole('button', { name: 'Submit bill correction' }));
    await user.click(await screen.findByRole('button', { name: 'View correction' }));

    expect(await screen.findByLabelText('Bill correction')).toHaveTextContent('Pending account');
    expect(calls.some(call => call.method === 'GET' && call.path.endsWith('/folio/corrections/CORR-1'))).toBe(true);
  });

  it('does not fabricate success when backend rejects the command', async () => {
    asManager({
      'GET /api/v1/workshop-sessions/session-2': session('session-2'),
      'POST /api/v1/workshop-tickets/TK-200/folio/corrections': json(409, {
        code: 'FINAL_BILL_NOT_FINALIZED', message: 'Final bill is not finalized',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-2' });
    await screen.findByRole('heading', { name: 'Manager bill correction' });
    await fillForm(user, 'TK-200', '30000', 'Overcharge mistake');
    await user.click(screen.getByRole('button', { name: 'Submit bill correction' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Bill correction CORR-/)).not.toBeInTheDocument();
  });

  it('requires reason before enabling the folio correction command', async () => {
    const calls = asManager({ 'GET /api/v1/workshop-sessions/session-3': session('session-3') });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-3' });
    await screen.findByRole('heading', { name: 'Manager bill correction' });
    await fillForm(user, 'TK-300', '20000');
    expect(screen.getByText('A manager correction reason is required before submitting.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit bill correction' })).toBeDisabled();
    expect(calls.some(call => call.path.includes('/folio/corrections'))).toBe(false);
  });

  it('shows an error when the folio correction command times out', async () => {
    asManager({
      'GET /api/v1/workshop-sessions/session-4': session('session-4'),
      'POST /api/v1/workshop-tickets/TK-400/folio/corrections': () => Promise.reject(new TypeError('Failed to fetch')),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-4' });
    await screen.findByRole('heading', { name: 'Manager bill correction' });
    await fillForm(user, 'TK-400', '20000', 'Refund reason');
    await user.click(screen.getByRole('button', { name: 'Submit bill correction' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Bill correction CORR-/)).not.toBeInTheDocument();
  });
});
