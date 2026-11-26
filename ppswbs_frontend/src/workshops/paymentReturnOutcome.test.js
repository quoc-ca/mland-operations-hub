import { describe, expect, it } from 'vitest';
import { classifyGroupPaymentOutcome } from './WorkshopPaymentReturnPage';

describe('payment return lookup errors', () => {
  it('shows the translated not-found wording instead of the server text (BUG-E2E-0029)', () => {
    const outcome = classifyGroupPaymentOutcome({ error: { status: 404, code: 'BOOKING_NOT_FOUND', message: 'Booking not found' } });
    expect(outcome.message).toEqual({ key: 'lookup.notFound' });
  });
});
