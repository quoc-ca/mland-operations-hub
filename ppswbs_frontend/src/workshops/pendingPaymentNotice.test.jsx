import React from 'react';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor, act } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { PendingPaymentNotice } from './PendingPaymentNotice';
import { invalidatePendingPayments } from './pendingPayments';

/** 003.1 FR-058 (T165, T166): the header notice for a Member's groups that wait for payment. */

const LIST = 'GET /api/v1/members/me/workshop-booking-groups';
const inSeconds = seconds => new Date(Date.now() + seconds * 1000).toISOString();
const group = (code, status, deadlineSeconds) => ({ groupCode: code, groupStatus: status, paymentDeadlineAt: inSeconds(deadlineSeconds), holdExpiresAt: inSeconds(deadlineSeconds + 120), tickets: [] });
let counter = 0;

function open({ enabled = true, path = '/', groups }) {
  counter += 1; // a different account per test: the 60-second read cache is per account
  const calls = mockFetch({ [LIST]: json(200, groups) });
  const routes = [{ path: '*', element: <PendingPaymentNotice enabled={enabled} userKey={`uid-${counter}`} getIdToken={async () => 'token'} /> }];
  renderRoutes(routes, { path, session: fakeSession('signed-in') });
  return calls;
}

describe('header notice of groups waiting for payment', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('shows how many wait, the countdown to the nearest deadline and a link to that group', async () => {
    open({ groups: [group('GRP-LATER', 'PENDING_PAYMENT', 600), group('GRP-NEAR', 'PENDING_PAYMENT', 300), group('GRP-PAID', 'CONFIRMED', 100)] });
    const link = await screen.findByRole('link', { name: /2 giao dịch đang chờ thanh toán/ });
    expect(link).toHaveAttribute('href', '/workshop-booking/GRP-NEAR/payment-return?resume=1');
    expect(link.textContent).toMatch(/4:5\d|5:00/);
  });

  it('shows nothing for expired, cancelled or confirmed groups and for a deadline that has passed', async () => {
    const calls = open({ groups: [group('A', 'EXPIRED', -10), group('B', 'CANCELLED', 100), group('C', 'CONFIRMED', 100), group('D', 'PENDING_PAYMENT', -5)] });
    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/members/me/workshop-booking-groups')).toBe(true));
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('does not read anything for a Guest or a non-Member', async () => {
    const calls = open({ enabled: false, groups: [group('A', 'PENDING_PAYMENT', 300)] });
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(calls.filter(call => call.path === '/api/v1/members/me/workshop-booking-groups')).toHaveLength(0);
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('is hidden on the payment screen of the same group, where the countdown already is', async () => {
    const calls = open({ path: '/workshop-booking/GRP-NEAR/payment-return', groups: [group('GRP-NEAR', 'PENDING_PAYMENT', 300)] });
    await waitFor(() => expect(calls.length).toBeGreaterThan(0));
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('reads the list again at once when a screen says the groups changed', async () => {
    const calls = open({ groups: [group('GRP-1', 'PENDING_PAYMENT', 300)] });
    await screen.findByRole('link', { name: /1 giao dịch đang chờ thanh toán/ });
    const before = calls.filter(call => call.path === '/api/v1/members/me/workshop-booking-groups').length;
    act(() => { invalidatePendingPayments(); });
    await waitFor(() => expect(calls.filter(call => call.path === '/api/v1/members/me/workshop-booking-groups').length).toBeGreaterThan(before));
  });
});
