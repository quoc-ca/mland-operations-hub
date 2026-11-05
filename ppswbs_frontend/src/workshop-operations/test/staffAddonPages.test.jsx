import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

const session = id => json(200, { id, state: 'IN_PROGRESS', locationId: 4, capacity: 10 });

async function fillForm(user, bookingCode, addOnReference, consentReference = null) {
  await user.type(screen.getByLabelText('Booking code'), bookingCode);
  await user.type(screen.getByLabelText('Catalogue add-on reference'), addOnReference);
  if (consentReference) {
    await user.type(screen.getByLabelText('Customer consent reference'), consentReference);
  }
}

describe('Staff workshop add-on operations screen', () => {
  it('creates a separate bill and starts independently verified payment', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'POST /api/v1/workshop-bookings/BK-100/direct-addon-bills': json(201, {
        billReference: 'ADD-BILL-1', description: 'Extra silver', amountVnd: 150000,
        currency: 'VND', lifecycleStatus: 'PENDING_PAYMENT',
      }),
      'POST /api/v1/workshop-bookings/BK-100/direct-addon-bills/ADD-BILL-1/payment-attempts': json(201, {
        attemptReference: 'ATT-1', paymentUrl: 'https://sandbox.example/pay',
        expiresAt: '2026-11-04T12:10:00Z', status: 'PENDING',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('heading', { name: 'Create separate add-on bill' })).toBeInTheDocument();
    await fillForm(user, 'BK-100', 'ADD-SILVER', 'CONSENT-100');
    await user.click(screen.getByRole('button', { name: 'Create separate add-on bill' }));

    expect(await screen.findByText(/Separate add-on bill ADD-BILL-1 created/)).toBeInTheDocument();
    const request = calls.find(call => call.path.endsWith('/direct-addon-bills'));
    expect(request.body).toEqual({ catalogueAddOnReference: 'ADD-SILVER', customerConsentReference: 'CONSENT-100' });
    expect(request.headers['Idempotency-Key']).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Start add-on payment' }));
    expect(await screen.findByText(/Awaiting verified IPN\/QueryDR evidence/)).toBeInTheDocument();
  });

  it('does not fabricate success when the finalized-bill contract rejects the command', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-2': session('session-2'),
      'POST /api/v1/workshop-bookings/BK-200/direct-addon-bills': json(409, {
        code: 'FINAL_BILL_NOT_FINALIZED', message: 'Final bill is not finalized',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-2' });
    await screen.findByRole('heading', { name: 'Create separate add-on bill' });
    await fillForm(user, 'BK-200', 'ADD-PAID', 'CONSENT-200');
    await user.click(screen.getByRole('button', { name: 'Create separate add-on bill' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Separate add-on bill/)).not.toBeInTheDocument();
  });

  it('requires consent before enabling the add-on command', async () => {
    const calls = mockFetch({ 'GET /api/v1/workshop-sessions/session-3': session('session-3') });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-3' });
    await screen.findByRole('heading', { name: 'Create separate add-on bill' });
    await fillForm(user, 'BK-300', 'ADD-PAID');
    expect(screen.getByText('A customer consent reference is required before creating the bill.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create separate add-on bill' })).toBeDisabled();
    expect(calls.some(call => call.path.includes('/direct-addon-bills'))).toBe(false);
  });

  it('shows an error when the add-on command times out', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-4': session('session-4'),
      'POST /api/v1/workshop-bookings/BK-400/direct-addon-bills': () => Promise.reject(new TypeError('Failed to fetch')),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-4' });
    await screen.findByRole('heading', { name: 'Create separate add-on bill' });
    await fillForm(user, 'BK-400', 'ADD-PAID', 'CONSENT-400');
    await user.click(screen.getByRole('button', { name: 'Create separate add-on bill' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Separate add-on bill/)).not.toBeInTheDocument();
  });
});
