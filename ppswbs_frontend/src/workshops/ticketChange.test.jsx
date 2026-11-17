import React from 'react';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch } from '../test/render';
import { GroupDetailCard } from './GroupDetailCard';

/** 003.3 FR-044a: the customer picks the new session from the server's list, then changes the ticket (T179). */

const FROM = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const TO = '7c1f0c2e-0000-4000-8000-000000000002';
const FULL = '7c1f0c2e-0000-4000-8000-000000000003';
const GROUP = 'WG-20261113-ABCD';
const TICKET = 'WT-1';
const day = hours => new Date(Date.now() + hours * 3600 * 1000).toISOString();

const confirmedGroup = () => ({
  groupCode: GROUP,
  groupStatus: 'CONFIRMED',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 500000,
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax', pricePerParticipantVnd: 500000, ticketCount: 1, sessionReferences: [FROM] }],
  tickets: [{
    ticketCode: TICKET, lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax', ticketStatus: 'CONFIRMED',
    financialStatus: 'PAID', finalPriceVnd: 500000, collectedAtBookingVnd: 500000, collectionKind: 'FULL', remainingVnd: 0,
    sessions: [{ sessionReference: FROM, ordinal: 1, scheduledStart: day(72), scheduledEnd: day(74) }],
  }],
});

const options = (optionOverrides = {}, top = {}) => json(200, {
  ticketCode: TICKET, type: 'ADVANCE', allowance: { used: 0, total: 1, managerGranted: 0, left: 1, exhausted: false },
  windowOpen: true, canSelfServe: true,
  perFromSession: [{
    fromSessionReference: FROM, start: day(72), end: day(74), sourceChangeable: true,
    options: [
      { sessionReference: TO, start: day(120), end: day(122), remainingCapacity: 4, selectable: true, requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0', ...optionOverrides },
      { sessionReference: FULL, start: day(144), end: day(146), remainingCapacity: 0, selectable: false, reasonCode: 'INSUFFICIENT_CAPACITY', requiresManager: false, managerRules: [], priceIncreaseAmountVnd: '0' },
    ],
  }],
  ...top,
});

const VERIFY = `/api/v1/workshop-booking-groups/${GROUP}/buyer-verifications`;
const guestHandlers = {
  [`POST ${VERIFY}`]: json(201, { verificationId: 'V-1' }),
  [`POST ${VERIFY}/V-1/confirmations`]: json(200, { buyerVerificationReference: 'BVR-1' }),
};

async function pickFirstOption(user) {
  await user.click(await screen.findByRole('button', { name: 'Đổi lịch' }));
  await user.click((await screen.findAllByRole('radio'))[0]);
  const select = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === `to-${TICKET}`);
  await user.selectOptions(select, Array.from(select.options).find(option => option.value && !option.disabled).value);
}

async function verifyGuest(user) {
  await user.click(screen.getByRole('button', { name: 'Gửi mã xác minh' }));
  await user.type(await screen.findByLabelText(/Mã xác minh/), '123456');
  await user.click(screen.getByRole('button', { name: 'Xác minh' }));
}

