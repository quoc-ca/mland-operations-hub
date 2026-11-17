import React from 'react';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';

/** Each ticket's session line says where the session is (003.1 FR-057 on the lookup result), when Operations sent the name. */

const SESSION_A = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const SESSION_B = '11111111-2222-4333-8444-555555555555';
const start = new Date(Date.now() + 2 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 2 * 24 * 3600 * 1000 + 3 * 3600 * 1000).toISOString();
const ticket = (code, session) => ({
  ticketCode: code, lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax Casting cơ bản',
  ticketStatus: 'CONFIRMED', financialStatus: 'DEPOSIT_PAID', finalPriceVnd: 500000, collectedAtBookingVnd: 200000, remainingVnd: 300000,
  collectionKind: 'DEPOSIT', qrReference: `QR-${code}`, sessions: [session],
});
const group = {
  groupCode: 'WG-20261113-ABCD', groupStatus: 'CONFIRMED', paymentMode: 'VNPAY', groupCollectionVnd: 200000, holdExpiresAt: null, paymentDeadlineAt: null,
  createdAt: new Date().toISOString(),
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 2, sessionReferences: [SESSION_A] }],
  tickets: [
    ticket('WT-1', { sessionReference: SESSION_A, locationId: 2, locationName: 'MLAND Tây Hồ', ordinal: 1, scheduledStart: start, scheduledEnd: end }),
    ticket('WT-2', { sessionReference: SESSION_B, locationId: 1, ordinal: 1, scheduledStart: start, scheduledEnd: end }),
  ],
};

describe('location of each ticket session', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('shows the location name beside the session time, and nothing extra when the name is not known', async () => {
    const user = userEvent.setup();
    mockFetch({ 'POST /api/v1/workshop-booking-group-lookups': json(200, group) });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/lookup', session: fakeSession('signed-out') });
    await user.type(await screen.findByLabelText(/Mã đặt chỗ/i), 'WG-20261113-ABCD');
    await user.type(screen.getByLabelText(/Email khi đặt/i), 'guest@mland.vn');
    await user.click(screen.getByRole('button', { name: 'Tra cứu nhóm đặt chỗ' }));

    const withName = await screen.findByText(/Buổi 1: .*· MLAND Tây Hồ/);
    expect(withName.tagName).toBe('LI');
    const lines = screen.getAllByText(/^Buổi 1:/);
    expect(lines).toHaveLength(2);
    expect(lines.filter(item => item.textContent.includes('·'))).toHaveLength(1);
  });
});
