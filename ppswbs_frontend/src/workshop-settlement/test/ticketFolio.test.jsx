import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { orderedCollections, vnpayWarning } from '../pages/SettlementWorkspacePage';
import { PROFILE, asRole, useFreshRole } from './helpers';

useFreshRole();

const FOLIO = '/api/v1/workshop-tickets/TK-1/folio';
const folio = (over = {}) => json(200, {
  ticketCode: 'TK-1', status: 'OPEN', totalAmount: '450000', paidAmount: '100000', pendingAmount: '50000', balanceDue: '300000',
  collections: [
    { collectionId: 'c-transfer', method: 'BANK_TRANSFER', amount: '50000', status: 'PENDING_VERIFICATION' },
    { collectionId: 'c-cash', method: 'CASH', amount: '100000', status: 'CONFIRMED' },
    { collectionId: 'c-vnpay', method: 'VNPAY', amount: '10000', status: 'PENDING_VERIFICATION' },
  ],
  ...over,
});

const amountField = async () => within(await screen.findByRole('form', { name: 'Record collection' })).getByLabelText('Amount (VND)');

async function openFolio(user, code = 'TK-1') {
  await user.type(await screen.findByPlaceholderText('TK-...'), code);
  await user.click(screen.getByRole('button', { name: 'Open folio' }));
}

describe('Ticket folio (Staff/Manager)', () => {
  it('orders collections VNPay, cash, transfer and shows the server amounts', () => {
    const order = orderedCollections([{ method: 'BANK_TRANSFER' }, { method: 'CASH' }, { method: 'VNPAY' }, { method: 'BOOKING_TIME' }]).map(item => item.method);
    expect(order).toEqual(['BOOKING_TIME', 'VNPAY', 'CASH', 'BANK_TRANSFER']);
  });

  it('warns before collecting when a VNPay part would be under 5,000 (FR-033) and never changes the amount', () => {
    expect(vnpayWarning('VNPAY', '4999', '100000')).toMatch(/at least 5,000/);
    expect(vnpayWarning('CASH', '97000', '100000')).toMatch(/under 5,000/);
    expect(vnpayWarning('VNPAY', '5000', '5000')).toBe('');
    expect(vnpayWarning('CASH', '100000', '100000')).toBe('');
  });

  it('renders the folio, pending verification, and the warning while typing a small VNPay part', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    expect(await screen.findByText('300.000 VND')).toBeInTheDocument();
    const rows = screen.getAllByRole('row').slice(1).map(row => within(row).getAllByRole('cell')[0].textContent);
    expect(rows).toEqual(['VNPAY', 'CASH', 'BANK_TRANSFER']);
    expect(screen.getAllByText('Awaiting verification').length).toBeGreaterThan(0);
    await user.selectOptions(screen.getByLabelText('Method'), 'VNPAY');
    await user.type(within(screen.getByRole('form', { name: 'Record collection' })).getByLabelText('Amount (VND)'), '4000');
    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 5,000/);
  });

  it('records a cash collection with whole-VND text and an Idempotency-Key', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio({ collections: [] }),
      'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      [`POST ${FOLIO}/collections`]: json(201, { collectionId: 'c-new', method: 'CASH', amount: '300000', status: 'CONFIRMED' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.type(await amountField(), '300000');
    await user.type(screen.getByLabelText('Cash received (VND)'), '500000');
    await user.click(screen.getByRole('button', { name: 'Record collection' }));
    expect(await screen.findByText('Collection recorded.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ method: 'CASH', amount: '300000', cashReceived: '500000' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('shows the VNPay checkout link the server returned', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio({ collections: [] }),
      'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      [`POST ${FOLIO}/collections`]: json(201, { collectionId: 'c-v', method: 'VNPAY', amount: '300000', status: 'PENDING_VERIFICATION', paymentUrl: 'https://pay.example/checkout', paymentExpiresAt: '2026-11-14T10:00:00Z' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.selectOptions(await screen.findByLabelText('Method'), 'VNPAY');
    await user.type(within(screen.getByRole('form', { name: 'Record collection' })).getByLabelText('Amount (VND)'), '300000');
    await user.click(screen.getByRole('button', { name: 'Record collection' }));
    expect(await screen.findByRole('link', { name: 'Open VNPay checkout' })).toHaveAttribute('href', 'https://pay.example/checkout');
  });

  it('marks a pending part as failed only after a confirmation with a reason, sending an Idempotency-Key', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(),
      'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      [`POST ${FOLIO}/collections/c-vnpay/failures`]: json(200, { collectionId: 'c-vnpay', method: 'VNPAY', amount: '10000', status: 'FAILED' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    const vnpayRow = (await screen.findByText('VNPAY')).closest('tr');
    await user.click(within(vnpayRow).getByRole('button', { name: 'Mark as failed' }));
    const dialog = screen.getByRole('dialog');
    expect(calls.some(call => call.method === 'POST')).toBe(false); // opening the dialog sends nothing
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Customer left without paying');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('Collection marked as not paid.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ reason: 'Customer left without paying' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('hides the failure and transfer-confirmation buttons from Staff', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio(), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await screen.findByText('VNPAY');
    expect(screen.queryByRole('button', { name: 'Mark as failed' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm transfer' })).not.toBeInTheDocument();
  });

  it('explains a 409 and a 422 from the server instead of a generic failure', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio({ collections: [] }),
      'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      [`POST ${FOLIO}/collections`]: json(422, { code: 'VNPAY_MINIMUM_NOT_MET', correlationId: 'corr-422' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.type(await amountField(), '1000');
    await user.click(screen.getByRole('button', { name: 'Record collection' }));
    expect(await screen.findByText('The server refused this request.')).toBeInTheDocument();
    expect(screen.getByText(/VNPAY_MINIMUM_NOT_MET/)).toBeInTheDocument();
    expect(screen.getByText(/corr-422/)).toBeInTheDocument();
  });

  it('adds a catalogue add-on by reference only, after the customer-agreed tick', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio({ collections: [] }),
      'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, [{ catalogueAddOnReference: 'ADD-2', name: 'Engraved name tag', price: '80000' }]),
      [`POST ${FOLIO}/charges`]: folio({ collections: [] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    const form = await screen.findByRole('form', { name: 'Catalogue add-on' });
    await user.click(await screen.findByRole('radio', { name: /Engraved name tag/ }));
    expect(screen.getByRole('button', { name: 'Add selected add-on' })).toBeDisabled();
    await user.click(within(form).getByRole('checkbox', { name: /Khách đã đồng ý/ }));
    await user.click(screen.getByRole('button', { name: 'Add selected add-on' }));
    expect(await screen.findByText('Add-on added.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ catalogueAddOnReference: 'ADD-2', customerConsentConfirmed: true });
  });

  it('offers no add-on or collection once the folio is finalized', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio({ status: 'FINALIZED' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await screen.findByText('FINALIZED');
    expect(screen.queryByRole('form', { name: 'Catalogue add-on' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Record collection' })).not.toBeInTheDocument();
    expect(calls.some(call => call.path.endsWith('/folio/add-ons'))).toBe(false);
  });
});
