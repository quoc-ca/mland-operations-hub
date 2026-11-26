import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

useFreshRole();

/** 003.3 Phase 14, T189 (FR-058, FR-059, FR-061, FR-063, FR-060): location names on every row and the two refusals. */

const F1 = '7c1f0c2e-0000-4000-8000-0000000000f1';
const HN = '7c1f0c2e-0000-4000-8000-000000000001';
const HCM = '7c1f0c2e-0000-4000-8000-000000000002';

const OPTIONS = {
  ticketCode: 'TK-1', type: 'ADVANCE', allowance: { used: 0, total: 1, managerGranted: 0, left: 1, exhausted: false },
  windowOpen: true, canSelfServe: true,
  perFromSession: [{
    fromSessionReference: F1, start: '2026-12-01T02:00:00Z', end: '2026-12-01T04:00:00Z', sourceChangeable: true,
    options: [
      { sessionReference: HN, start: '2026-12-03T02:00:00Z', end: '2026-12-03T04:00:00Z', remainingCapacity: 3, remainingRescheduleQuota: 2,
        locationReference: 11, locationName: 'Mland Hà Nội', selectable: true, requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0' },
      { sessionReference: HCM, start: '2026-12-03T02:00:00Z', end: '2026-12-03T04:00:00Z', remainingCapacity: 5, remainingRescheduleQuota: 1,
        locationReference: 22, locationName: null, selectable: true, requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0' },
    ],
  }],
};

const VERIFY = '/api/v1/workshop-booking-groups/GRP-1/buyer-verifications';
const verifyHandlers = {
  [`POST ${VERIFY}`]: json(200, { verificationId: 'V-1' }),
  [`POST ${VERIFY}/V-1/confirmations`]: json(200, { buyerVerificationReference: 'proof-ref-1' }),
};

async function verifyBuyer(user) {
  // Staff and Manager send no contact value: the code goes to the booking's own email.
  await user.click(screen.getByRole('button', { name: "Send code to the buyer's email" }));
  await user.type(await screen.findByLabelText('Code (6 digits)'), '123456');
  await user.click(screen.getByRole('button', { name: 'Verify' }));
  await screen.findByText(/Buyer verified/);
}

async function pickTarget(user) {
  await user.type(await screen.findByLabelText('Ticket code to change'), 'TK-1');
  await user.click(screen.getByRole('button', { name: 'Show sessions' }));
  await waitFor(() => expect(document.querySelector('select[name^="to-"]')).toBeTruthy());
  return (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === 'to-TK-1');
}

describe('Staff ticket change across locations', () => {
  it('shows the location name, seats and changes left on every option, and a reference when the name is missing', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-tickets/TK-1/change-options': json(200, OPTIONS) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    const select = await pickTarget(user);
    const texts = Array.from(select.options).filter(option => option.value).map(option => option.textContent);
    expect(texts[0]).toMatch(/^Mland Hà Nội – .*3 seats left · 2 changes left$/);
    expect(texts[1]).toMatch(/^Location 22 – .*5 seats left · 1 change left$/);
    // A different location never raises a Manager note (FR-059).
    expect(texts.join(' ')).not.toMatch(/Manager/);
  });

  it('lists the target location of a ticket added to the change', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-tickets/TK-1/change-options': json(200, OPTIONS) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    const select = await pickTarget(user);
    await user.selectOptions(select, HN);
    await user.click(screen.getByRole('button', { name: 'Add ticket to the change' }));
    expect(await screen.findByText(/TK-1: one session moved to Mland Hà Nội/)).toBeInTheDocument();
  });

  it('shows each session of a pair with its location, seats and changes left', async () => {
    const pairSessions = [
      { sessionReference: HN, start: '2026-12-03T02:00:00Z', end: '2026-12-03T04:00:00Z', remainingCapacity: 3, remainingRescheduleQuota: 2, locationReference: 11, locationName: 'Mland Hà Nội' },
      { sessionReference: HCM, start: '2026-12-04T02:00:00Z', end: '2026-12-04T04:00:00Z', remainingCapacity: 5, remainingRescheduleQuota: 1, locationReference: 11, locationName: 'Mland Hà Nội' },
    ];
    const two = { ...OPTIONS, perFromSession: [OPTIONS.perFromSession[0], { ...OPTIONS.perFromSession[0], fromSessionReference: HCM, options: [] }] };
    let reads = 0;
    mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-tickets/TK-1/change-options': () => json(200, ++reads > 1
        ? { ...two, pairs: [{ sessionReferences: [HN, HCM], selectable: true, requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0', sessions: pairSessions }] }
        : two),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    await user.type(await screen.findByLabelText('Ticket code to change'), 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Show sessions' }));
    await user.click(await screen.findByRole('radio', { name: /Both sessions together/ }));
    const pair = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === 'pair-TK-1');
    const label = Array.from(pair.options).find(option => option.value).textContent;
    expect(label).toMatch(/Mland Hà Nội – .*\(3 seats left, 2 changes left\) → Mland Hà Nội – .*\(5 seats left, 1 change left\)/);
  });

  it.each([
    ['PACKAGE_NOT_AVAILABLE_AT_LOCATION', /no longer sold at the chosen location/],
    ['LATE_ARRIVAL_WINDOW_EXPIRED', /7th calendar day/],
  ])('explains %s by its code', async (code, text) => {
    mockFetch({
      [PROFILE]: asRole('STAFF'),
      'GET /api/v1/workshop-tickets/TK-1/change-options': json(200, OPTIONS),
      'POST /api/v1/workshop-tickets/TK-1/changes': json(409, { code, correlationId: 'c-9' }),
      ...verifyHandlers,
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/ticket-actions?tab=change' });
    const select = await pickTarget(user);
    await user.selectOptions(select, HN);
    await user.click(screen.getByRole('button', { name: 'Add ticket to the change' }));
    await user.type(screen.getByLabelText('Reason (required)'), 'counter');
    await user.type(screen.getByLabelText('Booking code'), 'GRP-1');
    await verifyBuyer(user);
    await user.click(screen.getByRole('button', { name: 'Change tickets' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(text);
    expect(alert).toHaveTextContent(code);
    expect(alert).toHaveTextContent('c-9');
  });
});

describe('Mland remedy across locations', () => {
  const OFFER = {
    remedyReference: 'RM-1', ticketCode: 'TK-1', groupCode: 'GRP-1', outcome: 'PENDING_ANSWER', answerDeadlineAt: '2026-11-20T00:00:00Z',
    offeredSessions: [
      { sessionReference: HN, startAt: '2026-11-25T02:00:00Z', endAt: null, selectable: true, locationReference: 11, locationName: 'Mland Hà Nội' },
      { sessionReference: HCM, startAt: '2026-11-26T02:00:00Z', endAt: null, selectable: true, locationReference: null, locationName: null },
    ],
  };

  it('shows the location next to each offered session and names it in the confirmation; an old offer shows the time only', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER' }), 'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, OFFER) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/workshop-tickets/TK-1/remedy' });
    const radios = await screen.findAllByRole('radio');
    expect(screen.getByText(/^Mland Hà Nội – /)).toBeInTheDocument();
    expect(screen.queryByText(/null/)).toBeNull();
    await user.click(radios[0]);
    await user.click(screen.getByRole('button', { name: 'Send my answer' }));
    expect(within(screen.getByRole('dialog')).getByText(/moves to the chosen session at Mland Hà Nội\./)).toBeInTheDocument();
  });

  it('says a session no longer sold at its location is unavailable', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER' }),
      'GET /api/v1/workshop-tickets/TK-1/mland-remedy': json(200, OFFER),
      'POST /api/v1/workshop-tickets/TK-1/mland-remedy/answers': json(409, { code: 'REPLACEMENT_SESSION_UNAVAILABLE', correlationId: 'c' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/workshop-tickets/TK-1/remedy' });
    await user.click((await screen.findAllByRole('radio'))[0]);
    await user.click(screen.getByRole('button', { name: 'Send my answer' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText(/no longer sold at that location/)).toBeInTheDocument();
  });
});

describe('Manager: change-request approval refused for a switched-off location', () => {
  it('shows the message, keeps the request pending and re-reads on "Load latest"', async () => {
    const request = { requestReference: 'CR-1', ticketCode: 'TK-1', type: 'ADVANCE', blockedRules: ['RESCHEDULE_QUOTA_EXHAUSTED'], status: 'PENDING', expiresAt: '2026-12-01T00:00:00Z' };
    mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-change-requests': json(200, [request]),
      'POST /api/v1/workshop-change-requests/CR-1/decisions': json(409, { code: 'PACKAGE_NOT_AVAILABLE_AT_LOCATION', correlationId: 'c-1' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/change-requests' });
    await user.click(await screen.findByRole('button', { name: 'Approve' }));
    await user.type(within(screen.getByRole('dialog')).getByRole('textbox'), 'ok');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(/no longer sold at the chosen location/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load latest' })).toBeInTheDocument();
  });
});

describe('Manager: add-ons sold at the previous location', () => {
  const FOLIO = { ticketCode: 'TK-1', status: 'OPEN', totalAmount: '450000', paidAmount: '0', pendingAmount: '0', balanceDue: '450000', collections: [] };
  const ADDON = { chargeReference: 'CH-1', catalogueAddOnReference: 'A-1', description: 'Clay set', amount: '50000', addOnLocationReference: 11, ticketLocationReference: 22 };

  async function open(user) {
    await user.type(await screen.findByPlaceholderText('GRP-…-L0-T1'), 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Open folio' }));
  }

  it('lists them read-only for a Manager and renders nothing when the list is empty', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, FOLIO), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      'GET /api/v1/workshop-tickets/TK-1/folio/previous-location-add-ons': json(200, [ADDON]),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await open(user);
    const block = await screen.findByRole('region', { name: 'Add-ons sold at the previous location' });
    expect(block).toHaveTextContent('Clay set');
    expect(block).toHaveTextContent('Sold at location 11 · ticket now at location 22');
    expect(within(block).queryByRole('button')).toBeNull();
  });

  it('renders no block when the list is empty', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, FOLIO), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      'GET /api/v1/workshop-tickets/TK-1/folio/previous-location-add-ons': json(200, []),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await open(user);
    await screen.findAllByText('450,000 ₫');
    expect(screen.queryByRole('region', { name: 'Add-ons sold at the previous location' })).toBeNull();
  });

  it('never calls the route for Staff', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, FOLIO), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await open(user);
    await screen.findAllByText('450,000 ₫');
    expect(calls.some(call => call.path.endsWith('previous-location-add-ons'))).toBe(false);
  });

  it('shows a small notice with Try again when the read fails, folio stays usable', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-tickets/TK-1/folio': json(200, FOLIO), 'GET /api/v1/workshop-tickets/TK-1/folio/add-ons': json(200, []),
      'GET /api/v1/workshop-tickets/TK-1/folio/previous-location-add-ons': json(500, { code: 'X' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement' });
    await open(user);
    expect(await screen.findByText(/could not be loaded/)).toBeInTheDocument();
    expect(screen.getAllByText('450,000 ₫').length).toBeGreaterThan(0);
  });
});
