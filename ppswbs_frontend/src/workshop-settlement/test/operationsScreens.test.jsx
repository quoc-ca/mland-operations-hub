import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';
import { SessionNavigationBoundary } from '../../shared/auth/SessionNavigationBoundary';

useFreshRole();

describe('Cash close', () => {
  const CLOSE = { closeReference: 'CLOSE-1', locationId: 3, workingDate: '2026-11-14', expectedCashVnd: 1000000, countedCashVnd: 990000, differenceVnd: -10000, differenceReason: 'miscount', status: 'PENDING_MANAGER_CONFIRMATION' };
  const handlers = {
    [PROFILE]: asRole('MANAGER'),
    'GET /api/v1/workshop-cash-closes/expected-cash': json(200, { locationId: 3, workingDate: '2026-11-14', expectedCashVnd: 1000000 }),
    'POST /api/v1/workshop-cash-closes': json(201, CLOSE),
    'POST /api/v1/workshop-cash-closes/CLOSE-1/difference-confirmations': json(200, { ...CLOSE, status: 'CLOSED' }),
  };

  it('closes the book with the counted cash as text, then a Manager confirms the difference with a reason and a key', async () => {
    const calls = mockFetch(handlers);
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close' });
    await user.type(await screen.findByLabelText('Branch ID'), '3');
    await user.click(screen.getByRole('button', { name: 'Show expected cash' }));
    expect(await screen.findByText('1,000,000 ₫')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Counted cash (VND)'), '990000');
    await user.type(screen.getByLabelText(/Difference reason/), 'miscount');
    await user.click(screen.getByRole('button', { name: 'Close cash book' }));
    expect(await screen.findByText('Awaiting Manager confirmation')).toBeInTheDocument();
    const close = calls.find(call => call.method === 'POST');
    expect(close.body).toEqual({ locationId: 3, workingDate: '2026-11-14', countedAmount: '990000', differenceReason: 'miscount' });
    expect(close.headers['Idempotency-Key']).toBeTruthy();
    expect(calls.find(call => call.path.endsWith('/expected-cash')).query).toEqual({ locationId: '3' });
    // T172: the cut-off note is shown while the difference waits, and after the Manager closes the day.
    expect(screen.getByRole('status', { name: 'Cash close cut-off' })).toHaveTextContent(/belongs to the next working day, even while this difference waits/);

    await user.click(await screen.findByRole('button', { name: 'Confirm difference' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm difference' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'recounted by two people');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm difference' }));
    expect(await screen.findByText('Difference confirmed; the day is closed.')).toBeInTheDocument();
    const confirm = calls.filter(call => call.method === 'POST')[1];
    expect(confirm.body).toEqual({ reason: 'recounted by two people' });
    expect(confirm.headers['Idempotency-Key']).toBeTruthy();
    const note = screen.getByRole('status', { name: 'Cash close cut-off' });
    expect(note).toHaveTextContent(/belongs to the next working day/);
    expect(note).not.toHaveTextContent(/even while/);
  });

  it('does not offer the confirmation to Staff', async () => {
    mockFetch({ ...handlers, [PROFILE]: asRole('STAFF') });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close' });
    await user.type(await screen.findByLabelText('Branch ID'), '3');
    await user.click(screen.getByRole('button', { name: 'Show expected cash' }));
    await user.type(await screen.findByLabelText('Counted cash (VND)'), '990000');
    await user.click(screen.getByRole('button', { name: 'Close cash book' }));
    expect(await screen.findByText(/A Manager must confirm/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm difference' })).not.toBeInTheDocument();
  });

  it('explains a changed reuse of the Idempotency-Key (409)', async () => {
    mockFetch({ ...handlers, 'POST /api/v1/workshop-cash-closes': json(409, { code: 'IDEMPOTENCY_CONFLICT', correlationId: 'c-1' }) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close' });
    await user.type(await screen.findByLabelText('Branch ID'), '3');
    await user.click(screen.getByRole('button', { name: 'Show expected cash' }));
    await user.type(await screen.findByLabelText('Counted cash (VND)'), '1000000');
    await user.click(screen.getByRole('button', { name: 'Close cash book' }));
    expect(await screen.findByText(/Reload the latest data/)).toBeInTheDocument();
    expect(screen.getByText(/IDEMPOTENCY_CONFLICT/)).toBeInTheDocument();
  });
});

describe('Refund queue', () => {
  const refund = (over = {}) => ({ refundId: 'R-1', ticketCode: 'TK-1', groupCode: null, source: 'MLAND_CANCELLATION', amount: '200000', status: 'PENDING_APPROVAL', accountNumberMasked: '****1234', holderName: 'NGUYEN VAN A', ...over });

  it('lists refunds with masked accounts and approves one with a key', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-refund-requests': json(200, [refund()]),
      'POST /api/v1/workshop-refund-requests/R-1/transitions': json(200, refund({ status: 'PENDING_TRANSFER' })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    expect(screen.getByText(/\*\*\*\*1234/)).toBeInTheDocument();
    expect(screen.getAllByText('200,000 ₫').length).toBeGreaterThan(0);
    await user.click(await screen.findByRole('button', { name: 'Approve' }));
    expect(await screen.findByText('Refund approved.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ action: 'APPROVE' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('shows the full account number only on request and hides it again', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-refund-requests': json(200, [refund({ status: 'PENDING_TRANSFER' })]),
      'GET /api/v1/workshop-refund-requests/R-1/account-number': json(200, { refundId: 'R-1', bankCode: 'VCB', accountNumber: '0123456789', holderName: 'NGUYEN VAN A' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    expect(screen.queryByText('0123456789')).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Show full account number' }));
    expect(await screen.findByText('0123456789')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Hide' }));
    expect(screen.queryByText('0123456789')).not.toBeInTheDocument();
  });

  it('records the transfer with the bank reference alone; the proof note and the receipt image are optional', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-refund-requests': json(200, [refund({ status: 'PENDING_TRANSFER' })]),
      'POST /api/v1/workshop-refund-requests/R-1/transitions': json(200, refund({ status: 'PENDING_RECONCILIATION' })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    const submit = await screen.findByRole('button', { name: 'Record transfer' });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('Bank transaction reference'), 'FT123');
    expect(submit).toBeEnabled();
    await user.click(submit);
    expect(await screen.findByText('Transfer recorded.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ action: 'RECORD_TRANSFER', bankTransactionReference: 'FT123' });
  });

  it('closing as unreachable needs a confirmation with a reason, and a 409 is explained', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-refund-requests': json(200, [refund({ status: 'PENDING_ACCOUNT', accountNumberMasked: null, holderName: null })]),
      'POST /api/v1/workshop-refund-requests/R-1/transitions': json(409, { code: 'REFUND_NOT_YET_CLOSABLE', correlationId: 'c-2' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.click(await screen.findByRole('button', { name: 'Close as unreachable' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Close refund' }));
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'no answer after reminders');
    await user.click(within(dialog).getByRole('button', { name: 'Close refund' }));
    expect(await screen.findByText(/Reload the latest data/)).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ action: 'CLOSE_UNREACHABLE', reason: 'no answer after reminders' });
  });

  it('lets Staff enter the counter account, but not close or approve', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-refund-requests': json(200, [refund({ status: 'PENDING_ACCOUNT', accountNumberMasked: null, holderName: null })]),
      'PUT /api/v1/workshop-refund-requests/R-1/account': json(200, refund({ status: 'PENDING_APPROVAL' })),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/refunds' });
    await user.click(await screen.findByText('R-1'));
    await user.type(await screen.findByLabelText('Bank code'), 'VCB');
    await user.type(screen.getByLabelText('Account number'), '0123456789');
    await user.type(screen.getByLabelText(/Holder name/), 'NGUYEN VAN A');
    await user.click(screen.getByRole('button', { name: 'Review details' }));
    expect(screen.getByText('0123456789')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save account' })).toBeDisabled();
    await user.click(screen.getByRole('checkbox', { name: 'These details are correct' }));
    await user.click(screen.getByRole('button', { name: 'Save account' }));
    expect(await screen.findByText('Account recorded.')).toBeInTheDocument();
    const put = calls.find(call => call.method === 'PUT');
    expect(put.headers['Idempotency-Key']).toBeTruthy();
    expect(put.body).toEqual({ bankCode: 'VCB', accountNumber: '0123456789', holderName: 'NGUYEN VAN A', accountDetailsConfirmed: true });
    expect(screen.queryByRole('button', { name: 'Close as unreachable' })).not.toBeInTheDocument();
  });
});

describe('Change request queue', () => {
  const request = { requestReference: 'CR-1', ticketCode: 'TK-1', type: 'ADVANCE', targetSessionReferences: ['s1'], blockedRules: ['RESCHEDULE_QUOTA_EXHAUSTED'], status: 'PENDING', priceIncreaseAmount: '30000', buyerConfirmedIncrease: true, expiresAt: '2026-11-20T00:00:00Z' };

  it('lists pending requests and approves one with a reason and an Idempotency-Key', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-change-requests': json(200, [request]),
      'POST /api/v1/workshop-change-requests/CR-1/decisions': json(200, { ...request, status: 'APPROVED' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    expect(await screen.findByText('RESCHEDULE_QUOTA_EXHAUSTED')).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Approve' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Quota raised in 003.2');
    await user.click(within(dialog).getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText('Change approved and applied.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ decision: 'APPROVE', reason: 'Quota raised in 003.2' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
  });

  it('shows the server answer when the quota is still full (409)', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-change-requests': json(200, [request]),
      'POST /api/v1/workshop-change-requests/CR-1/decisions': json(409, { code: 'RESCHEDULE_QUOTA_EXHAUSTED', correlationId: 'c-3' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    await user.click(await screen.findByRole('button', { name: 'Reject' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'No seats');
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await screen.findByText(/Reload the latest data/)).toBeInTheDocument();
  });

  it('gives Staff the queue with the decision buttons (owner decision 2026-11-17)', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-change-requests': json(200, [request]) });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    expect(await screen.findByText('CR-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
  });

  it('opens a detail row with both sessions, the reason and the dates', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-change-requests': json(200, [{ ...request, groupCode: 'GRP-1', reason: 'Customer is sick', requesterRole: 'MEMBER', channel: 'WEB', fromSessionReferences: ['from-1'], targetSessionReferences: ['to-1'] }]),
      'GET /api/v1/workshop-sessions/from-1': json(200, { id: 'from-1', workshopDate: '2026-12-01', scheduledStartAt: '2026-12-01T02:00:00Z', scheduledEndAt: '2026-12-01T04:00:00Z', locationName: 'Mland Hà Nội' }),
      'GET /api/v1/workshop-sessions/to-1': json(200, { id: 'to-1', workshopDate: '2026-12-08', scheduledStartAt: '2026-12-08T07:00:00Z', scheduledEndAt: '2026-12-08T09:00:00Z', locationName: 'Mland TP.HCM' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    await user.click(await screen.findByRole('button', { name: 'Details' }));
    expect(await screen.findByText('Customer is sick')).toBeInTheDocument();
    expect(screen.getByText('Member · WEB')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Hide details' }));
    expect(screen.queryByText('Customer is sick')).not.toBeInTheDocument();
  });

  it('shows the date, time and branch of the current and the requested session', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-change-requests': json(200, [{ ...request, fromSessionReferences: ['from-1'], targetSessionReferences: ['to-1'] }]),
      'GET /api/v1/workshop-sessions/from-1': json(200, { id: 'from-1', workshopDate: '2026-12-01', scheduledStartAt: '2026-12-01T02:00:00Z', scheduledEndAt: '2026-12-01T04:00:00Z', locationName: 'Mland Hà Nội' }),
      'GET /api/v1/workshop-sessions/to-1': json(200, { id: 'to-1', workshopDate: '2026-12-08', scheduledStartAt: '2026-12-08T07:00:00Z', scheduledEndAt: '2026-12-08T09:00:00Z', locationName: 'Mland TP.HCM' }),
    });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    expect(await screen.findByText('2026-12-01')).toBeInTheDocument();
    expect(await screen.findByText('2026-12-08')).toBeInTheDocument();
    expect(screen.getByText('Mland Hà Nội')).toBeInTheDocument();
    expect(screen.getByText('Mland TP.HCM')).toBeInTheDocument();
  });

  // BUG-E2E-0024: a decided request may target a session at another branch; Staff then gets 403 on that session read.
  it('keeps Staff on the queue when a session of another branch answers 403', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-change-requests': json(200, [{ ...request, status: 'REJECTED', fromSessionReferences: ['from-1'], targetSessionReferences: ['other-branch'] }]),
      'GET /api/v1/workshop-sessions/from-1': json(200, { id: 'from-1', workshopDate: '2026-12-01', scheduledStartAt: '2026-12-01T02:00:00Z', scheduledEndAt: '2026-12-01T04:00:00Z', locationName: 'Mland Hà Nội' }),
      'GET /api/v1/workshop-sessions/other-branch': json(403, { code: 'LOCATION_SCOPE_DENIED' }),
    });
    const routes = [{ element: <SessionNavigationBoundary />, children: [...settlementRoutes, { path: '/', element: <p>HOME</p> }] }];
    const { router } = renderRoutes(routes, { language: 'en', path: '/settlement/change-requests?status=REJECTED' });
    expect(await screen.findByText('2026-12-01')).toBeInTheDocument();
    expect(await screen.findByText(/other-br/)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/settlement/change-requests');
    expect(screen.queryByText('HOME')).not.toBeInTheDocument();
  });

  // BUG-E2E-0026: the reject dialog must not describe what approving does.
  it('describes rejecting, not approving, in the reject dialog', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-change-requests': json(200, [request]) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    await user.click(await screen.findByRole('button', { name: 'Reject' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('Ticket TK-1. Rejecting keeps the ticket on its current schedule.')).toBeInTheDocument();
    expect(within(dialog).queryByText(/Approving applies/)).not.toBeInTheDocument();
  });

  it('gives an Admin no queue and no decision buttons, and reads nothing', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('ADMIN'), 'GET /api/v1/workshop-change-requests': json(200, [request]) });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    expect(await screen.findByText(/Admin accounts have no part in ticket changes/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByText('CR-1')).not.toBeInTheDocument();
    expect(calls.filter(call => call.path === '/api/v1/workshop-change-requests')).toHaveLength(0);
  });
});
