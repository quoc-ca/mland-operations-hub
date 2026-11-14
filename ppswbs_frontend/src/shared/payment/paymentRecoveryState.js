export function classifyPaymentCommandError(error) {
  const code = error?.code || '';
  const status = Number(error?.status || 0);

  if (code === 'HOLD_EXPIRED' || code === 'BOOKING_EXPIRED') {
    return {
      kind: 'EXPIRED',
      message: 'The booking hold has expired. The old payment link cannot be reissued; create a new booking.',
    };
  }
  if (code === 'PAYMENT_ATTEMPT_RECONCILIATION_PENDING'
      || code === 'PAYMENT_STATUS_UNKNOWN'
      || code === 'NETWORK_ERROR'
      || code === 'REQUEST_TIMEOUT'
      || status === 0
      || error?.name === 'AbortError') {
    return {
      kind: 'UNKNOWN',
      message: 'The payment result is not yet known. Do not create another transaction; check the status again through the backend.',
    };
  }
  return {
    kind: 'ERROR',
    message: error?.detail || error?.message || 'The payment action cannot be completed right now.',
  };
}

export function finalPaymentRecoveryState(attempt, now = Date.now()) {
  if (!attempt) {
    return {
      kind: 'READY',
      title: 'No payment attempt started',
      message: 'Start one final-payment checkout for the outstanding balance.',
      actionLabel: 'Start final payment',
      actionDisabled: false,
    };
  }

  const status = attempt.status || 'UNKNOWN';
  const expiresAt = attempt.expiresAt ? new Date(attempt.expiresAt).getTime() : null;
  const expiredByClock = Number.isFinite(expiresAt) && expiresAt <= now;

  if (status === 'VERIFIED_PAID') {
    return {
      kind: status,
      title: 'Payment verified',
      message: 'The provider result is verified. No new payment attempt is allowed.',
      actionLabel: 'Payment verified',
      actionDisabled: true,
    };
  }
  if (status === 'VERIFIED_UNPAID') {
    return {
      kind: status,
      title: 'Previous attempt verified unpaid',
      message: 'The server verified that the previous attempt did not succeed. A new attempt may now be created.',
      actionLabel: 'Create new payment attempt',
      actionDisabled: false,
    };
  }
  if (status === 'EXPIRED' || (status === 'PENDING' && expiredByClock)) {
    return {
      kind: 'EXPIRED',
      title: 'Payment attempt expired',
      message: 'The link cannot be reissued. Verify the expired attempt through QueryDR; the server creates a new attempt only after verified unpaid.',
      actionLabel: 'Verify expired attempt',
      actionDisabled: false,
    };
  }
  if (status === 'UNKNOWN') {
    return {
      kind: status,
      title: 'Provider result unknown',
      message: expiredByClock
        ? 'The attempt is past its deadline. Reconcile it with QueryDR before any new attempt is created.'
        : 'Do not retry or create another attempt while the provider result is unknown.',
      actionLabel: expiredByClock ? 'Reconcile unknown attempt' : 'Await provider result',
      actionDisabled: !expiredByClock,
    };
  }
  return {
    kind: 'PENDING',
    title: 'Payment attempt active',
    message: 'Use the active link. A second attempt is blocked until this attempt expires and is reconciled.',
    actionLabel: 'Payment attempt active',
    actionDisabled: true,
  };
}