describe('003.3 FR-044a customer ticket change', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('a Guest sees the list with one contact and no OTP, disables a full session, and changes with the OTP', async () => {
    const calls = mockFetch({
      ...guestHandlers,
      [`POST /api/v1/workshop-tickets/${TICKET}/change-option-lookups`]: options(),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(200, { ticketCode: TICKET, applied: true, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0', countedChange: true }),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} proof={{ contactEmail: 'guest@mland.vn' }} onTicketUpdated={() => {}} />);

    await user.click(await screen.findByRole('button', { name: 'Đổi lịch' }));
    await user.click((await screen.findAllByRole('radio'))[0]);
    expect(await screen.findByText(/còn 4 chỗ/)).toBeInTheDocument();
    const select = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === `to-${TICKET}`);
    expect(Array.from(select.options).find(option => option.value && option.disabled)).toBeTruthy();
    expect(screen.getByText(/Hết chỗ/)).toBeInTheDocument();

    // The list was read with the contact only: no OTP and no verification call yet.
    const lookup = calls.find(call => call.path.endsWith('/change-option-lookups'));
    expect(lookup.body).toEqual({ type: 'ADVANCE', contactEmail: 'guest@mland.vn' });
    expect(calls.some(call => call.path.endsWith('/buyer-verifications'))).toBe(false);

    await user.selectOptions(select, Array.from(select.options).find(option => option.value && !option.disabled).value);
    expect(screen.getByRole('button', { name: 'Xác nhận đổi lịch' })).toBeDisabled(); // OTP first
    await verifyGuest(user);
    await user.click(await screen.findByRole('button', { name: 'Xác nhận đổi lịch' }));

    expect(await screen.findByText(/Đã đổi lịch cho vé WT-1/)).toBeInTheDocument();
    const change = calls.find(call => call.path.endsWith('/changes'));
    expect(change.body).toEqual({ type: 'ADVANCE', fromSessionReferences: [FROM], targetSessionReferences: [TO], buyerVerificationReference: 'BVR-1' });
    expect(change.headers['Idempotency-Key']).toBeTruthy();
  });

  it('a Member reads the list with the bearer and needs no OTP', async () => {
    const calls = mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options(),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(200, { ticketCode: TICKET, applied: true, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0', countedChange: true }),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    await pickFirstOption(user);
    expect(screen.queryByRole('button', { name: 'Gửi mã xác minh' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Xác nhận đổi lịch' }));

    expect(await screen.findByText(/Đã đổi lịch cho vé WT-1/)).toBeInTheDocument();
    const read = calls.find(call => call.method === 'GET' && call.path.endsWith('/change-options'));
    expect(read.headers.Authorization).toBe('Bearer member-token');
    expect(read.query).toEqual({ type: 'ADVANCE' });
    const change = calls.find(call => call.path.endsWith('/changes'));
    expect(change.headers.Authorization).toBe('Bearer member-token');
    expect(change.body.buyerVerificationReference).toBeUndefined();
  });

  it('asks the buyer to confirm a price increase, then repeats with confirmIncrease and a new key', async () => {
    let call = 0;
    const calls = mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options({ priceIncreaseAmountVnd: '50000' }),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: () => (++call === 1
        ? json(200, { ticketCode: TICKET, applied: false, priceIncreaseAmount: '50000', confirmIncreaseRequired: true, balanceDue: '0', countedChange: false })
        : json(200, { ticketCode: TICKET, applied: true, priceIncreaseAmount: '50000', confirmIncreaseRequired: false, balanceDue: '50000', countedChange: true })),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    await pickFirstOption(user);
    expect(screen.getByText(/tăng giá/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Xác nhận đổi lịch' }));
    await user.click(await screen.findByRole('button', { name: 'Đồng ý mức tăng giá' }));

    expect(await screen.findByText(/Đã đổi lịch cho vé WT-1/)).toBeInTheDocument();
    const posts = calls.filter(item => item.path.endsWith('/changes'));
    expect(posts).toHaveLength(2);
    expect(posts[0].body.confirmIncrease).toBeUndefined();
    expect(posts[1].body.confirmIncrease).toBe(true);
    expect(posts[1].headers['Idempotency-Key']).not.toBe(posts[0].headers['Idempotency-Key']);
  });

  it('a choice only a Manager may allow is sent as a request and reported as waiting (202)', async () => {
    mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options({ requiresManager: true, managerRules: ['RESCHEDULE_QUOTA_EXHAUSTED'] }),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(202, { ticketCode: TICKET, applied: false, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0', countedChange: false, changeRequestReference: 'CHG-REQ-9' }),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    await pickFirstOption(user);
    expect(screen.getByRole('option', { name: /Cần quản lý duyệt/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu đổi lịch' }));

    expect(await screen.findByText(/Đã gửi yêu cầu đổi lịch cho quản lý duyệt/)).toBeInTheDocument();
    expect(screen.getByText('CHG-REQ-9')).toBeInTheDocument();
  });

  const STATUS = `GET /api/v1/workshop-tickets/${TICKET}/change-request-status`;

  it('a Member who reloads still sees that a change request is waiting for the Manager', async () => {
    const calls = mockFetch({
      [STATUS]: json(200, { requestReference: 'CHG-REQ-9', status: 'PENDING', decisionReason: null, decidedAt: null }),
    });
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    expect(await screen.findByText(/Đã gửi yêu cầu đổi lịch cho quản lý duyệt/)).toBeInTheDocument();
    expect(screen.getByText('CHG-REQ-9')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/change-request-status')).headers.Authorization).toBe('Bearer member-token');
  });

  it('the "waiting" notice turns into the Manager decision and the ticket is read again', async () => {
    let decided = false;
    mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options({ requiresManager: true, managerRules: ['RESCHEDULE_QUOTA_EXHAUSTED'] }),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(202, { ticketCode: TICKET, applied: false, priceIncreaseAmount: '0', confirmIncreaseRequired: false, balanceDue: '0', countedChange: false, changeRequestReference: 'CHG-REQ-9' }),
      [STATUS]: () => (decided
        ? json(200, { requestReference: 'CHG-REQ-9', status: 'REJECTED', decisionReason: 'Hết chỗ', decidedAt: new Date().toISOString() })
        : new Response(null, { status: 204 })),
    });
    const onTicketUpdated = vi.fn();
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={onTicketUpdated} />);

    await pickFirstOption(user);
    decided = true;
    await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu đổi lịch' }));

    expect(await screen.findByText(/Quản lý đã từ chối yêu cầu đổi lịch/)).toBeInTheDocument();
    expect(screen.getByText(/Lý do: Hết chỗ/)).toBeInTheDocument();
    expect(screen.queryByText(/Đã gửi yêu cầu đổi lịch cho quản lý duyệt/)).toBeNull();
  });

  it('shows a clear message when the server refuses the change (409 INSUFFICIENT_CAPACITY) and keeps the choice', async () => {
    mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options(),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(409, { code: 'INSUFFICIENT_CAPACITY', detail: 'x' }),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    await pickFirstOption(user);
    await user.click(screen.getByRole('button', { name: 'Xác nhận đổi lịch' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('vừa hết chỗ');
    expect(screen.getByRole('button', { name: 'Xác nhận đổi lịch' })).toBeEnabled();
  });

  it('shows a retry when the list cannot be loaded', async () => {
    mockFetch({ [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: json(409, { code: 'TICKET_FOLIO_NOT_OPEN', detail: 'closed' }) });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    await user.click(await screen.findByRole('button', { name: 'Đổi lịch' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('closed');
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });
});

describe('003.3 Phase 14 (T189) customer change across locations', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('shows the location name, seats and changes left on each row', async () => {
    mockFetch({ [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options({ locationName: 'Mland Hà Nội', locationReference: 11, remainingRescheduleQuota: 2 }) });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);
    await user.click(await screen.findByRole('button', { name: 'Đổi lịch' }));
    await user.click((await screen.findAllByRole('radio'))[0]);
    const select = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === `to-${TICKET}`);
    const text = Array.from(select.options).find(option => option.value && !option.disabled).textContent;
    expect(text).toMatch(/^Mland Hà Nội – .* · còn 4 chỗ · còn 2 lượt đổi$/);
  });

  it.each([
    ['PACKAGE_NOT_AVAILABLE_AT_LOCATION', 'không còn bán tại chi nhánh bạn chọn'],
    ['LATE_ARRIVAL_WINDOW_EXPIRED', 'ngày thứ 7 sau ngày buổi học kết thúc'],
  ])('explains %s and keeps the ticket unchanged', async (code, text) => {
    const calls = mockFetch({
      [`GET /api/v1/workshop-tickets/${TICKET}/change-options`]: options({ locationName: 'Mland Hà Nội' }),
      [`POST /api/v1/workshop-tickets/${TICKET}/changes`]: json(409, { code }),
    });
    const user = userEvent.setup();
    render(<GroupDetailCard group={confirmedGroup()} authorization="Bearer member-token" onTicketUpdated={() => {}} />);
    await pickFirstOption(user);
    await user.click(screen.getByRole('button', { name: 'Xác nhận đổi lịch' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
    if (code === 'PACKAGE_NOT_AVAILABLE_AT_LOCATION') {
      // The list is re-read and the choice cleared.
      await screen.findAllByRole('radio');
      expect(calls.filter(call => call.method === 'GET' && call.path.endsWith('/change-options')).length).toBeGreaterThan(1);
    }
  });
});
