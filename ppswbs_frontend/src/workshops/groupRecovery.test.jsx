import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const start = new Date(Date.now() - 2 * 3600 * 1000).toISOString();
const end = new Date(Date.now() - 1 * 3600 * 1000).toISOString();

const confirmedGroup = (overrides = {}) => ({
  groupCode: 'WG-20261113-ABCD',
  groupStatus: 'CONFIRMED',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 400000,
  holdExpiresAt: null,
  paymentDeadlineAt: null,
  createdAt: new Date(Date.now() - 86400000).toISOString(),
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 2, sessionReferences: [SESSION_ONE] }],
  tickets: [
    {
      ticketCode: 'WT-1',
      lineIndex: 0,
      participantOrdinal: 1,
      packageId: 5,
      packageName: 'Wax Casting cơ bản',
      ticketStatus: 'CONFIRMED',
      financialStatus: 'PARTIALLY_PAID',
      finalPriceVnd: 500000,
      collectedAtBookingVnd: 200000,
      collectionKind: 'DEPOSIT',
      remainingVnd: 300000,
      recipientEmailMasked: null,
      onlineRecipientChangesRemaining: 3,
      attendanceStatus: null,
      qrReference: 'QR-OPAQUE-1',
      sessions: [{ sessionReference: SESSION_ONE, locationId: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    },
    {
      ticketCode: 'WT-2',
      lineIndex: 0,
      participantOrdinal: 2,
      packageId: 5,
      packageName: 'Wax Casting cơ bản',
      ticketStatus: 'CONFIRMED',
      financialStatus: 'PARTIALLY_PAID',
      finalPriceVnd: 500000,
      collectedAtBookingVnd: 200000,
      collectionKind: 'DEPOSIT',
      remainingVnd: 300000,
      recipientEmailMasked: 'f***@mland.vn',
      onlineRecipientChangesRemaining: 2,
      attendanceStatus: 'NO_SHOW',
      qrReference: null,
      sessions: [{ sessionReference: SESSION_ONE, locationId: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    },
  ],
  ...overrides,
});

const MEMBER_PROFILE = { accountId: 11, role: 'MEMBER', email: 'member@mland.vn', phone: '0901234567', fullName: 'Member MLAND' };

describe('003.1 FR-042/FR-049 Guest lookup of a booking group', () => {
  it('proves the group in the request body and shows each ticket with its own facts', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'POST /api/v1/workshop-booking-group-lookups': json(200, confirmedGroup()),
    });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });

    await user.type(await screen.findByLabelText(/Mã nhóm đặt chỗ/i), 'WG-20261113-ABCD');
    await user.type(screen.getByLabelText(/Số điện thoại khi đặt/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));

    const lookup = await waitFor(() => {
      const call = calls.find(item => item.path === '/api/v1/workshop-booking-group-lookups');
      expect(call).toBeTruthy();
      return call;
    });
    expect(lookup.body).toEqual({ groupCode: 'WG-20261113-ABCD', contactPhone: '0901234567' });
    expect(lookup.query).toEqual({});

    expect(await screen.findByText('WT-1')).toBeInTheDocument();
    expect(screen.getByText('QR-OPAQUE-1')).toBeInTheDocument();
    expect(screen.getAllByText('Cọc').length).toBe(2);
    // Per-ticket recipient and attendance (FR-042, FR-050).
    expect(screen.getByText(/Người nhận vé: f\*\*\*@mland\.vn/)).toBeInTheDocument();
    expect(screen.getByText('Vắng mặt')).toBeInTheDocument();
  });

  it('asks a Guest for exactly one contact proof', async () => {
    const user = userEvent.setup();
    mockFetch({});
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText(/Mã nhóm đặt chỗ/i), 'WG-1');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/đúng một trong hai/i);
  });

  it('releases the whole group hold with a reason, and reports a payment already in flight', async () => {
    const user = userEvent.setup();
    const pending = confirmedGroup({
      groupStatus: 'PENDING_PAYMENT',
      paymentDeadlineAt: new Date(Date.now() + 9 * 60 * 1000).toISOString(),
      holdExpiresAt: new Date(Date.now() + 11 * 60 * 1000).toISOString(),
      tickets: confirmedGroup().tickets.map(ticket => ({ ...ticket, ticketStatus: 'PENDING_PAYMENT', attendanceStatus: null, qrReference: null })),
    });
    const calls = mockFetch({
      'POST /api/v1/workshop-booking-group-lookups': json(200, pending),
      'POST /api/v1/workshop-booking-groups/WG-20261113-ABCD/pending-hold-release': json(409, {
        code: 'PAYMENT_ATTEMPT_EXISTS',
        detail: 'A payment attempt exists',
      }),
    });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText(/Mã nhóm đặt chỗ/i), 'WG-20261113-ABCD');
    await user.type(screen.getByLabelText(/Email khi đặt/i), 'guest@mland.vn');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));

    await user.click(await screen.findByRole('button', { name: 'Hủy giữ chỗ' }));
    await user.type(screen.getByLabelText(/Lý do hủy/i), 'Khách đổi lịch');
    await user.click(screen.getByRole('button', { name: 'Đồng ý hủy' }));

    const release = await waitFor(() => {
      const call = calls.find(item => item.path.endsWith('/pending-hold-release'));
      expect(call).toBeTruthy();
      return call;
    });
    expect(release.body).toEqual({ reason: 'Khách đổi lịch', contactEmail: 'guest@mland.vn' });
    expect(release.headers['Idempotency-Key']).toMatch(/.{16,128}/);
    expect(await screen.findByText(/Đã có thanh toán đang thực hiện/i)).toBeInTheDocument();
  });

  it('asks for a confirmation resend of every ticket to the stored contact', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'POST /api/v1/workshop-booking-group-lookups': json(200, confirmedGroup()),
      'POST /api/v1/workshop-booking-groups/WG-20261113-ABCD/confirmation-deliveries': json(202, {
        groupCode: 'WG-20261113-ABCD', action: 'RESEND_CONFIRMATION', outcome: 'QUEUED', correlationId: 'corr-1',
      }),
    });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText(/Mã nhóm đặt chỗ/i), 'WG-20261113-ABCD');
    await user.type(screen.getByLabelText(/Email khi đặt/i), 'guest@mland.vn');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));

    await user.click(await screen.findByRole('button', { name: 'Gửi lại email xác nhận' }));
    const resend = await waitFor(() => {
      const call = calls.find(item => item.path.endsWith('/confirmation-deliveries'));
      expect(call).toBeTruthy();
      return call;
    });
    expect(resend.body).toEqual({ contactEmail: 'guest@mland.vn' });
    expect(await screen.findByText(/Yêu cầu gửi lại email xác nhận đã được tiếp nhận/i)).toBeInTheDocument();
  });
});

