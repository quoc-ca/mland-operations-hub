import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

useFreshRole();

const VERIFY = '/api/v1/workshop-booking-groups/GRP-1/buyer-verifications';
const verifyHandlers = {
  [`POST ${VERIFY}`]: json(200, { verificationId: 'V-1' }),
  [`POST ${VERIFY}/V-1/confirmations`]: json(200, { buyerVerificationReference: 'proof-ref-1' }),
};
const S1 = '7c1f0c2e-0000-4000-8000-000000000001';
const F1 = '7c1f0c2e-0000-4000-8000-0000000000f1';

/** GET change-options of one ticket: its session F1 can move to S1 (4 seats left). */
const optionsOf = (code, over = {}) => json(200, {
  ticketCode: code, type: 'ADVANCE', allowance: { used: 0, total: 1, managerGranted: 0, left: 1, exhausted: false },
  windowOpen: true, canSelfServe: true,
  perFromSession: [{
    fromSessionReference: F1, start: '2026-12-01T02:00:00Z', end: '2026-12-01T04:00:00Z', sourceChangeable: true,
    options: [{ sessionReference: S1, start: '2026-12-03T02:00:00Z', end: '2026-12-03T04:00:00Z', remainingCapacity: 4,
      selectable: true, requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0', ...over }],
  }],
});

/** Staff picks the replacement from the list instead of typing identifiers, then adds the ticket to the change. */
async function addTicketToChange(user, code) {
  await user.type(screen.getByLabelText('Ticket code to change'), code);
  await user.click(screen.getByRole('button', { name: 'Show sessions' }));
  const fromRadio = (await screen.findAllByRole('radio')).find(radio => radio.getAttribute('name') === `from-${code}`);
  await user.click(fromRadio);
  const toRadio = (await screen.findAllByRole('radio')).find(radio => radio.getAttribute('name') === `to-${code}`);
  await user.click(toRadio);
  await user.click(screen.getByRole('button', { name: 'Add ticket to the change' }));
}

async function verifyBuyer(user) {
  await user.type(screen.getByLabelText('Contact email'), 'buyer@example.com');
  await user.click(screen.getByRole('button', { name: 'Send code' }));
  await user.type(await screen.findByLabelText('Code (6 digits)'), '123456');
  await user.click(screen.getByRole('button', { name: 'Verify' }));
  await screen.findByText(/Buyer verified/);
}

describe('Staff ticket cancellation and change', () => {
  it('keeps Change tickets disabled until a ticket with a chosen session is added, and shows the 422 refusal', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'GET /api/v1/workshop-tickets/TK-1/change-options': optionsOf('TK-1'),
      'POST /api/v1/workshop-tickets/TK-1/changes': json(422, { code: 'SETTLEMENT_VALIDATION_ERROR', correlationId: 'c-422' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await user.type(screen.getByLabelText('Reason (required)'), 'x');
    await verifyBuyer(user);
    expect(screen.getByRole('button', { name: 'Change tickets' })).toBeDisabled(); // nothing chosen yet
    await addTicketToChange(user, 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Change tickets' }));
    expect(await screen.findByText('The server refused this request.')).toBeInTheDocument();
    expect(calls.filter(call => call.path.endsWith('/changes'))).toHaveLength(1);
    expect(calls.find(call => call.path.endsWith('/change-options')).query).toEqual({ type: 'ADVANCE' });
  });

  it('shows the seats left and marks a full session as not selectable, and a Manager-only choice as such', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-tickets/TK-1/change-options': optionsOf('TK-1', { remainingCapacity: 0, selectable: false, reasonCode: 'INSUFFICIENT_CAPACITY' }),
      'GET /api/v1/workshop-tickets/TK-2/change-options': optionsOf('TK-2', { requiresManager: true, managerRules: ['RESCHEDULE_QUOTA_EXHAUSTED'] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Ticket code to change'), 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Show sessions' }));
    await user.click((await screen.findAllByRole('radio'))[0]);
    const full = (await screen.findAllByRole('radio')).find(radio => radio.getAttribute('name') === 'to-TK-1');
    expect(full).toBeDisabled();
    expect(screen.getByText(/Full/)).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Ticket code to change'));
    await user.type(screen.getByLabelText('Ticket code to change'), 'TK-2');
    await user.click(screen.getByRole('button', { name: 'Show sessions' }));
    await user.click((await screen.findAllByRole('radio'))[0]);
    expect(await screen.findByText(/4 seats left/)).toBeInTheDocument();
    expect(screen.getByText(/Needs a Manager to approve/)).toBeInTheDocument();
    await user.click((await screen.findAllByRole('radio')).find(radio => radio.getAttribute('name') === 'to-TK-2'));
    expect(screen.getByRole('note')).toHaveTextContent('queued');
  });

  it('cancels several tickets of a group all or none after verification, a reason and a confirmation', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'POST /api/v1/workshop-booking-groups/GRP-1/cancellations': json(201, { groupCode: 'GRP-1', tickets: [
        { ticketCode: 'TK-1', benefitOutcome: 'VALID_CANCELLATION', pointsReturned: 10, voucherRestored: false },
        { ticketCode: 'TK-2', benefitOutcome: 'LATE_CANCELLATION', pointsReturned: 0, voucherRestored: true },
      ] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=cancel' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await user.type(screen.getByLabelText('Ticket codes'), 'TK-1, TK-2');
    await user.type(screen.getByLabelText('Reason (required)'), 'Customer cannot attend');
    const cancel = screen.getByRole('button', { name: 'Cancel selected tickets' });
    expect(cancel).toBeDisabled(); // no buyer proof yet
    await verifyBuyer(user);
    await user.click(cancel);
    expect(calls.some(call => call.path.endsWith('/cancellations'))).toBe(false); // the dialog sends nothing
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel tickets' }));
    const result = await screen.findByLabelText('Cancellation result');
    expect(within(result).getByText(/TK-1: valid cancellation/)).toBeInTheDocument();
    expect(within(result).getByText(/TK-2: late cancellation.*voucher restored/)).toBeInTheDocument();
    const post = calls.find(call => call.path.endsWith('/cancellations'));
    expect(post.body).toEqual({ reason: 'Customer cannot attend', buyerVerificationReference: 'proof-ref-1', ticketCodes: ['TK-1', 'TK-2'] });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
    expect(JSON.stringify(calls.map(call => call.path))).not.toContain('proof-ref-1');
  });

  it('lists the tickets that blocked a group cancellation (409)', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'POST /api/v1/workshop-booking-groups/GRP-1/cancellations': json(409, { code: 'TICKET_NOT_CANCELLABLE', correlationId: 'c-9', failingTicketCodes: ['TK-2'] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=cancel' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await user.type(screen.getByLabelText('Ticket codes'), 'TK-1 TK-2');
    await user.type(screen.getByLabelText('Reason (required)'), 'x');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Cancel selected tickets' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel tickets' }));
    expect(await screen.findByText(/Blocked tickets: TK-2/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Cancellation result')).not.toBeInTheDocument();
  });

  it('shows a price increase first and repeats the change with confirmIncrease and a new key', async () => {
    let call = 0;
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'GET /api/v1/workshop-tickets/TK-1/change-options': optionsOf('TK-1'),
      'POST /api/v1/workshop-tickets/TK-1/changes': () => (++call === 1
        ? json(200, { ticketCode: 'TK-1', applied: false, priceIncreaseAmount: '30000', confirmIncreaseRequired: true, balanceDue: '0' })
        : json(200, { ticketCode: 'TK-1', applied: true, priceIncreaseAmount: '30000', confirmIncreaseRequired: false, balanceDue: '30000' })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await addTicketToChange(user, 'TK-1');
    await user.type(screen.getByLabelText('Reason (required)'), 'Buyer asked at the counter');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Change tickets' }));
    expect(await screen.findByText('30.000 VND')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Buyer confirms the increase' }));
    expect(await screen.findByText('Change applied.')).toBeInTheDocument();
    const posts = calls.filter(item => item.path.endsWith('/changes'));
    expect(posts[0].body).toEqual({ type: 'ADVANCE', reason: 'Buyer asked at the counter', buyerVerificationReference: 'proof-ref-1', fromSessionReferences: [F1], targetSessionReferences: [S1] });
    expect(posts[1].body.confirmIncrease).toBe(true);
    expect(posts[1].headers['Idempotency-Key']).not.toBe(posts[0].headers['Idempotency-Key']);
  });

  it('reports a change queued for a Manager (202)', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'GET /api/v1/workshop-tickets/TK-1/change-options': optionsOf('TK-1'),
      'POST /api/v1/workshop-tickets/TK-1/changes': json(202, { ticketCode: 'TK-1', applied: false, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0', changeRequestReference: 'CR-5' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await addTicketToChange(user, 'TK-1');
    await user.type(screen.getByLabelText('Reason (required)'), 'late');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Change tickets' }));
    expect(await screen.findByText('CR-5')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'change request queue' })).toHaveAttribute('href', '/settlement/change-requests');
  });


  it('tells the buyer an expired voucher was not restored, only for the stored reason EXPIRED (T163)', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'POST /api/v1/workshop-booking-groups/GRP-1/cancellations': json(201, { groupCode: 'GRP-1', tickets: [
        { ticketCode: 'TK-1', benefitOutcome: 'VALID_CANCELLATION', pointsReturned: 0, voucherRestoration: { restored: false, reason: 'EXPIRED' } },
        { ticketCode: 'TK-2', benefitOutcome: 'VALID_CANCELLATION', pointsReturned: 0, voucherRestoration: { restored: false, reason: null } },
      ] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=cancel' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await user.type(screen.getByLabelText('Ticket codes'), 'TK-1, TK-2');
    await user.type(screen.getByLabelText('Reason (required)'), 'Customer cannot attend');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Cancel selected tickets' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel tickets' }));
    const result = await screen.findByLabelText('Cancellation result');
    // Only the ticket whose stored reason is EXPIRED carries the notice; ALREADY_RESTORED (reason null) shows nothing.
    expect(within(result).getAllByLabelText('Voucher not restored')).toHaveLength(1);
    expect(within(result).getByText(/voucher had expired, so it was not restored. Your refund is not affected/)).toBeInTheDocument();
  });

  it('changes several tickets of one group on the group route', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), ...verifyHandlers,
      'GET /api/v1/workshop-tickets/TK-1/change-options': optionsOf('TK-1'),
      'GET /api/v1/workshop-tickets/TK-2/change-options': optionsOf('TK-2'),
      'POST /api/v1/workshop-booking-groups/GRP-1/ticket-changes': json(200, { groupCode: 'GRP-1', applied: true, totalPriceIncrease: '0', confirmIncreaseRequired: false, tickets: [
        { ticketCode: 'TK-1', applied: true, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0' },
        { ticketCode: 'TK-2', applied: true, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0' },
      ] }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Booking group code'), 'GRP-1');
    await addTicketToChange(user, 'TK-1');
    await addTicketToChange(user, 'TK-2');
    await user.type(screen.getByLabelText('Reason (required)'), 'Whole family moves');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Change tickets' }));
    expect(await screen.findByText('Change applied.')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/ticket-changes')).body.changes).toHaveLength(2);
    expect(calls.find(call => call.path.endsWith('/ticket-changes')).body.changes[1]).toEqual({ ticketCode: 'TK-2', fromSessionReferences: [F1], targetSessionReferences: [S1] });
  });
});

describe('Customer refund-account page', () => {
  it('sends the account through the single-use link, keeps the token out of the page, and never shows the account again', async () => {
    const calls = mockFetch({ 'POST /api/v1/refund-account-links/tok123': new Response(null, { status: 204 }) });
    const user = userEvent.setup();
    const { container } = renderRoutes(settlementRoutes, { path: '/refund-account#tok123', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText('Bank'), 'VCB');
    await user.type(screen.getByLabelText('Account number'), '0123456789');
    await user.type(screen.getByLabelText('Account holder name'), 'NGUYEN VAN A');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Send account' }));
    expect(await screen.findByText(/Your bank account was received/)).toBeInTheDocument();
    expect(calls[0].body).toEqual({ bankCode: 'VCB', accountNumber: '0123456789', holderName: 'NGUYEN VAN A', accountDetailsConfirmed: true });
    expect(calls[0].headers.Authorization).toBeUndefined();
    expect(container.textContent).not.toContain('0123456789');
    expect(container.textContent).not.toContain('tok123');
  });

  it('says a used or expired link needs a new one (410) and an unknown link is invalid (404)', async () => {
    mockFetch({ 'POST /api/v1/refund-account-links/old': json(410, { code: 'REFUND_ACCOUNT_LINK_NOT_USABLE', correlationId: 'c' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/refund-account#old', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText('Bank'), 'VCB');
    await user.type(screen.getByLabelText('Account number'), '1');
    await user.type(screen.getByLabelText('Account holder name'), 'A');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Send account' }));
    expect(await screen.findByText(/already used, replaced or has expired/)).toBeInTheDocument();
  });

  it('refuses a link with no token', async () => {
    mockFetch({});
    renderRoutes(settlementRoutes, { path: '/refund-account', session: fakeSession('signed-out') });
    expect(await screen.findByText(/link is incomplete/)).toBeInTheDocument();
  });
});

describe('Customer Mland remedy page', () => {
  const OFFER = {
    remedyReference: 'RM-1', ticketCode: 'TK-1', groupCode: 'GRP-1', outcome: 'PENDING_ANSWER', answerDeadlineAt: '2026-11-20T00:00:00Z', selectedReplacementSessionReference: null,
    offeredSessions: [
      { sessionReference: S1, startAt: '2026-11-25T02:00:00Z', endAt: '2026-11-25T04:00:00Z', selectable: true },
      { sessionReference: '7c1f0c2e-0000-4000-8000-000000000002', startAt: '2026-11-21T02:00:00Z', endAt: null, selectable: false },
    ],
  };

  it('lets a Guest pick an offered session after buyer verification, without a bearer', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, OFFER), ...verifyHandlers,
      'POST /api/v1/workshop-tickets/TK-1/mland-remedy/answers': json(200, {}),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy', session: fakeSession('signed-out') });
    const radios = await screen.findAllByRole('radio');
    expect(radios[1]).toBeDisabled(); // not selectable
    const send = screen.getByRole('button', { name: 'Send my answer' });
    await user.click(radios[0]);
    expect(send).toBeDisabled(); // a Guest still needs the buyer proof
    await verifyBuyer(user);
    await user.click(send);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('Your new session is booked.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST' && call.path.endsWith('/answers'));
    expect(post.body).toEqual({ answer: 'ACCEPT_REPLACEMENT', replacementSessionReference: S1, buyerVerificationReference: 'proof-ref-1' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
    expect(calls.every(call => call.headers.Authorization === undefined)).toBe(true);
  });

  it('lets a signed-in Member choose a refund with the bearer and no code', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, OFFER),
      'POST /api/v1/workshop-tickets/TK-1/mland-remedy/answers': json(200, {}),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy' });
    await user.click(await screen.findByRole('radio', { name: /Refund this ticket/ }));
    await user.click(screen.getByRole('button', { name: 'Send my answer' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('Your refund request was recorded.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ answer: 'REFUND' });
    expect(post.headers.Authorization).toBe('Bearer test-token');
  });

  it('explains a replacement session that just filled up and keeps the choice open', async () => {
    mockFetch({
      'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, OFFER),
      'POST /api/v1/workshop-tickets/TK-1/mland-remedy/answers': json(409, { code: 'REPLACEMENT_SESSION_UNAVAILABLE', correlationId: 'c' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy' });
    await user.click((await screen.findAllByRole('radio'))[0]);
    await user.click(screen.getByRole('button', { name: 'Send my answer' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText(/no seat left/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send my answer' })).toBeInTheDocument();
  });

  it('shows an answered offer read-only', async () => {
    mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, { ...OFFER, outcome: 'MOVED', selectedReplacementSessionReference: S1 }) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy', session: fakeSession('signed-out') });
    expect(await screen.findByText(/moved to the replacement session/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Send my answer' })).not.toBeInTheDocument();
  });

  it('shows the expired-voucher notice on an answered refund, and nothing for ALREADY_RESTORED (T163)', async () => {
    mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, { ...OFFER, outcome: 'REFUND', voucherRestoration: { restored: false, reason: 'EXPIRED' } }) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy', session: fakeSession('signed-out') });
    expect(await screen.findByLabelText('Voucher not restored')).toHaveTextContent('Your refund is not affected');
  });

  it('shows no voucher notice for a restored or already restored voucher', async () => {
    mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, { ...OFFER, outcome: 'REFUND', voucherRestoration: { restored: false, reason: null } }) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/remedy', session: fakeSession('signed-out') });
    expect(await screen.findByText(/being refunded/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Voucher not restored')).not.toBeInTheDocument();
  });
});

describe('Customer ticket folio', () => {
  const FOLIO = { ticketCode: 'TK-1', status: 'OPEN', totalAmount: '450000', paidAmount: '450000', balanceDue: '0', collections: [{ collectionId: 'c', method: 'CASH', amount: '450000', status: 'CONFIRMED' }] };

  it('reads a Member folio with the bearer', async () => {
    const calls = mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, FOLIO) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio' });
    expect(await screen.findByLabelText('Ticket folio')).toHaveTextContent('450.000 VND');
    expect(calls[0].headers.Authorization).toBe('Bearer test-token');
  });

  it('tells the Member an expired voucher was not restored when the folio carries reason EXPIRED (T163)', async () => {
    mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, { ...FOLIO, status: 'CLOSED', voucherRestoration: { restored: false, reason: 'EXPIRED' } }) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio' });
    expect(await screen.findByLabelText('Voucher not restored')).toBeInTheDocument();
  });

  it('shows no voucher notice on a folio without an expired voucher', async () => {
    mockFetch({ 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, { ...FOLIO, voucherRestoration: { restored: false, reason: null } }) });
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio' });
    expect(await screen.findByLabelText('Ticket folio')).toBeInTheDocument();
    expect(screen.queryByLabelText('Voucher not restored')).not.toBeInTheDocument();
  });

  it('gives a Guest a lookup form that sends one contact in the body, never in the URL, and clears it', async () => {
    const calls = mockFetch({ 'POST /api/v1/workshop-tickets/TK-1/folio-lookups': json(200, FOLIO) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio', session: fakeSession('signed-out') });
    const show = await screen.findByRole('button', { name: 'Show my ticket' });
    expect(show).toBeDisabled();
    await user.type(screen.getByLabelText('Email'), 'buyer@example.com');
    await user.click(show);
    expect(await screen.findByLabelText('Ticket folio')).toBeInTheDocument();
    expect(calls[0].body).toEqual({ contactEmail: 'buyer@example.com' });
    expect(calls[0].path).toBe('/api/v1/workshop-tickets/TK-1/folio-lookups');
    expect(calls[0].query).toEqual({});
    expect(calls[0].headers.Authorization).toBeUndefined();
  });

  it('answers the same for a wrong ticket and a wrong contact (404) and explains the rate limit (429)', async () => {
    let status = 404;
    mockFetch({ 'POST /api/v1/workshop-tickets/TK-1/folio-lookups': () => json(status, { code: status === 404 ? 'RESOURCE_NOT_FOUND' : 'RATE_LIMITED', correlationId: 'c' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText('Email'), 'wrong@example.com');
    await user.click(screen.getByRole('button', { name: 'Show my ticket' }));
    expect(await screen.findByText('We could not find a ticket with this code and contact.')).toBeInTheDocument();
    status = 429;
    await user.type(screen.getByLabelText('Phone'), '0900000000');
    await user.click(screen.getByRole('button', { name: 'Show my ticket' }));
    expect(await screen.findByText(/Too many attempts/)).toBeInTheDocument();
  });
});
