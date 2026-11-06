import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';

const bill = status => json(200, {
  bookingCode: 'BK-1', status, currency: 'VND', outstandingAmount: 450000, paidAmount: 450000, refundedAmount: 0, charges: [],
});
const addOns = json(200, [
  { addOnReference: 'ADD-1', description: 'Silver ring box', amountVnd: 120000 },
  { addOnReference: 'ADD-2', description: 'Engraved name tag', amountVnd: 80000 },
]);

async function openBill(user) {
  await user.type(await screen.findByPlaceholderText('BK-...'), 'BK-1');
  await user.click(screen.getByRole('button', { name: 'Open bill' }));
}

describe('Catalogue add-on on the folio', () => {
  it('lists the catalogue add-ons of an OPEN folio and adds the ticked one by reference only', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-bookings/BK-1/final-bill/operator': bill('OPEN'),
      'GET /api/v1/workshop-bookings/BK-1/final-bill/add-ons': addOns,
      'POST /api/v1/workshop-bookings/BK-1/final-bill/charges': bill('OPEN'),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openBill(user);

    const form = await screen.findByRole('form', { name: 'Catalogue add-on' });
    expect(await screen.findByText('Silver ring box')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add selected add-on' })).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: /Engraved name tag/ }));
    // Ticking an add-on alone is not enough: the operator must also confirm that the customer agreed.
    expect(screen.getByRole('button', { name: 'Add selected add-on' })).toBeDisabled();
    await user.click(within(form).getByRole('checkbox', { name: /Khách đã đồng ý/ }));
    await user.click(screen.getByRole('button', { name: 'Add selected add-on' }));

    expect(form).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST' && call.path.endsWith('/final-bill/charges'));
    // The price is never sent: the backend resolves it from the catalogue.
    expect(post.body).toEqual({ catalogueAddOnReference: 'ADD-2', customerConsentConfirmed: true });
    expect(await screen.findByText('Add-on added.')).toBeInTheDocument();
  });

  it('sends a variable charge as a whole-VND number, separate from catalogue add-ons', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-bookings/BK-1/final-bill/operator': bill('OPEN'),
      'GET /api/v1/workshop-bookings/BK-1/final-bill/add-ons': json(200, []),
      'POST /api/v1/workshop-bookings/BK-1/final-bill/charges': bill('OPEN'),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openBill(user);

    expect(await screen.findByText(/No add-on is available/)).toBeInTheDocument();
    const form = screen.getByRole('form', { name: 'Variable charge' });
    await user.type(screen.getByLabelText('Description'), 'Broken tool');
    await user.type(screen.getByLabelText('Amount (VND)'), '50000');
    // A variable charge needs the same "customer agreed" confirmation as a catalogue add-on.
    expect(form.querySelector('button[type="submit"]')).toBeDisabled();
    await user.click(form.querySelector('input[type="checkbox"]'));
    await user.click(form.querySelector('button[type="submit"]'));

    const post = calls.find(call => call.method === 'POST' && call.path.endsWith('/final-bill/charges'));
    expect(post.body).toMatchObject({ description: 'Broken tool', amount: 50000, customerConsentConfirmed: true });
    expect(post.body.catalogueAddOnReference).toBeUndefined();
  });

  it('offers no catalogue add-on once the folio is finalized', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-bookings/BK-1/final-bill/operator': bill('FINALIZED'),
      'GET /api/v1/workshop-bookings/BK-1/final-bill/add-ons': addOns,
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openBill(user);

    await screen.findByText('BK-1');
    expect(screen.queryByRole('form', { name: 'Catalogue add-on' })).not.toBeInTheDocument();
    expect(calls.some(call => call.path.endsWith('/final-bill/add-ons'))).toBe(false);
  });
});