describe('003.1 FR-041 sending one ticket to a recipient', () => {
  it('verifies a Guest buyer by OTP and then changes only that ticket', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'POST /api/v1/workshop-booking-group-lookups': json(200, confirmedGroup()),
      'POST /api/v1/workshop-booking-groups/WG-20261113-ABCD/buyer-verifications': json(202, {
        verificationId: '8f14e45f-ceea-467a-9c5e-1c7c4c0a1b22',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
      }),
      'POST /api/v1/workshop-booking-groups/WG-20261113-ABCD/buyer-verifications/8f14e45f-ceea-467a-9c5e-1c7c4c0a1b22/confirmations': json(200, {
        buyerVerificationReference: 'b2f1c0de-0000-4000-8000-000000000000',
        expiresAt: new Date(Date.now() + 900000).toISOString(),
      }),
      'PUT /api/v1/workshop-tickets/WT-1/recipient': json(200, {
        ticketCode: 'WT-1',
        status: 'CONFIRMED',
        collectionKind: 'DEPOSIT',
        recipientEmailMasked: 'f***@mland.vn',
        onlineRecipientChangesRemaining: 2,
      }),
    });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText(/Mã nhóm đặt chỗ/i), 'WG-20261113-ABCD');
    await user.type(screen.getByLabelText(/Email khi đặt/i), 'guest@mland.vn');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));

    // Only the confirmed ticket that has not been marked absent offers the form.
    const openButtons = await screen.findAllByRole('button', { name: 'Gửi vé cho người đi cùng' });
    expect(openButtons).toHaveLength(1);
    await user.click(openButtons[0]);

    await user.type(screen.getByLabelText(/Email người nhận vé/i), 'friend@mland.vn');
    await user.click(screen.getByRole('button', { name: 'Gửi mã xác minh' }));
    const challenge = await waitFor(() => {
      const call = calls.find(item => item.path.endsWith('/buyer-verifications'));
      expect(call).toBeTruthy();
      return call;
    });
    expect(challenge.body).toEqual({ contactEmail: 'guest@mland.vn' });

    await user.type(await screen.findByLabelText(/Mã xác minh/i), '123456');
    await user.click(screen.getByRole('button', { name: 'Xác minh' }));
    await waitFor(() => expect(calls.some(call => call.path.endsWith('/confirmations'))).toBe(true));

    await user.click(screen.getByRole('button', { name: 'Gửi vé' }));
    const change = await waitFor(() => {
      const call = calls.find(item => item.path === '/api/v1/workshop-tickets/WT-1/recipient');
      expect(call).toBeTruthy();
      return call;
    });
    expect(change.method).toBe('PUT');
    expect(change.body).toEqual({
      recipientEmail: 'friend@mland.vn',
      buyerVerificationReference: 'b2f1c0de-0000-4000-8000-000000000000',
    });
    expect(change.headers['Idempotency-Key']).toMatch(/.{16,128}/);
    expect(await screen.findByText(/Đã gửi vé tới email người nhận/i)).toBeInTheDocument();
  });
});

