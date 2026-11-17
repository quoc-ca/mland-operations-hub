import { describe, expect, it } from 'vitest';
import { classifyPaymentCommandError, finalPaymentRecoveryState } from './paymentRecoveryState';

describe('payment recovery UI contract', () => {
  it('treats a lost response as unknown and forbids a blind new attempt', () => {
    expect(classifyPaymentCommandError({ code: 'NETWORK_ERROR', status: 0 })).toMatchObject({
      kind: 'UNKNOWN',
    });
  });

  it('treats a request timeout as an unknown provider result', () => {
    expect(classifyPaymentCommandError({ code: 'REQUEST_TIMEOUT', status: 0 })).toMatchObject({
      kind: 'UNKNOWN',
    });
  });

  it('treats an expired booking as terminal without link reissue', () => {
    const state = classifyPaymentCommandError({ code: 'HOLD_EXPIRED', status: 409 });
    expect(state.kind).toBe('EXPIRED');
    expect(state.message).toContain('cannot be reissued');
  });

  it('blocks a second attempt while a payment link is active', () => {
    const state = finalPaymentRecoveryState(
      { status: 'PENDING', expiresAt: '2026-11-03T10:10:00Z' },
      Date.parse('2026-11-03T10:05:00Z'),
    );
    expect(state.actionDisabled).toBe(true);
    expect(state.kind).toBe('PENDING');
  });

  it('uses reconciliation instead of reissue after expiry', () => {
    const state = finalPaymentRecoveryState(
      { status: 'EXPIRED', expiresAt: '2026-11-03T10:00:00Z' },
      Date.parse('2026-11-03T10:05:00Z'),
    );
    expect(state.actionDisabled).toBe(false);
    expect(state.actionLabel).toBe('Verify expired attempt');
    expect(state.message).toContain('cannot be reissued');
  });

  it('only offers a new attempt after verified unpaid', () => {
    const state = finalPaymentRecoveryState({ status: 'VERIFIED_UNPAID' });
    expect(state.actionDisabled).toBe(false);
    expect(state.actionLabel).toBe('Create new payment attempt');
  });

  it('keeps an unknown provider result locked until its deadline', () => {
    const state = finalPaymentRecoveryState(
      { status: 'UNKNOWN', expiresAt: '2026-11-03T10:10:00Z' },
      Date.parse('2026-11-03T10:05:00Z'),
    );
    expect(state.actionDisabled).toBe(true);
    expect(state.actionLabel).toBe('Await provider result');
  });
});
