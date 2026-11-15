import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

useFreshRole();

const FOLIO = '/api/v1/workshop-tickets/TK-1/folio';
const folio = (balanceDue = '100000', status = 'OPEN') => json(200, {
  ticketCode: 'TK-1', status, totalAmount: '200000', paidAmount: '100000', pendingAmount: '0', balanceDue,
  collections: [], charges: [],
});
const CORRECTION = {
  correctionReference: 'CORR-1', ticketCode: 'TK-1', overchargeAmount: '50000',
  refundId: 'R-1', status: 'PENDING_PROVIDER', refundStatus: 'PENDING_ACCOUNT',
};

async function openFolio(user) {
  await user.type(await screen.findByPlaceholderText('TK-...'), 'TK-1');
  await user.click(screen.getByRole('button', { name: 'Open folio' }));
}

describe('T126: Manager handover exception on an unsettled ticket folio (FR-035)', () => {
  it('allows handover only after a confirmation with a reason, then shows the server balance', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []),
      [`POST ${FOLIO}/handover-exceptions`]: json(201, {}),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.click(await screen.findByRole('button', { name: 'Allow handover' }));
    const dialog = screen.getByRole('dialog');
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Regular customer, pays tomorrow');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByText('Handover allowed and audited. The balance below is still owed.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST');
    expect(post.path).toBe(`${FOLIO}/handover-exceptions`);
    expect(post.body).toEqual({ reason: 'Regular customer, pays tomorrow' });
    expect(post.headers['Idempotency-Key']).toBeTruthy();
    expect(calls.filter(call => call.method === 'GET' && call.path === FOLIO).length).toBeGreaterThan(1);
    expect(screen.getByText('Balance due').nextSibling).toHaveTextContent('100.000 VND');
  });

  it('explains a conflict and offers to load the latest folio', async () => {
    mockFetch({
      [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []),
      [`POST ${FOLIO}/handover-exceptions`]: json(409, { code: 'FOLIO_ALREADY_SETTLED', correlationId: 'c-h' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.click(await screen.findByRole('button', { name: 'Allow handover' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Pays later');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText(/The record changed or the action conflicts with its current state/)).toBeInTheDocument();
    expect(screen.getByText('Code: FOLIO_ALREADY_SETTLED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load latest' })).toBeInTheDocument();
  });

  it('is not offered to Staff or on a settled folio', async () => {
    mockFetch({ [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio(), [`GET ${FOLIO}/add-ons`]: json(200, []) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await screen.findByText('Balance due');
    expect(screen.queryByRole('button', { name: 'Allow handover' })).toBeNull();
  });

  it('is not offered when nothing is owed', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), [`GET ${FOLIO}`]: folio('0'), [`GET ${FOLIO}/add-ons`]: json(200, []) });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await screen.findByText('Balance due');
    expect(screen.queryByRole('button', { name: 'Allow handover' })).toBeNull();
  });
});

describe('T127: read-only view of a posted folio correction (FR-050, FR-048)', () => {
  it('lets an operator read a correction from the folio page with the bearer', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio('0', 'FINALIZED'),
      [`GET ${FOLIO}/corrections/CORR-1`]: json(200, CORRECTION),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.type(await screen.findByLabelText('Correction reference'), 'CORR-1');
    await user.click(screen.getByRole('button', { name: 'View correction' }));
    const details = await screen.findByLabelText('Folio correction');
    expect(details).toHaveTextContent('50.000 VND');
    expect(details).toHaveTextContent('PENDING_PROVIDER');
    expect(details).toHaveTextContent('R-1');
    const read = calls.find(call => call.path.endsWith('/corrections/CORR-1'));
    expect(read.method).toBe('GET');
    expect(read.headers.Authorization).toBe('Bearer test-token');
    expect(calls.some(call => call.method === 'POST')).toBe(false);
  });

  it('answers a hidden or unknown correction the same way', async () => {
    mockFetch({
      [PROFILE]: asRole('STAFF'), [`GET ${FOLIO}`]: folio('0', 'FINALIZED'),
      [`GET ${FOLIO}/corrections/CORR-9`]: json(404, { code: 'NOT_FOUND' }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement' });
    await openFolio(user);
    await user.type(await screen.findByLabelText('Correction reference'), 'CORR-9');
    await user.click(screen.getByRole('button', { name: 'View correction' }));
    expect(await screen.findByText('No correction with this reference is visible for this ticket.')).toBeInTheDocument();
  });

  it('lets the owning Member read a correction from the customer folio page', async () => {
    const calls = mockFetch({
      [`GET ${FOLIO}`]: folio('0', 'FINALIZED'),
      [`GET ${FOLIO}/corrections/CORR-1`]: json(200, CORRECTION),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio' });
    await user.type(await screen.findByLabelText('Correction reference'), 'CORR-1');
    await user.click(screen.getByRole('button', { name: 'View correction' }));
    expect(await screen.findByLabelText('Folio correction')).toHaveTextContent('CORR-1');
    expect(calls.find(call => call.path.endsWith('/corrections/CORR-1')).headers.Authorization).toBe('Bearer test-token');
  });

  it('never offers the correction view to a Guest', async () => {
    mockFetch({});
    renderRoutes(settlementRoutes, { path: '/workshop-tickets/TK-1/folio', session: fakeSession('signed-out') });
    await screen.findByRole('button', { name: 'Show my ticket' });
    expect(screen.queryByLabelText('Correction reference')).toBeNull();
  });
});
