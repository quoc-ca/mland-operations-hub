import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

/**
 * 003.1 FR-042/FR-048: the group card shown right after "giữ chỗ" must prove the buyer the way the server expects.
 * A signed-in Member sends the bearer and NO contact; a Guest sends exactly ONE contact (the e-mail when there is one).
 */
const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const GROUP_CODE = 'WG-20261113-ABCD';
const RELEASE_PATH = `/api/v1/workshop-booking-groups/${GROUP_CODE}/pending-hold-release`;
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();

const QUOTE = {
  quoteRef: 'quote-ref-1',
  expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
  grossVnd: 500000,
  voucherDiscountVnd: 0,
  pointsUsed: 0,
  pointDiscountVnd: 0,
  finalTotalVnd: 500000,
  groupCollectionVnd: 200000,
  remainingAtCheckoutVnd: 300000,
  paymentMode: 'VNPAY',
  lines: [{
    lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, participantCount: 1,
    sessions: [{ sessionReference: SESSION_ONE, locationReference: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    tickets: [{ participantOrdinal: 1, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 }],
  }],
};

const heldGroup = () => ({
  groupCode: GROUP_CODE,
  groupStatus: 'PENDING_PAYMENT',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 200000,
  holdExpiresAt: new Date(Date.now() + 12 * 60 * 1000).toISOString(),
  paymentDeadlineAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 1, sessionReferences: [SESSION_ONE] }],
  tickets: [{ ticketCode: 'WT-1', lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax Casting cơ bản', ticketStatus: 'PENDING_PAYMENT', financialStatus: 'UNPAID', finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 }],
});

const handlers = extra => ({
  'GET /api/v1/workshop-packages/5': json(200, { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 }),
  'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Quận 1' }]),
  'GET /api/v1/workshop-packages/5/sessions': json(200, [{ sessionReference: SESSION_ONE, scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 4 }]),
  'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
  'POST /api/v1/workshop-booking-groups': json(201, heldGroup()),
  [`POST ${RELEASE_PATH}`]: json(200, { ...heldGroup(), groupStatus: 'CANCELLED' }),
  [`GET /api/v1/workshop-booking-groups/${GROUP_CODE}`]: json(200, heldGroup()),
  ...extra,
});

async function releaseFromCard(user) {
  await user.click(await screen.findByRole('button', { name: 'Hủy giữ chỗ' }));
  await user.type(screen.getByLabelText(/Lý do hủy/i), 'Đổi kế hoạch');
  await user.click(screen.getByRole('button', { name: 'Đồng ý hủy' }));
  return waitFor(() => {
    const call = (globalThis.__calls || []).find(item => item.path === RELEASE_PATH);
    expect(call).toBeTruthy();
    return call;
  });
}

describe('003.1 group card identity on the booking page', () => {
  beforeEach(() => { clearCart(); });

  it('a signed-in Member releases the hold with the bearer and no contact', async () => {
    const user = userEvent.setup();
    globalThis.__calls = mockFetch(handlers({
      'GET /api/v1/accounts/me/profile': json(200, { accountId: 11, role: 'MEMBER', email: 'member@mland.vn', phone: '0901234567', fullName: 'Member' }),
    }));
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=1`, session: fakeSession('signed-in') });

    await screen.findByText('Báo giá đặt ngay');
    await user.click(await screen.findByRole('button', { name: 'Xác nhận và giữ chỗ' }));
    const call = await releaseFromCard(user);

    expect(call.headers.Authorization).toBe('Bearer test-token');
    expect(call.body).toEqual({ reason: 'Đổi kế hoạch' });
  });

  it('a Guest who typed both contacts sends exactly one (the e-mail)', async () => {
    const user = userEvent.setup();
    globalThis.__calls = mockFetch(handlers());
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=1`, session: fakeSession('signed-out') });

    await screen.findByText('Báo giá đặt ngay');
    await user.type(screen.getByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));
    const call = await releaseFromCard(user);

    expect(call.headers.Authorization).toBeUndefined();
    expect(call.body).toEqual({ reason: 'Đổi kế hoạch', contactEmail: 'guest@mland.vn' });
  });
});
