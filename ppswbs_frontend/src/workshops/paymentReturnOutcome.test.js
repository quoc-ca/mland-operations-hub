import { describe, expect, it } from 'vitest';
import { classifyGuestPaymentOutcome } from './WorkshopPaymentReturnPage';

describe('Guest payment return outcome classification', () => {
  it('keeps provider success display-only until IPN verification', () => {
    expect(classifyGuestPaymentOutcome({ responseCode: '00' }).kind).toBe('PROVIDER_SUCCESS_PENDING_VERIFICATION');
  });

  it('shows provider response code for non-success responses', () => {
    const outcome = classifyGuestPaymentOutcome({ responseCode: '24' });
    expect(outcome.kind).toBe('PROVIDER_ERROR');
    expect(outcome.title).toContain('24');
  });

  it('distinguishes network timeout from provider rejection', () => {
    expect(classifyGuestPaymentOutcome({ error: { code: 'NETWORK_ERROR', status: 0 } }).kind).toBe('NETWORK_TIMEOUT');
    expect(classifyGuestPaymentOutcome({ error: { code: 'HOLD_EXPIRED', status: 409 } }).kind).toBe('EXPIRED');
  });
});
