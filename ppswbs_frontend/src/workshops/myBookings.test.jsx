import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { clearRoleCache } from '../shared/auth/useRole';
import { fakeSession, json, mockFetch } from '../test/render';
import { WorkshopLookupPage } from './WorkshopLookupPage';

const LIST = 'GET /api/v1/members/me/workshop-bookings';
const PROFILE = 'GET /api/v1/accounts/me/profile';

const confirmed = {
  bookingCode: 'BK-CONFIRMED-1', bookingStatus: 'CONFIRMED', paymentStatus: 'DEPOSIT_PAID', packageId: 145,
  packageName: 'Signature Workshop - Silver 925', participantCount: 2,
  sessions: [{ sessionReference: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', ordinal: 1, scheduledStart: '2026-12-01T13:00:00Z', scheduledEnd: '2026-12-01T16:00:00Z' }],
  tickets: [{ ticketCode: 'TK-AAA111', qrReference: 'QR-1', sessionReferences: [] }, { ticketCode: 'TK-BBB222', qrReference: 'QR-2', sessionReferences: [] }],
};
const pending = {
  bookingCode: 'BK-PENDING-2', bookingStatus: 'PENDING_PAYMENT', paymentStatus: 'UNPAID', packageId: 145,
  packageName: 'Ring Basic', participantCount: 1, holdExpiresAt: '2099-01-01T00:00:00Z', sessions: [], tickets: [],
};

function renderPage(session = fakeSession()) {
  return render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={session}>
        <MemoryRouter><WorkshopLookupPage /></MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
}

describe('a Member sees their own bookings and tickets on the lookup page', () => {
  beforeEach(() => clearRoleCache());

  it('lists the bookings with status and ticket codes, with no booking code typed', async () => {
    const calls = mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, [confirmed, pending]) });
    renderPage();

    const list = within(await screen.findByLabelText('Đặt chỗ của tôi'));
    expect(await list.findByText('BK-CONFIRMED-1')).toBeInTheDocument();
    expect(list.getByText('Đã xác nhận')).toBeInTheDocument();
    expect(list.getByText('TK-AAA111')).toBeInTheDocument();
    expect(list.getByText('TK-BBB222')).toBeInTheDocument();
    expect(list.getByText('BK-PENDING-2')).toBeInTheDocument();
    expect(list.getByText('Chờ thanh toán cọc')).toBeInTheDocument();
    expect(calls.find(call => call.path === '/api/v1/members/me/workshop-bookings').headers.Authorization).toBe('Bearer test-token');
  });

  it('opens the full detail of a booking from the list without another lookup', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, [confirmed]) });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Xem chi tiết BK-CONFIRMED-1' }));

    expect(await screen.findByText('Vé tham gia workshop đã được phát hành')).toBeInTheDocument();
    expect(screen.getByLabelText(/Mã đặt chỗ \(Booking Code\)/)).toHaveValue('BK-CONFIRMED-1');
    expect(calls.filter(call => call.path.startsWith('/api/v1/members/me/workshop-bookings/'))).toHaveLength(0);
  });

  it('says so when the Member has no booking yet', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, []) });
    renderPage();

    expect(await screen.findByText(/Bạn chưa có đặt chỗ nào/)).toBeInTheDocument();
  });

  it('offers a retry when the list cannot be loaded', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: () => (++attempt === 1 ? json(503, { code: 'X', message: 'down' }) : json(200, [confirmed])) });
    renderPage();

    expect(await screen.findByText(/Không tải được danh sách đặt chỗ/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('BK-CONFIRMED-1')).toBeInTheDocument();
  });

  it('shows no list to a guest', async () => {
    mockFetch({});
    renderPage(fakeSession('signed-out'));

    await waitFor(() => expect(screen.getByLabelText(/Mã đặt chỗ \(Booking Code\)/)).toBeInTheDocument());
    expect(screen.queryByLabelText('Đặt chỗ của tôi')).toBeNull();
  });
});

describe('a customer whose ticket was recorded as a no-show', () => {
  beforeEach(() => clearRoleCache());

  const noShowPending = { ...confirmed, bookingCode: 'BK-NOSHOW-3', attendanceStatus: 'NO_SHOW_PENDING' };
  const attended = { ...confirmed, bookingCode: 'BK-ATTENDED-4', attendanceStatus: 'ATTENDED' };

  it('is told what happened in the list, with no ticket codes to present', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, [noShowPending, attended]) });
    renderPage();

    const list = within(await screen.findByLabelText('Đặt chỗ của tôi'));
    expect(await list.findByText('BK-NOSHOW-3')).toBeInTheDocument();
    expect(list.getByText('Vắng mặt (chờ xác nhận)')).toBeInTheDocument();
    expect(list.getByText(/xác nhận chính thức sau 24 giờ/)).toBeInTheDocument();
    expect(list.getByText('Đã tham dự')).toBeInTheDocument();
    // Only the attended booking still shows its ticket codes.
    expect(list.getAllByText('TK-AAA111')).toHaveLength(1);
  });

  it('sees the same explanation on the detail view', async () => {
    const user = userEvent.setup();
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, [noShowPending]) });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Xem chi tiết BK-NOSHOW-3' }));

    expect(await screen.findByText('VẮNG MẶT (CHỜ XÁC NHẬN)')).toBeInTheDocument();
    expect(screen.getAllByText(/xác nhận chính thức sau 24 giờ/).length).toBeGreaterThan(0);
  });

  it('keeps the normal status before attendance is finalized', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: json(200, [confirmed]) });
    renderPage();

    const list = within(await screen.findByLabelText('Đặt chỗ của tôi'));
    expect(await list.findByText('Đã xác nhận')).toBeInTheDocument();
    expect(list.queryByText(/Vắng mặt/)).toBeNull();
  });
});

describe('the package reviews are offered once a session has been held', () => {
  beforeEach(() => clearRoleCache());

  it('links a booking with a past session to the reviews of its package, and not an upcoming one', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [
        { ...confirmed, bookingCode: 'BK-PAST', packageId: 145, sessions: [{ sessionReference: 'a', ordinal: 1, scheduledStart: '2020-01-01T13:00:00Z' }] },
        { ...confirmed, bookingCode: 'BK-FUTURE', packageId: 146, sessions: [{ sessionReference: 'b', ordinal: 1, scheduledStart: '2099-01-01T13:00:00Z' }] },
      ]),
    });
    renderPage();

    const links = await screen.findAllByRole('link', { name: 'Xem đánh giá của gói' });
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', '/reviews?packageId=145');
  });
});
