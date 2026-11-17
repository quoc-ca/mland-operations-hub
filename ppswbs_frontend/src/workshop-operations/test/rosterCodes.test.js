import { describe, expect, it } from 'vitest';
import { rosterCodes } from '../pages/OperationsSessionPage';

describe('rosterCodes', () => {
  it('shows a group ticket as the customer knows it: GRP-…-L1-T1 and the group code', () => {
    expect(rosterCodes({ ticketCode: 'TK-D0198954', bookingReference: 'GRP-5E849940018F442D8C9C-L1', ticketSequence: 1 }))
      .toEqual({ ticket: 'GRP-5E849940018F442D8C9C-L1-T1', booking: 'GRP-5E849940018F442D8C9C', internal: 'TK-D0198954' });
  });

  it('keeps the stored codes of a booking without a line suffix, or without a person number', () => {
    expect(rosterCodes({ ticketCode: 'TK-1', bookingReference: 'BK-A', ticketSequence: 1 }))
      .toEqual({ ticket: 'TK-1', booking: 'BK-A', internal: null });
    expect(rosterCodes({ ticketCode: 'TK-2', bookingReference: 'GRP-X-L2' }))
      .toEqual({ ticket: 'TK-2', booking: 'GRP-X-L2', internal: null });
  });
});

import { paymentBadge } from '../pages/OperationsSessionPage';

describe('paymentBadge', () => {
  it('says paid once the folio is finalized, whatever the session state', () => {
    expect(paymentBadge('FINALIZED', 'IN_PROGRESS')).toEqual({ key: 'paid', label: 'Đã thanh toán' });
    expect(paymentBadge('CLOSED', 'ATTENDANCE_FINALIZED')).toEqual({ key: 'paid', label: 'Đã thanh toán' });
  });

  it('says unpaid only after the session attendance is finalized', () => {
    expect(paymentBadge('OPEN', 'ATTENDANCE_FINALIZED')).toEqual({ key: 'unpaid', label: 'Chưa thanh toán' });
    expect(paymentBadge(undefined, 'ATTENDANCE_FINALIZED')).toBeNull();
    expect(paymentBadge('OPEN', 'IN_PROGRESS')).toBeNull();
  });
});
