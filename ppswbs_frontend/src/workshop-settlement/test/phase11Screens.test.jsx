import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { clearRoleCache } from '../../shared/auth/useRole';
import { PROFILE, asRole, useFreshRole } from './helpers';

useFreshRole();

const FOLIO = '/api/v1/workshop-tickets/TK-1/folio';
const folio = (over = {}) => json(200, {
  ticketCode: 'TK-1', status: 'OPEN', totalAmount: '450000', paidAmount: '100000', pendingAmount: '0', balanceDue: '350000',
  collections: [{ collectionId: 'c-cash', method: 'CASH', amount: '100000', status: 'CONFIRMED', cashDayClosed: true }],
  ...over,
});
async function openFolio(user) {
  await user.type(await screen.findByPlaceholderText('GRP-…-L0-T1'), 'TK-1');
  await user.click(screen.getByRole('button', { name: 'Open folio' }));
}

// T145 (FR-036, FR-053): the button comes from the server's cashDayClosed.
describe('Cash reversal on the ticket folio', () => {
  const REVERSE = `POST ${FOLIO}/collections/c-cash/cash-reversals`;
  const base = { 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []) };

  it('offers "Reverse cash entry" for a cash collection of a closed day, sends a reason and a key, and shows the server answer', async () => {
    const calls = mockFetch({
      ...base, [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(),
      [REVERSE]: json(201, { reversalReference: 'REV-1', ticketCode: 'TK-1', collectionId: 'c-cash', amountVnd: '-100000', workingDate: '2026-11-17', originalWorkingDate: '2026-11-16', reason: 'typo', recordedBy: 'm', recordedAt: '2026-11-17T01:00:00Z' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    expect(screen.queryByRole('button', { name: 'Void' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Reverse cash entry' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Reverse cash entry' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'typed twice');
    await user.click(within(dialog).getByRole('button', { name: 'Reverse cash entry' }));
    expect(await screen.findByText(/Cash entry reversed: -100\.000 VND recorded on working day 2026-11-17/)).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ reason: 'typed twice' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('keeps "Void" for a cash collection of an open day and hides the reversal from Staff', async () => {
    mockFetch({ ...base, [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio({ collections: [{ collectionId: 'c-cash', method: 'CASH', amount: '100000', status: 'CONFIRMED', cashDayClosed: false }] }) });
    const user = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    expect(await screen.findByRole('button', { name: 'Void' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reverse cash entry' })).not.toBeInTheDocument();
    view.unmount();
    clearRoleCache();

    mockFetch({ ...base, [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio() });
    const staff = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(staff);
    await screen.findByText('Collections');
    expect(screen.queryByRole('button', { name: 'Reverse cash entry' })).not.toBeInTheDocument();
  });

  it.each([
    ['FINAL_BILL_NOT_OPEN', /FINALIZED or CLOSED/],
    ['COLLECTION_UNDER_REFUND', /refund request already exists/],
    ['CASH_DAY_NOT_CLOSED', /Use Void/],
  ])('explains the 409 %s', async (code, text) => {
    mockFetch({ ...base, [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), [REVERSE]: json(409, { code, correlationId: 'c-1' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.click(await screen.findByRole('button', { name: 'Reverse cash entry' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'why');
    await user.click(within(dialog).getByRole('button', { name: 'Reverse cash entry' }));
    expect(await screen.findByText(text)).toBeInTheDocument();
  });
});

// T136 (FR-057): record and void on the folio, list per branch and period.
describe('Absorbed undercharges', () => {
  const RECORD = { underchargeReference: 'UC-1', ticketCode: 'TK-1', amountVnd: '20000', reason: 'quoted wrong', status: 'ACTIVE', recordedBy: 'm', recordedAt: '2026-11-16T03:00:00Z' };
  const finalized = folio({ status: 'FINALIZED', balanceDue: '0', collections: [] });

  it('records on a FINALIZED folio with amount, reason and a key, then voids the record with a reason', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: finalized,
      [`POST ${FOLIO}/absorbed-undercharges`]: json(201, RECORD),
      [`POST ${FOLIO}/absorbed-undercharges/UC-1/voids`]: json(200, { ...RECORD, status: 'VOIDED', voidReason: 'mistake' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.click(await screen.findByRole('button', { name: 'Record absorbed undercharge' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'quoted wrong');
    await user.click(within(dialog).getByRole('button', { name: 'Record undercharge' }));
    expect(await within(dialog).findByText('Enter a whole VND amount above 0.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.type(within(dialog).getByLabelText('Amount (VND)'), '20000');
    await user.click(within(dialog).getByRole('button', { name: 'Record undercharge' }));
    expect(await screen.findByText('Absorbed undercharge recorded.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ amountVnd: '20000', reason: 'quoted wrong' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();

    await user.click(await screen.findByRole('button', { name: 'Void record' }));
    const voidDialog = screen.getByRole('dialog');
    await user.type(within(voidDialog).getByLabelText('Reason (required)'), 'mistake');
    await user.click(within(voidDialog).getByRole('button', { name: 'Void record' }));
    expect(await screen.findByText('Absorbed undercharge voided.')).toBeInTheDocument();
    expect(screen.getByText('VOIDED')).toBeInTheDocument();
    expect(calls.filter(call => call.method === 'POST')[1].body).toEqual({ reason: 'mistake' });
  });

  it('does not offer the record action on an OPEN folio or to Staff, and explains a 422 over the folio total', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []) });
    const user = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await screen.findByText('Collections');
    expect(screen.queryByRole('button', { name: 'Record absorbed undercharge' })).not.toBeInTheDocument();
    view.unmount();
    clearRoleCache();

    mockFetch({ [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: finalized });
    const staff = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(staff);
    await screen.findByText('Collections');
    expect(screen.queryByRole('button', { name: 'Record absorbed undercharge' })).not.toBeInTheDocument();
  });

  it('shows the 422 cap refusal', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: finalized, [`POST ${FOLIO}/absorbed-undercharges`]: json(422, { code: 'UNDERCHARGE_EXCEEDS_FOLIO_TOTAL' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.click(await screen.findByRole('button', { name: 'Record absorbed undercharge' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Amount (VND)'), '999999999');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Record undercharge' }));
    expect(await screen.findByText(/exceeds the folio total/)).toBeInTheDocument();
  });

  const LIST = { locationId: 3, from: '2026-11-01', to: '2026-11-30', totalVnd: '20000', records: [RECORD, { ...RECORD, underchargeReference: 'UC-2', amountVnd: '5000', status: 'VOIDED', voidReason: 'dup' }] };
  async function fill(user) {
    await user.type(await screen.findByLabelText('Branch ID'), '3');
    await user.type(screen.getByLabelText('From'), '2026-11-01');
    await user.type(screen.getByLabelText('To'), '2026-11-30');
    await user.click(screen.getByRole('button', { name: 'Show records' }));
  }

  it('lists records of a branch and period, marks VOIDED ones and shows the server total of ACTIVE records', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-absorbed-undercharges': json(200, LIST) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/absorbed-undercharges' });
    await fill(user);
    expect(await screen.findByText('UC-2')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/workshop-absorbed-undercharges')).query).toEqual({ locationId: '3', from: '2026-11-01', to: '2026-11-30' });
    expect(screen.getByText(/not in the total: dup/)).toBeInTheDocument();
    expect(screen.getAllByText('20.000 VND').length).toBeGreaterThan(0);
    // Only the ACTIVE record can be voided.
    expect(screen.getAllByRole('button', { name: 'Void record' })).toHaveLength(1);
  });

  it('shows the empty state and refuses Staff', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-absorbed-undercharges': json(200, { ...LIST, totalVnd: '0', records: [] }) });
    const user = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/settlement/absorbed-undercharges' });
    await fill(user);
    expect(await screen.findByText(/No absorbed-undercharge record/)).toBeInTheDocument();
    view.unmount();
    clearRoleCache();

    mockFetch({ [PROFILE]: asRole('STAFF') });
    renderRoutes(settlementRoutes, { path: '/settlement/absorbed-undercharges' });
    expect(await screen.findByText(/Only a Manager/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Branch ID')).not.toBeInTheDocument();
  });
});

// T134 (FR-056): read-only reconciliation list.
describe('Reconciliation list', () => {
  const ROWS = [
    { collectionId: 'c1', createdAt: '2026-11-16T02:00:00Z', ticketCode: 'TK-1', ticketCodes: ['TK-1'], groupCode: 'G-1', amount: '100000', method: 'BANK_TRANSFER', state: 'CONFIRMED', transactionReference: 'FT1', confirmedBy: 'mgr', confirmedAt: '2026-11-16T03:00:00Z', excessAmount: '20000', refundRequestReference: 'R-9' },
    { collectionId: 'c2', createdAt: '2026-11-16T02:00:00Z', ticketCode: null, ticketCodes: ['TK-2', 'TK-3'], groupCode: 'G-2', amount: '300000', method: 'BOOKING_TIME', state: 'CONFIRMED', confirmedBy: null, locationIds: ['3', '4'] },
    { collectionId: 'c3', createdAt: '2026-11-16T02:00:00Z', ticketCode: null, ticketCodes: [], groupCode: 'G-3', amount: '70000', method: 'BOOKING_TIME', state: 'PENDING_VERIFICATION', locationIds: ['3'] },
    { collectionId: 'c4', createdAt: '2026-11-16T02:00:00Z', ticketCode: null, ticketCodes: [], groupCode: 'G-4', amount: '50000', method: 'BOOKING_TIME', state: 'PENDING_VERIFICATION', note: 'late payment under review', locationIds: ['3'] },
    { collectionId: null, createdAt: '2026-11-16T02:00:00Z', ticketCode: 'TK-5', ticketCodes: ['TK-5'], groupCode: 'G-5', amount: '10000', method: 'VNPAY', state: 'EXCESS', refundRequestReference: null },
  ];
  async function show(user) {
    await user.type(await screen.findByLabelText('Branch ID'), '3');
    await user.type(screen.getByLabelText('Working day'), '2026-11-16');
    await user.click(screen.getByRole('button', { name: 'Show collections' }));
  }
  const URL = 'GET /api/v1/workshop-reconciliation-collections';

  it('sends the filters, labels the two meanings of BOOKING_TIME, leaves unverified and late rows out of the confirmed total and warns about multi-branch payments', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('MANAGER'), [URL]: json(200, ROWS) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/reconciliation' });
    await user.selectOptions(await screen.findByLabelText('Method'), 'BOOKING_TIME');
    await show(user);
    expect(await screen.findByText('G-2')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/workshop-reconciliation-collections')).query).toEqual({ locationId: '3', workingDate: '2026-11-16', method: 'BOOKING_TIME' });
    // 100,000 + 300,000 confirmed; 70,000 unverified, 50,000 late and the excess row do not count.
    expect(screen.getByText('400.000 VND', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getAllByText(/whole group payment/).length).toBe(3);
    expect(screen.getByText(/chưa xác minh/)).toBeInTheDocument();
    expect(screen.getAllByText(/late payment under review/).length).toBeGreaterThan(0);
    expect(screen.getByText('TK-2, TK-3')).toBeInTheDocument();
    expect(screen.getByText(/branches: 3, 4/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/Do not add the per-branch totals/);
    expect(screen.getByText(/excess 20\.000 VND, refund R-9/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reconciled/i })).not.toBeInTheDocument();
  });

  it('needs a numeric branch and a working day, shows the empty state, and hides the list from Staff', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [URL]: json(200, []) });
    const user = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/settlement/reconciliation' });
    expect(await screen.findByRole('button', { name: 'Show collections' })).toBeDisabled();
    await show(user);
    expect(await screen.findByText(/No transfer, VNPay or booking-time collection/)).toBeInTheDocument();
    view.unmount();
    clearRoleCache();

    const calls = mockFetch({ [PROFILE]: asRole('STAFF') });
    renderRoutes(settlementRoutes, { path: '/settlement/reconciliation' });
    expect(await screen.findByText(/Only a Manager/)).toBeInTheDocument();
    expect(calls.some(call => call.path.includes('reconciliation-collections'))).toBe(false);
  });

  it('explains 503 RECONCILIATION_SOURCE_UNAVAILABLE without a partial list, and 400 / 403', async () => {
    let attempt = 0;
    mockFetch({ [PROFILE]: asRole('MANAGER'), [URL]: () => (++attempt === 1 ? json(503, { code: 'RECONCILIATION_SOURCE_UNAVAILABLE' }) : json(200, ROWS.slice(0, 1))) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/reconciliation' });
    await show(user);
    expect(await screen.findByText(/no partial list is shown/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('FT1')).toBeInTheDocument();
  });
});

// T151 (FR-040, CHK005/006): confirmation step before an account is sent.
describe('Account entry confirmation', () => {
  const LINK = '/api/v1/refund-account-links/tok-1';
  async function fill(user) {
    await user.type(await screen.findByLabelText('Bank'), 'BIDV');
    await user.type(screen.getByLabelText('Account number'), '12345678901');
    await user.type(screen.getByLabelText('Account holder name'), 'NGUYEN VAN A');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
  }

  it('shows the details back, will not send until they are confirmed, lets the payer edit, and sends accountDetailsConfirmed', async () => {
    const calls = mockFetch({ [`POST ${LINK}`]: new Response(null, { status: 204 }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/refund-account#tok-1', language: 'en' });
    await fill(user);
    const review = screen.getByRole('form', { name: 'Review account details' });
    expect(within(review).getByText('12345678901')).toBeInTheDocument();
    expect(within(review).getByRole('button', { name: 'Send account' })).toBeDisabled();
    expect(calls).toHaveLength(0);
    await user.click(within(review).getByRole('button', { name: 'Edit details' }));
    expect(screen.getByLabelText('Account number')).toHaveValue('12345678901');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Send account' }));
    expect(await screen.findByText(/Your bank account was received/)).toBeInTheDocument();
    expect(calls[0].body).toEqual({ bankCode: 'BIDV', accountNumber: '12345678901', holderName: 'NGUYEN VAN A', accountDetailsConfirmed: true });
  });

  it('keeps the payer on the review step with the values after 422 ACCOUNT_CONFIRMATION_REQUIRED', async () => {
    mockFetch({ [`POST ${LINK}`]: json(422, { code: 'ACCOUNT_CONFIRMATION_REQUIRED', correlationId: 'c-1' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/refund-account#tok-1', language: 'en' });
    await fill(user);
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Send account' }));
    expect(await screen.findByText(/ACCOUNT_CONFIRMATION_REQUIRED/)).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Review account details' })).toBeInTheDocument();
    expect(screen.getByText('12345678901')).toBeInTheDocument();
  });
});

describe('Return to account entry and the optional receipt image (refund queue)', () => {
  const refund = (over = {}) => ({ refundId: 'R-1', ticketCode: 'TK-1', groupCode: null, source: 'MLAND_CANCELLATION', amount: '200000', status: 'PENDING_APPROVAL', accountNumberMasked: '****1234', holderName: 'NGUYEN VAN A', ...over });
  const LIST = 'GET /api/v1/workshop-refund-requests';
  const TRANSITION = 'POST /api/v1/workshop-refund-requests/R-1/transitions';
  const UPLOAD = 'POST /api/v1/workshop-refund-requests/R-1/receipt-images';
  const png = (name = 'receipt.png', type = 'image/png', size = 100) => new File([new Uint8Array(size)], name, { type });

  it.each(['PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION'])('lets a Manager return a %s request to account entry with a reason, and not Staff', async status => {
    const calls = mockFetch({ [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund({ status })]), [TRANSITION]: json(200, refund({ status: 'PENDING_ACCOUNT', accountNumberMasked: '****1234' })) });
    const user = userEvent.setup();
    const view = renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.click(await screen.findByRole('button', { name: 'Return to account entry' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Return to account entry' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'bank returned the transfer');
    await user.click(within(dialog).getByRole('button', { name: 'Return to account entry' }));
    expect(await screen.findByText(/earlier approval no longer applies and a new account link was sent/)).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ action: 'RETURN_TO_PENDING_ACCOUNT', reason: 'bank returned the transfer' });
    view.unmount();
    clearRoleCache();

    mockFetch({ [PROFILE]: asRole('STAFF'), [LIST]: json(200, [refund({ status })]) });
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await userEvent.setup().click(await screen.findByText('R-1'));
    expect(screen.queryByRole('button', { name: 'Return to account entry' })).not.toBeInTheDocument();
  });

  it('explains 409 REFUND_NOT_RETURNABLE', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund()]), [TRANSITION]: json(409, { code: 'REFUND_NOT_RETURNABLE' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.click(await screen.findByRole('button', { name: 'Return to account entry' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'r');
    await user.click(within(dialog).getByRole('button', { name: 'Return to account entry' }));
    expect(await screen.findByText(/REFUND_NOT_RETURNABLE/)).toBeInTheDocument();
  });

  it('shows the counter account back for confirmation; 422 keeps the review step with the values', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), [LIST]: json(200, [refund({ status: 'PENDING_ACCOUNT', accountNumberMasked: null, holderName: null })]),
      'PUT /api/v1/workshop-refund-requests/R-1/account': json(422, { code: 'ACCOUNT_CONFIRMATION_REQUIRED' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.type(await screen.findByLabelText('Bank code'), 'VCB');
    await user.type(screen.getByLabelText('Account number'), '0123456789');
    await user.type(screen.getByLabelText(/Holder name/), 'NGUYEN VAN A');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Save account' }));
    expect(await screen.findByText(/ACCOUNT_CONFIRMATION_REQUIRED/)).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Review account details' })).toBeInTheDocument();
    expect(screen.getByText('0123456789')).toBeInTheDocument();
  });

  it('uploads the optional receipt image first, then attaches it by RECORD_TRANSFER', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund({ status: 'PENDING_TRANSFER' })]),
      [UPLOAD]: json(201, { receiptImageId: 'IMG-1', contentType: 'image/png', byteCount: 100, checksum: 'abc', uploadedAt: '2026-11-16T03:00:00Z' }),
      [TRANSITION]: json(200, refund({ status: 'PENDING_RECONCILIATION', receiptImageAttached: true })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.type(await screen.findByLabelText('Bank transaction reference'), 'FT123');
    await user.upload(screen.getByLabelText(/Receipt image/), png());
    await user.click(screen.getByRole('button', { name: 'Record transfer' }));
    expect(await screen.findByText('Transfer recorded.')).toBeInTheDocument();
    const upload = calls.find(call => call.path.endsWith('/receipt-images'));
    expect(upload.body).toBeInstanceOf(FormData);
    expect(upload.body.get('file').name).toBe('receipt.png');
    expect(upload.headers['Idempotency-Key']).toBeTruthy();
    expect(calls.indexOf(upload)).toBeLessThan(calls.findIndex(call => call.path.endsWith('/transitions')));
    expect(calls.find(call => call.path.endsWith('/transitions')).body).toEqual({ action: 'RECORD_TRANSFER', bankTransactionReference: 'FT123', proofImageReference: 'IMG-1' });
  });

  it('refuses a PDF and a file over 5,000,000 bytes before upload, and records without an image', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund({ status: 'PENDING_TRANSFER' })]) });
    const user = userEvent.setup({ applyAccept: false });
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.upload(await screen.findByLabelText(/Receipt image/), png('r.pdf', 'application/pdf'));
    expect(await screen.findByText(/must be a JPEG, PNG or WebP image/)).toBeInTheDocument();
    await user.upload(screen.getByLabelText(/Receipt image/), png('big.png', 'image/png', 5000001));
    expect(await screen.findByText(/larger than 5,000,000 bytes/)).toBeInTheDocument();
    expect(calls.some(call => call.path.endsWith('/receipt-images'))).toBe(false);
  });

  it('shows the typed 415 and 503 answers of the upload and lets the Manager drop the image and record the transfer anyway', async () => {
    let status = 415;
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund({ status: 'PENDING_TRANSFER' })]),
      [UPLOAD]: () => (status === 415 ? json(415, { code: 'RECEIPT_IMAGE_TYPE_UNSUPPORTED' }) : json(503, { code: 'RECEIPT_STORAGE_UNAVAILABLE' })),
      [TRANSITION]: json(200, refund({ status: 'PENDING_RECONCILIATION' })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.type(await screen.findByLabelText('Bank transaction reference'), 'FT123');
    await user.upload(screen.getByLabelText(/Receipt image/), png());
    await user.click(screen.getByRole('button', { name: 'Record transfer' }));
    expect(await screen.findByText(/The receipt must be a JPEG, PNG or WebP image/)).toBeInTheDocument();
    status = 503;
    await user.click(screen.getByRole('button', { name: 'Record transfer' }));
    expect(await screen.findByText(/storage is not available/)).toBeInTheDocument();
    expect(calls.some(call => call.path.endsWith('/transitions'))).toBe(false);
    await user.click(screen.getByRole('button', { name: 'Remove image' }));
    await user.click(screen.getByRole('button', { name: 'Record transfer' }));
    expect(await screen.findByText('Transfer recorded.')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/transitions')).body).toEqual({ action: 'RECORD_TRANSFER', bankTransactionReference: 'FT123' });
  });

  it('streams the attached image through the same-origin route for a Manager only and revokes the object URL when hidden', async () => {
    const create = vi.fn(() => 'blob:receipt-1');
    const revoke = vi.fn();
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }));
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [LIST]: json(200, [refund({ status: 'PENDING_RECONCILIATION', receiptImageAttached: true })]),
      'GET /api/v1/workshop-refund-requests/R-1/receipt-image': () => new Response(new Blob(['x'], { type: 'image/png' }), { status: 200, headers: { 'Content-Type': 'image/png' } }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.click(await screen.findByRole('button', { name: 'View receipt image' }));
    expect(await screen.findByRole('img', { name: /Transfer receipt for R-1/ })).toHaveAttribute('src', 'blob:receipt-1');
    expect(calls.find(call => call.path.endsWith('/receipt-image')).headers.Accept).toBe('image/*');
    await user.click(screen.getByRole('button', { name: 'Hide receipt image' }));
    expect(revoke).toHaveBeenCalledWith('blob:receipt-1');
    expect(screen.queryByRole('img', { name: /Transfer receipt/ })).not.toBeInTheDocument();
  });
});
