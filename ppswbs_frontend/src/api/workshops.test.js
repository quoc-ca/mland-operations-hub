import { describe, it, afterEach } from 'vitest';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  WorkshopApiError,
  changeTicketRecipient,
  createBookingGroup,
  exchangeGroupMagicLinkToken,
  getApiBaseUrl,
  getOwnedBookingGroup,
  getWorkshopPackage,
  initiateGroupPayment,
  isValidUuid,
  listMemberBookingGroups,
  listWorkshopPackages,
  listWorkshopSessions,
  lookupGuestBookingGroup,
  quoteWorkshopCart,
  releaseGroupPendingHold,
  requestGroupConfirmationDelivery,
} from './workshops.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SESSION = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const SESSION_TWO = '1b4e28ba-2fa1-11d2-883f-0016d3cca427';

function captureFetch(response = {}) {
  const captured = {};
  globalThis.fetch = async (url, options) => {
    captured.url = url;
    captured.options = options;
    return {
      ok: response.ok !== false,
      status: response.status || 200,
      headers: { get: () => null },
      json: async () => (response.body === undefined ? {} : response.body),
    };
  };
  return captured;
}

/**
 * 003.1 T102/T103: the client speaks only the booking-group contract
 * (contracts/workshop-booking.openapi.yaml, where T104 moved the implemented group routes). The single-booking
 * routes of FR-046 are deleted, so no call may reach them.
 */
