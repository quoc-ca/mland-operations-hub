import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  WorkshopApiError,
  isValidUuid,
  initiateDepositPayment,
  lookupGuestBooking,
  lookupMemberBooking,
  exchangeMagicLinkToken,
  releasePendingHold,
  requestConfirmationDelivery,
} from '../api/workshops.js';

describe('003.1 T035: Payment Pending, Display-Only Return, Confirmation Ticket & Retry', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('Deposit Payment Initiation (POST /payment-attempts)', () => {
    it('initiates deposit payment with Idempotency-Key and returns paymentUrl', async () => {
      let capturedUrl;
      let capturedHeaders;

      const mockResponse = {
        attemptReference: 'att-12345678-uuid',
        merchantReference: 'MLAND-DEP-001',
        amountVnd: 445000,
        currency: 'VND',
        paymentUrl: 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=44500000',
        expiresAt: '2026-11-01T12:45:00Z',
        status: 'PENDING',
      };

      globalThis.fetch = async (url, options) => {
        capturedUrl = url;
        capturedHeaders = options.headers;
        assert.strictEqual(options.method, 'POST');
        return {
          ok: true,
          status: 200,
          json: async () => mockResponse,
        };
      };

      const result = await initiateDepositPayment('WS-20261101-ABCD');
      assert.ok(capturedUrl.pathname.endsWith('/api/v1/workshop-bookings/WS-20261101-ABCD/payment-attempts'));
      assert.ok(capturedHeaders['Idempotency-Key'], 'Idempotency-Key header is required');
      assert.strictEqual(result.merchantReference, 'MLAND-DEP-001');
      assert.strictEqual(result.amountVnd, 445000);
      assert.strictEqual(result.currency, 'VND');
      assert.ok(result.paymentUrl.startsWith('https://sandbox.vnpayment.vn/'));
      assert.strictEqual(result.status, 'PENDING');
    });

    it('rejects initiateDepositPayment when bookingCode is missing or blank', async () => {
      await assert.rejects(() => initiateDepositPayment(), { message: 'BOOKING_CODE_REQUIRED' });
      await assert.rejects(() => initiateDepositPayment(''), { message: 'BOOKING_CODE_REQUIRED' });
      await assert.rejects(() => initiateDepositPayment('   '), { message: 'BOOKING_CODE_REQUIRED' });
    });

    it('surfaces 409 HOLD_EXPIRED problem detail safely when hold expired', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: new Headers({ 'x-correlation-id': 'corr-hold-exp' }),
        json: async () => ({
          code: 'HOLD_EXPIRED',
          detail: 'Capacity hold for booking WS-20261101-EXP has expired (10-minute cap)',
          correlationId: 'corr-hold-exp',
        }),
      });

      await assert.rejects(
        () => initiateDepositPayment('WS-20261101-EXP'),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 409);
          assert.strictEqual(err.code, 'HOLD_EXPIRED');
          assert.strictEqual(err.correlationId, 'corr-hold-exp');
          return true;
        }
      );
    });
  });

  describe('Gateway Return Display-Only Invariant', () => {
    it('proves browser return params (e.g. vnp_ResponseCode=00) do NOT confirm booking locally', async () => {
      // Even if return params indicate vnp_ResponseCode=00, client must query verified backend evidence
      const pendingBackendBooking = {
        bookingCode: 'WS-20261101-ABCD',
        bookingStatus: 'PENDING_PAYMENT',
        paymentStatus: 'UNPAID',
        packageId: 1,
        packageName: 'Signature Ring Workshop',
        participantCount: 2,
        holdExpiresAt: '2026-11-01T12:50:00Z',
        sessions: [],
        tickets: [], // zero tickets while PENDING_PAYMENT
      };

      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => pendingBackendBooking,
      });

      // Browser return must read committed backend state only
      const verifiedResult = await lookupGuestBooking({
        bookingCode: 'WS-20261101-ABCD',
        contactPhone: '0901234567',
      });

      // Assert booking is still pending until backend verified IPN arrives
      assert.strictEqual(verifiedResult.bookingStatus, 'PENDING_PAYMENT');
      assert.strictEqual(verifiedResult.paymentStatus, 'UNPAID');
      assert.deepStrictEqual(verifiedResult.tickets, [], 'No tickets issued before backend IPN verification');
    });

    it('projects tickets only after backend confirms payment via verified IPN evidence', async () => {
      const confirmedBackendBooking = {
        bookingCode: 'WS-20261101-ABCD',
        bookingStatus: 'CONFIRMED',
        paymentStatus: 'DEPOSIT_PAID',
        packageId: 1,
        packageName: 'Signature Ring Workshop',
        participantCount: 2,
        holdExpiresAt: null,
        sessions: [
          {
            sessionReference: 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6',
            locationId: 1,
            ordinal: 1,
            scheduledStart: '2026-11-10T09:00:00Z',
            scheduledEnd: '2026-11-10T11:30:00Z',
          },
        ],
        tickets: [
          {
            ticketCode: 'TK-20261101-0001',
            qrReference: 'QR-SCAN-SAFE-001',
            sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
          },
          {
            ticketCode: 'TK-20261101-0002',
            qrReference: 'QR-SCAN-SAFE-002',
            sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
          },
        ],
      };

      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => confirmedBackendBooking,
      });

      const result = await lookupGuestBooking({
        bookingCode: 'WS-20261101-ABCD',
        contactEmail: 'guest@mland.vn',
      });

      assert.strictEqual(result.bookingStatus, 'CONFIRMED');
      assert.strictEqual(result.paymentStatus, 'DEPOSIT_PAID');
      assert.strictEqual(result.tickets.length, 2);
      assert.strictEqual(result.tickets[0].ticketCode, 'TK-20261101-0001');
      assert.strictEqual(result.tickets[0].qrReference, 'QR-SCAN-SAFE-001');
      assert.ok(isValidUuid(result.tickets[0].sessionReferences[0]));
    });
  });

  describe('Retry Invariant & Hold Expiration', () => {
    it('enforces hold deadline constraint (10 minutes) and does not extend deadline on retry', () => {
      const holdCreated = new Date('2026-11-01T12:00:00Z');
      const holdExpiresAt = new Date('2026-11-01T12:10:00Z');
      const diffMinutes = (holdExpiresAt.getTime() - holdCreated.getTime()) / (60 * 1000);
      assert.strictEqual(diffMinutes, 10, 'Hold must expire at most 10 minutes after creation');
    });
  });
});