describe('003.1 FR-046/FR-048 the Member list and the group Magic Link', () => {
  it('lists the Member groups and opens one through the owned-group route', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, MEMBER_PROFILE),
      'GET /api/v1/members/me/workshop-booking-groups': json(200, [confirmedGroup()]),
      'GET /api/v1/workshop-booking-groups/WG-20261113-ABCD': json(200, confirmedGroup()),
    });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-in') });

    expect(await screen.findByText('WG-20261113-ABCD')).toBeInTheDocument();
    const listCall = calls.find(call => call.path === '/api/v1/members/me/workshop-booking-groups');
    expect(listCall.headers.Authorization).toBe('Bearer test-token');

    await user.click(await screen.findByRole('button', { name: /Xem chi tiết WG-20261113-ABCD/i }));
    expect(await screen.findByText('WT-1')).toBeInTheDocument();
  });

  it('exchanges the group Magic-Link token from the fragment and scrubs the address', async () => {
    const calls = mockFetch({
      'POST /api/v1/workshop-booking-group-magic-links/exchanges': json(200, confirmedGroup()),
    });
    window.history.replaceState(null, '', '/workshop-booking/access#opaque-token-1');
    renderRoutes(workshopRoutes, { path: '/workshop-booking/access', session: fakeSession('signed-out') });

    const exchange = await waitFor(() => {
      const call = calls.find(item => item.path === '/api/v1/workshop-booking-group-magic-links/exchanges');
      expect(call).toBeTruthy();
      return call;
    });
    expect(exchange.body).toEqual({ token: 'opaque-token-1' });
    expect(window.location.hash).toBe('');
    expect(await screen.findByText('WT-1')).toBeInTheDocument();
    // A read-only view offers no hold release.
    expect(screen.queryByRole('button', { name: 'Hủy giữ chỗ' })).toBeNull();
  });
});