describe('003.1 workshop booking group client (decision 2026-11-13)', () => {
  const originalFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = originalFetch; });

  describe('Cart quote (POST /api/v1/workshop-cart-quotes)', () => {
    it('posts every cart line and sends no Authorization for a Guest', async () => {
      const captured = captureFetch({ status: 201, body: { quoteRef: 'q-1' } });
      const result = await quoteWorkshopCart({
        lines: [
          { packageId: 5, sessionReferences: [SESSION], participantCount: 2 },
          { packageId: 7, sessionReferences: [SESSION, SESSION_TWO], participantCount: 1 },
        ],
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-cart-quotes'));
      assert.strictEqual(captured.options.method, 'POST');
      assert.strictEqual(captured.options.headers.Authorization, undefined);
      assert.deepStrictEqual(JSON.parse(captured.options.body), {
        lines: [
          { packageId: 5, sessionReferences: [SESSION], participantCount: 2 },
          { packageId: 7, sessionReferences: [SESSION, SESSION_TWO], participantCount: 1 },
        ],
      });
      assert.strictEqual(result.quoteRef, 'q-1');
    });

    it('sends the Member bearer and the normalized benefit selection', async () => {
      const captured = captureFetch({ status: 201, body: { quoteRef: 'q-2', pointsUsed: 0 } });
      await quoteWorkshopCart({
        lines: [{ packageId: 5, sessionReferences: [SESSION], participantCount: 1 }],
        benefitSelection: { choice: 'VOUCHER', voucherCode: ' WS10 ', requestedPoints: '150' },
        authorization: 'Bearer member-token',
      });
      assert.strictEqual(captured.options.headers.Authorization, 'Bearer member-token');
      assert.deepStrictEqual(JSON.parse(captured.options.body).benefitSelection, {
        choice: 'VOUCHER',
        voucherCode: 'WS10',
        requestedPoints: '150',
      });
    });

    it('refuses an empty cart, a line without sessions and a non-UUID session reference', async () => {
      await assert.rejects(() => quoteWorkshopCart({ lines: [] }), { message: 'CART_LINES_REQUIRED' });
      await assert.rejects(
        () => quoteWorkshopCart({ lines: [{ packageId: 5, sessionReferences: [], participantCount: 1 }] }),
        { message: 'SESSION_REFERENCES_REQUIRED: line 0' },
      );
      await assert.rejects(
        () => quoteWorkshopCart({ lines: [{ packageId: 5, sessionReferences: ['not-a-uuid'], participantCount: 1 }] }),
        { message: 'INVALID_SESSION_REFERENCE_UUID: not-a-uuid' },
      );
      await assert.rejects(
        () => quoteWorkshopCart({ lines: [{ packageId: 5, sessionReferences: [SESSION], participantCount: 0 }] }),
        { message: 'PARTICIPANT_COUNT_INVALID: line 0' },
      );
    });
  });

  describe('Group creation (POST /api/v1/workshop-booking-groups)', () => {
    it('confirms the quote with an Idempotency-Key of 16 to 128 characters', async () => {
      const captured = captureFetch({ status: 201, body: { groupCode: 'WG-1', groupStatus: 'PENDING_PAYMENT' } });
      const created = await createBookingGroup({
        quoteRef: 'quote-ref-1',
        contactEmail: ' guest@mland.vn ',
        contactPhone: ' 0901234567 ',
        idempotencyKey: 'idem-key-0123456789',
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-groups'));
      assert.strictEqual(captured.options.method, 'POST');
      assert.strictEqual(captured.options.headers['Idempotency-Key'], 'idem-key-0123456789');
      assert.deepStrictEqual(JSON.parse(captured.options.body), {
        quoteRef: 'quote-ref-1',
        contactEmail: 'guest@mland.vn',
        contactPhone: '0901234567',
      });
      assert.strictEqual(created.groupCode, 'WG-1');
    });

    it('validates the quote reference and the contact before any request', async () => {
      await assert.rejects(() => createBookingGroup({}), { message: 'QUOTE_REF_REQUIRED' });
      await assert.rejects(
        () => createBookingGroup({ quoteRef: 'q', contactEmail: 'nope' }),
        { message: 'CONTACT_EMAIL_INVALID' },
      );
      await assert.rejects(
        () => createBookingGroup({ quoteRef: 'q', contactEmail: 'guest@mland.vn', contactPhone: '123' }),
        { message: 'CONTACT_PHONE_INVALID' },
      );
    });

    it('surfaces the failing cart lines of a 409 so the cart stays editable (FR-031)', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: { get: () => 'corr-1' },
        json: async () => ({ code: 'CART_HOLD_REJECTED', detail: 'Nothing was held', failingLines: [1] }),
      });
      await assert.rejects(
        () => createBookingGroup({
          quoteRef: 'q-1',
          contactEmail: 'guest@mland.vn',
          contactPhone: '0901234567',
          idempotencyKey: 'idem-key-0123456789',
        }),
        error => {
          assert.ok(error instanceof WorkshopApiError);
          assert.strictEqual(error.status, 409);
          assert.strictEqual(error.code, 'CART_HOLD_REJECTED');
          assert.deepStrictEqual(error.data.failingLines, [1]);
          return true;
        },
      );
    });
  });

  describe('Group payment (POST /api/v1/workshop-booking-groups/{groupCode}/payment-attempts)', () => {
    it('opens the single group payment with a command key', async () => {
      const captured = captureFetch({ body: { paymentUrl: 'https://vnpay.example/pay', status: 'CREATED' } });
      const attempt = await initiateGroupPayment('WG-1', { idempotencyKey: 'idem-key-0123456789' });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-groups/WG-1/payment-attempts'));
      assert.strictEqual(captured.options.method, 'POST');
      assert.strictEqual(captured.options.headers['Idempotency-Key'], 'idem-key-0123456789');
      assert.strictEqual(attempt.paymentUrl, 'https://vnpay.example/pay');
    });

    it('requires the group code', async () => {
      await assert.rejects(() => initiateGroupPayment(''), { message: 'GROUP_CODE_REQUIRED' });
    });
  });

  describe('Group reads (FR-048)', () => {
    it('reads an owned group with the Member bearer', async () => {
      const captured = captureFetch({ body: { groupCode: 'WG-1', tickets: [] } });
      await getOwnedBookingGroup('WG-1', { authorization: 'Bearer member-token' });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-groups/WG-1'));
      assert.strictEqual(captured.options.method, 'GET');
      assert.strictEqual(captured.options.headers.Authorization, 'Bearer member-token');
    });

    it('lists the Member groups and always answers with an array', async () => {
      const captured = captureFetch({ body: null });
      const groups = await listMemberBookingGroups({ authorization: 'Bearer member-token' });
      assert.ok(captured.url.pathname.endsWith('/api/v1/members/me/workshop-booking-groups'));
      assert.deepStrictEqual(groups, []);
    });

    it('looks a Guest group up by body, with exactly one contact proof', async () => {
      const captured = captureFetch({ body: { groupCode: 'WG-1' } });
      await lookupGuestBookingGroup({ groupCode: 'WG-1', contactPhone: '0901234567' });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-group-lookups'));
      assert.deepStrictEqual(JSON.parse(captured.options.body), { groupCode: 'WG-1', contactPhone: '0901234567' });
      // The proof never travels in the URL.
      assert.strictEqual(captured.url.search, '');

      await assert.rejects(
        () => lookupGuestBookingGroup({ groupCode: 'WG-1', contactEmail: 'guest@mland.vn', contactPhone: '0901234567' }),
        { message: 'EXACTLY_ONE_CONTACT_PROOF_REQUIRED' },
      );
      await assert.rejects(
        () => lookupGuestBookingGroup({ groupCode: 'WG-1' }),
        { message: 'EXACTLY_ONE_CONTACT_PROOF_REQUIRED' },
      );
    });

    it('exchanges the group Magic-Link token in the request body', async () => {
      const captured = captureFetch({ body: { groupCode: 'WG-1' } });
      await exchangeGroupMagicLinkToken(' token-value ');
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-group-magic-links/exchanges'));
      assert.deepStrictEqual(JSON.parse(captured.options.body), { token: 'token-value' });
      assert.strictEqual(captured.url.search, '');
      await assert.rejects(() => exchangeGroupMagicLinkToken(''), { message: 'MAGIC_LINK_TOKEN_REQUIRED' });
    });
  });

  describe('Group recovery commands (FR-048)', () => {
    it('releases the pending hold with a reason and the Guest proof', async () => {
      const captured = captureFetch({ status: 204 });
      await releaseGroupPendingHold('WG-1', {
        reason: 'Khách đổi lịch',
        contactPhone: '0901234567',
        idempotencyKey: 'idem-key-0123456789',
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-groups/WG-1/pending-hold-release'));
      assert.deepStrictEqual(JSON.parse(captured.options.body), {
        reason: 'Khách đổi lịch',
        contactPhone: '0901234567',
      });
      await assert.rejects(
        () => releaseGroupPendingHold('WG-1', { reason: '' }),
        { message: 'RELEASE_REASON_REQUIRED' },
      );
    });

    it('asks for a confirmation resend, with no body for a Member', async () => {
      const captured = captureFetch({ status: 202, body: { outcome: 'QUEUED' } });
      await requestGroupConfirmationDelivery('WG-1', {
        authorization: 'Bearer member-token',
        idempotencyKey: 'idem-key-0123456789',
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-groups/WG-1/confirmation-deliveries'));
      assert.strictEqual(captured.options.body, undefined);
      assert.strictEqual(captured.options.headers.Authorization, 'Bearer member-token');
    });
  });

  describe('Ticket recipient (PUT /api/v1/workshop-tickets/{ticketCode}/recipient)', () => {
    it('sends one ticket to a recipient with the buyer verification of a Guest', async () => {
      const captured = captureFetch({ body: { ticketCode: 'WT-1', recipientEmailMasked: 'f***@mland.vn' } });
      await changeTicketRecipient('WT-1', {
        recipientEmail: ' friend@mland.vn ',
        buyerVerificationReference: 'b2f1c0de-0000-4000-8000-000000000000',
        idempotencyKey: 'idem-key-0123456789',
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-tickets/WT-1/recipient'));
      assert.strictEqual(captured.options.method, 'PUT');
      assert.deepStrictEqual(JSON.parse(captured.options.body), {
        recipientEmail: 'friend@mland.vn',
        buyerVerificationReference: 'b2f1c0de-0000-4000-8000-000000000000',
      });
      await assert.rejects(
        () => changeTicketRecipient('WT-1', { recipientEmail: 'nope' }),
        { message: 'RECIPIENT_EMAIL_INVALID' },
      );
    });
  });

  describe('Discovery stays owned by Workshop Operations (003.2)', () => {
    it('reads packages and sessions without any booking state', async () => {
      const captured = captureFetch({ body: [] });
      await listWorkshopPackages();
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-packages'));
      await getWorkshopPackage(5);
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-packages/5'));
      await listWorkshopSessions(5, { locationId: 2, from: '2026-11-13', to: '2026-11-27' });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-packages/5/sessions'));
      assert.strictEqual(captured.url.searchParams.get('locationId'), '2');
    });
  });

  describe('Client basics', () => {
    it('validates canonical UUID strings and resolves the API base URL', () => {
      assert.ok(isValidUuid(SESSION));
      assert.strictEqual(isValidUuid('not-a-uuid'), false);
      assert.ok(getApiBaseUrl().startsWith('http'));
      assert.strictEqual(getApiBaseUrl().endsWith('/'), false);
    });

    it('turns a problem response into a WorkshopApiError with its code and correlation id', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 404,
        headers: { get: () => 'corr-9' },
        json: async () => ({ code: 'BOOKING_NOT_FOUND', detail: 'Không tìm thấy' }),
      });
      await assert.rejects(
        () => getOwnedBookingGroup('WG-404', { authorization: 'Bearer member-token' }),
        error => {
          assert.ok(error instanceof WorkshopApiError);
          assert.strictEqual(error.code, 'BOOKING_NOT_FOUND');
          assert.strictEqual(error.correlationId, 'corr-9');
          return true;
        },
      );
    });
  });

  describe('Contract alignment (contracts/workshop-booking.openapi.yaml)', () => {
    // T104 (2026-11-14) moved the implemented group routes out of
    // workshop-booking-cart.openapi.yaml into workshop-booking.openapi.yaml.
    const contractPath = path.resolve(
      __dirname,
      '../../../ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml',
    );

    it('declares every group route this client calls', () => {
      assert.ok(fs.existsSync(contractPath), 'workshop-booking.openapi.yaml must exist');
      const contract = fs.readFileSync(contractPath, 'utf8');
      [
        '/api/v1/workshop-cart-quotes:',
        '/api/v1/workshop-booking-groups:',
        '/api/v1/workshop-booking-groups/{groupCode}:',
        '/api/v1/workshop-booking-groups/{groupCode}/payment-attempts:',
        '/api/v1/workshop-booking-groups/{groupCode}/pending-hold-release:',
        '/api/v1/workshop-booking-groups/{groupCode}/confirmation-deliveries:',
        '/api/v1/workshop-booking-groups/{groupCode}/magic-link-incidents:',
        '/api/v1/workshop-booking-groups/{groupCode}/buyer-verifications:',
        '/api/v1/members/me/workshop-booking-groups:',
        '/api/v1/workshop-booking-group-lookups:',
        '/api/v1/workshop-booking-group-magic-links/exchanges:',
        '/api/v1/workshop-tickets/{ticketCode}/recipient:',
        '/api/v1/late-payment-reviews:',
      ].forEach(route => assert.ok(contract.includes(route), `the contract must declare ${route}`));
    });

    it('no longer declares the single-booking paths removed by FR-046', () => {
      const contract = fs.readFileSync(contractPath, 'utf8');
      [
        '/api/v1/workshop-bookings:',
        '/api/v1/workshop-booking-quotes:',
        '/api/v1/workshop-bookings/{bookingCode}/payment-attempts:',
        '/api/v1/workshop-bookings/{bookingCode}/pending-hold-release:',
        '/api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries:',
        '/api/v1/workshop-bookings/{bookingCode}/magic-link-incidents:',
        '/api/v1/workshop-bookings/{bookingCode}/late-payment-follow-ups:',
        '/api/v1/booking-lookups:',
        '/api/v1/booking-magic-links/exchanges:',
        '/api/v1/members/me/workshop-bookings:',
        '/api/v1/members/me/workshop-bookings/{bookingCode}:',
      ].forEach(route => assert.strictEqual(
        contract.includes(route),
        false,
        `the contract must no longer declare the removed path ${route}`,
      ));
    });

    it('declares the cart quote point limits and reasons the screen shows', () => {
      const contract = fs.readFileSync(contractPath, 'utf8');
      ['maxPoints', 'minRedeemPoints', 'pointValueVnd', 'reasons']
        .forEach(field => assert.ok(contract.includes(`${field}:`), `CartQuote must declare ${field}`));
    });

    it('calls none of the single-booking routes removed by FR-046', () => {
      const client = fs.readFileSync(path.resolve(__dirname, './workshops.js'), 'utf8');
      [
        "'/api/v1/workshop-bookings'",
        '/api/v1/workshop-booking-quotes',
        '/api/v1/booking-lookups',
        '/api/v1/booking-magic-links/exchanges',
        '/api/v1/members/me/workshop-bookings',
      ].forEach(route => assert.strictEqual(
        client.includes(route),
        false,
        `the client must not call the removed route ${route}`,
      ));
    });
  });
});
