import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { GroupDetailCard } from './GroupDetailCard';

/** The ticket card shows the folio's money position once it exists, not the booking-time snapshot. */

const baseTicket = {
  ticketCode: 'WT-1', lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax', ticketStatus: 'CONFIRMED',
  financialStatus: 'DEPOSIT_PAID', finalPriceVnd: 725000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT',
  remainingVnd: 525000, sessions: [],
};

const group = ticket => ({
  groupCode: 'GRP-CURRENT-1',
  groupStatus: 'CONFIRMED',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 200000,
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax', pricePerParticipantVnd: 725000, ticketCount: 1, sessionReferences: [] }],
  tickets: [ticket],
});

const rowValue = label => screen.getByText(label).nextSibling.textContent.replace(/\s/g, ' ');

describe('ticket card money position', () => {
  it('after an add-on and a later payment it shows the folio figures instead of the booking-time balance', () => {
    render(<GroupDetailCard group={group({
      ...baseTicket, currentTotalVnd: 825000, currentAddOnVnd: 100000, currentPaidVnd: 825000, currentBalanceDueVnd: 0,
    })} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    expect(rowValue('Còn phải trả')).toMatch(/^0\s?₫$/);
    expect(screen.getByText('Dịch vụ thêm đã xác nhận')).toBeTruthy();
    expect(rowValue('Đã thanh toán')).toContain('825.000');
    expect(screen.queryByText('Đã trả khi đặt')).toBeNull();
  });

  it('without a folio it keeps the booking-time figures', () => {
    render(<GroupDetailCard group={group(baseTicket)} authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    expect(rowValue('Còn phải trả')).toContain('525.000');
    expect(screen.getByText('Đã trả khi đặt')).toBeTruthy();
    expect(screen.queryByText('Dịch vụ thêm đã xác nhận')).toBeNull();
  });

  it.each([
    ['booking-time figures', {}],
    ['folio figures', { currentTotalVnd: 725000, currentAddOnVnd: 0, currentPaidVnd: 200000, currentBalanceDueVnd: 525000 }],
  ])('a cancelled ticket shows nothing to collect (%s)', (_, money) => {
    render(<GroupDetailCard group={group({ ...baseTicket, ...money, ticketStatus: 'CANCELLED' })}
      authorization="Bearer member-token" onTicketUpdated={() => {}} />);

    expect(rowValue('Còn phải trả')).toBe('Không phải thu');
  });
});