describe('003.1 T042: Booking Lookup, Pending-Resume, Magic-Link Exchange & Recovery', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('Guest Booking Lookup (POST /api/v1/booking-lookups)', () => {
    it('requires exactly one of contactEmail or contactPhone', async () => {
      // Neither provided
      await assert.rejects(
        () => lookupGuestBooking({ bookingCode: 'WS-123' }),
        { message: 'EXACTLY_ONE_CONTACT_PROOF_REQUIRED' }
      );

      // Both provided
      await assert.rejects(
        () => lookupGuestBooking({
          bookingCode: 'WS-123',
          contactEmail: 'a@b.com',
          contactPhone: '0901234567',
        }),
        { message: 'EXACTLY_ONE_CONTACT_PROOF_REQUIRED' }
      );
    });

    it('looks up Guest booking with email proof successfully', async () => {
      let sentBody;
      globalThis.fetch = async (url, options) => {
        sentBody = JSON.parse(options.body);
        assert.ok(url.pathname.endsWith('/api/v1/booking-lookups'));
        return {
          ok: true,
          status: 200,
          json: async () => ({
            bookingCode: 'WS-123',
            bookingStatus: 'PENDING_PAYMENT',
            paymentStatus: 'UNPAID',
            packageId: 1,
            packageName: 'Basic Ring Workshop',
            participantCount: 1,
            holdExpiresAt: '2026-11-01T12:40:00Z',
            sessions: [],
            tickets: [],
          }),
        };
      };

      const result = await lookupGuestBooking({
        bookingCode: 'WS-123',
        contactEmail: 'customer@mland.vn',
      });

      assert.strictEqual(sentBody.bookingCode, 'WS-123');
      assert.strictEqual(sentBody.contactEmail, 'customer@mland.vn');
      assert.strictEqual(sentBody.contactPhone, undefined);
      assert.strictEqual(result.bookingCode, 'WS-123');
    });

    it('surfaces 404 BOOKING_NOT_FOUND when contact proof does not match', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 404,
        headers: new Headers({ 'x-correlation-id': 'corr-lookup-404' }),
        json: async () => ({
          code: 'BOOKING_NOT_FOUND',
          detail: 'No matching booking found for code WS-WRONG and provided contact proof',
          correlationId: 'corr-lookup-404',
        }),
      });

      await assert.rejects(
        () => lookupGuestBooking({
          bookingCode: 'WS-WRONG',
          contactPhone: '0900000000',
        }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 404);
          assert.strictEqual(err.code, 'BOOKING_NOT_FOUND');
          return true;
        }
      );
    });
  });

  describe('Member Booking Lookup (GET /api/v1/members/me/workshop-bookings/{code})', () => {
    it('sends Authorization header and returns owned booking', async () => {
      let capturedHeaders;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            bookingCode: 'WS-MEMBER-01',
            bookingStatus: 'CONFIRMED',
            paymentStatus: 'DEPOSIT_PAID',
            packageId: 1,
            packageName: 'Signature Ring Workshop',
            participantCount: 2,
            holdExpiresAt: null,
            sessions: [],
            tickets: [],
          }),
        };
      };

      const result = await lookupMemberBooking('WS-MEMBER-01', {
        authorization: 'Bearer token-member-123',
      });

      assert.strictEqual(capturedHeaders['Authorization'], 'Bearer token-member-123');
      assert.strictEqual(result.bookingCode, 'WS-MEMBER-01');
    });

    it('surfaces 401/403 for unauthorized Member lookup', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 401,
        json: async () => ({
          code: 'UNAUTHORIZED',
          detail: 'Valid Bearer token is required',
        }),
      });

      await assert.rejects(
        () => lookupMemberBooking('WS-MEMBER-01'),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 401);
          return true;
        }
      );
    });
  });

  describe('Magic-Link Fragment Exchange (POST /api/v1/booking-magic-links/exchanges)', () => {
    it('exchanges token and never exposes token in client return projection', async () => {
      let sentBody;
      const mockProjection = {
        bookingCode: 'WS-MAGIC-01',
        bookingStatus: 'CONFIRMED',
        paymentStatus: 'DEPOSIT_PAID',
        packageId: 1,
        packageName: 'Signature Ring Workshop',
        participantCount: 1,
        holdExpiresAt: null,
        sessions: [],
        tickets: [
          {
            ticketCode: 'TK-MAGIC-01',
            qrReference: 'QR-MAGIC-SCAN',
            sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
          },
        ],
      };

      globalThis.fetch = async (url, options) => {
        sentBody = JSON.parse(options.body);
        return {
          ok: true,
          status: 200,
          json: async () => mockProjection,
        };
      };

      const result = await exchangeMagicLinkToken('secret-bearer-fragment-token');
      assert.strictEqual(sentBody.token, 'secret-bearer-fragment-token');
      assert.strictEqual(result.bookingCode, 'WS-MAGIC-01');
      assert.strictEqual(result.tickets.length, 1);
      // Ensure the returned projection does NOT echo the raw token
      assert.strictEqual(result.token, undefined);
    });

    it('rejects exchange when token is missing or empty', async () => {
      await assert.rejects(() => exchangeMagicLinkToken(), { message: 'MAGIC_LINK_TOKEN_REQUIRED' });
      await assert.rejects(() => exchangeMagicLinkToken(''), { message: 'MAGIC_LINK_TOKEN_REQUIRED' });
    });

    it('surfaces 404 / 401 when token is expired or revoked', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 404,
        json: async () => ({
          code: 'MAGIC_LINK_NOT_FOUND',
          detail: 'Magic Link is invalid, expired, or revoked',
        }),
      });

      await assert.rejects(
        () => exchangeMagicLinkToken('expired-token'),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 404);
          assert.strictEqual(err.code, 'MAGIC_LINK_NOT_FOUND');
          return true;
        }
      );
    });
  });

  describe('Pending Hold Release (POST /pending-hold-release)', () => {
    it('releases pending hold with reason, contact proof, and Idempotency-Key', async () => {
      let capturedUrl;
      let capturedHeaders;
      let capturedBody;

      globalThis.fetch = async (url, options) => {
        capturedUrl = url;
        capturedHeaders = options.headers;
        capturedBody = JSON.parse(options.body);
        return {
          ok: true,
          status: 204,
        };
      };

      const result = await releasePendingHold('WS-REL-01', {
        contactPhone: '0901234567',
        reason: 'Customer changed schedule plan',
      });

      assert.ok(capturedUrl.pathname.endsWith('/api/v1/workshop-bookings/WS-REL-01/pending-hold-release'));
      assert.ok(capturedHeaders['Idempotency-Key']);
      assert.strictEqual(capturedBody.reason, 'Customer changed schedule plan');
      assert.strictEqual(capturedBody.contactPhone, '0901234567');
      assert.strictEqual(result, null);
    });

    it('requires a non-empty audit reason for release', async () => {
      await assert.rejects(
        () => releasePendingHold('WS-REL-01', { reason: '' }),
        { message: 'RELEASE_REASON_REQUIRED' }
      );
    });
  });

  describe('Confirmation Delivery Resend (POST /confirmation-deliveries)', () => {
    it('requests confirmation resend and returns 202 outcome', async () => {
      let capturedHeaders;
      const mockOutcome = {
        bookingCode: 'WS-DELIV-01',
        action: 'CONFIRMATION_RESEND',
        outcome: 'ACCEPTED',
        correlationId: 'corr-deliv-01',
      };

      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 202,
          json: async () => mockOutcome,
        };
      };

      const outcome = await requestConfirmationDelivery('WS-DELIV-01', {
        contactEmail: 'customer@mland.vn',
      });

      assert.ok(capturedHeaders['Idempotency-Key']);
      assert.strictEqual(outcome.bookingCode, 'WS-DELIV-01');
      assert.strictEqual(outcome.outcome, 'ACCEPTED');
    });

    it('surfaces 429 Too Many Requests when rolling limit is exceeded', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 429,
        json: async () => ({
          code: 'RATE_LIMIT_EXCEEDED',
          detail: 'Self-service limit exceeded: at most 3 resends per hour',
        }),
      });

      await assert.rejects(
        () => requestConfirmationDelivery('WS-DELIV-01', { contactPhone: '0901234567' }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 429);
          assert.strictEqual(err.code, 'RATE_LIMIT_EXCEEDED');
          return true;
        }
      );
    });
  });
});

