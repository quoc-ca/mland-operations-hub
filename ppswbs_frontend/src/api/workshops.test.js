import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  WorkshopApiError,
  isValidUuid,
  listWorkshopPackages,
  getWorkshopPackage,
  listWorkshopSessions,
  previewWorkshopBookingQuote,
  createWorkshopBooking,
  getApiBaseUrl,
} from './workshops.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe('003.1 T016: Workshop Discovery & Booking Client Contract', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('Member workshop quote preview', () => {
    it('posts benefit selection and preserves maxPoints from the server response', async () => {
      let captured;
      globalThis.fetch = async (url, options) => {
        captured = { url, options };
        return {
          ok: true,
          status: 201,
          json: async () => ({ quoteRef: 'quote-1', maxPoints: '1250', postPromotionAmount: '450000' }),
        };
      };
      const result = await previewWorkshopBookingQuote({
        packageId: 5,
        sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
        participantCount: 1,
        benefitSelection: { choice: 'NONE', requestedPoints: '0' },
        authorization: 'Bearer member-token',
      });
      assert.ok(captured.url.pathname.endsWith('/api/v1/workshop-booking-quotes'));
      assert.strictEqual(captured.options.method, 'POST');
      assert.strictEqual(captured.options.headers.Authorization, 'Bearer member-token');
      assert.deepStrictEqual(JSON.parse(captured.options.body), {
        packageId: 5,
        sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
        participantCount: 1,
        benefitSelection: { choice: 'NONE', requestedPoints: '0' },
      });
      assert.strictEqual(result.maxPoints, '1250');
    });
  });

  describe('UUID validation & Opaque sessionReference', () => {
    it('validates canonical 36-character UUID string format', () => {
      assert.strictEqual(isValidUuid('f81d4fae-7dec-11d0-a765-00a0c91e6bf6'), true);
      assert.strictEqual(isValidUuid('a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d'), true);
      // Rejects numeric legacy ID
      assert.strictEqual(isValidUuid('12345'), false);
      assert.strictEqual(isValidUuid(12345), false);
      // Rejects invalid string formats
      assert.strictEqual(isValidUuid('not-a-uuid'), false);
      assert.strictEqual(isValidUuid(''), false);
      assert.strictEqual(isValidUuid(null), false);
    });
  });

  describe('Package Discovery (003.2 Workshop Operations-owned)', () => {
    it('listWorkshopPackages fetches published packages from /api/v1/workshop-packages', async () => {
      const mockPackages = [
        {
          packageId: 1,
          name: 'Signature Ring Workshop',
          description: 'Custom silver ring crafting',
          publicPrice: 890000,
          minParticipants: 1,
          maxParticipants: 4,
          requiredSessionCount: 1,
          sameLocationRequired: true,
          minimumSameDaySlotGap: 0,
          state: 'PUBLISHED',
        },
      ];

      globalThis.fetch = async (url, options) => {
        assert.strictEqual(options.method, 'GET');
        assert.ok(url.pathname.endsWith('/api/v1/workshop-packages'));
        return {
          ok: true,
          status: 200,
          json: async () => mockPackages,
        };
      };

      const result = await listWorkshopPackages();
      assert.strictEqual(result.length, 1);
      assert.strictEqual(result[0].packageId, 1);
      assert.strictEqual(result[0].name, 'Signature Ring Workshop');
    });

    it('listWorkshopPackages handles empty package list gracefully', async () => {
      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => [],
      });

      const result = await listWorkshopPackages();
      assert.deepStrictEqual(result, []);
    });

    it('getWorkshopPackage throws when packageId is missing or empty', async () => {
      await assert.rejects(() => getWorkshopPackage(), { message: 'WORKSHOP_PACKAGE_ID_REQUIRED' });
      await assert.rejects(() => getWorkshopPackage(''), { message: 'WORKSHOP_PACKAGE_ID_REQUIRED' });
      await assert.rejects(() => getWorkshopPackage(null), { message: 'WORKSHOP_PACKAGE_ID_REQUIRED' });
    });

    it('getWorkshopPackage retrieves one published package by id', async () => {
      const mockPackage = {
        packageId: 2,
        name: 'Silver Clay Workshop',
        description: 'Two-session silver clay creation',
        publicPrice: 1290000,
        minParticipants: 1,
        maxParticipants: 2,
        requiredSessionCount: 2,
        sameLocationRequired: true,
        minimumSameDaySlotGap: 1,
        state: 'PUBLISHED',
      };

      globalThis.fetch = async (url) => {
        assert.ok(url.pathname.endsWith('/api/v1/workshop-packages/2'));
        return {
          ok: true,
          status: 200,
          json: async () => mockPackage,
        };
      };

      const result = await getWorkshopPackage(2);
      assert.strictEqual(result.packageId, 2);
      assert.strictEqual(result.requiredSessionCount, 2);
    });

    it('getWorkshopPackage parses 404 Problem Detail into typed WorkshopApiError', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 404,
        headers: new Headers({ 'x-correlation-id': 'corr-404-pkg' }),
        json: async () => ({
          code: 'PACKAGE_NOT_FOUND',
          detail: 'Workshop package 999 does not exist',
          correlationId: 'corr-404-pkg',
        }),
      });

      await assert.rejects(
        () => getWorkshopPackage(999),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 404);
          assert.strictEqual(err.code, 'PACKAGE_NOT_FOUND');
          assert.strictEqual(err.correlationId, 'corr-404-pkg');
          assert.strictEqual(err.detail, 'Workshop package 999 does not exist');
          return true;
        }
      );
    });
  });

  describe('Session Discovery (003.2 Workshop Operations-owned)', () => {
    it('listWorkshopSessions throws when packageId or locationId is missing', async () => {
      await assert.rejects(() => listWorkshopSessions(), { message: 'WORKSHOP_PACKAGE_ID_REQUIRED' });
      await assert.rejects(() => listWorkshopSessions(1), { message: 'WORKSHOP_LOCATION_ID_REQUIRED' });
      await assert.rejects(() => listWorkshopSessions(1, { locationId: null }), { message: 'WORKSHOP_LOCATION_ID_REQUIRED' });
    });

    it('listWorkshopSessions sends required locationId and optional from/to query params', async () => {
      let capturedUrl;
      globalThis.fetch = async (url) => {
        capturedUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => [],
        };
      };

      await listWorkshopSessions(1, { locationId: 10, from: '2026-11-05', to: '2026-11-12' });
      assert.ok(capturedUrl.pathname.endsWith('/api/v1/workshop-packages/1/sessions'));
      assert.strictEqual(capturedUrl.searchParams.get('locationId'), '10');
      assert.strictEqual(capturedUrl.searchParams.get('from'), '2026-11-05');
      assert.strictEqual(capturedUrl.searchParams.get('to'), '2026-11-12');
    });

    it('preserves opaque UUID sessionReference and snapshot remainingCapacity', async () => {
      const mockSessions = [
        {
          sessionReference: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
          locationId: 1,
          scheduledStart: '2026-11-05T09:00:00Z',
          scheduledEnd: '2026-11-05T11:30:00Z',
          saleState: 'OPEN_FOR_SALE',
          customerSelectable: true,
          remainingCapacity: 4,
        },
        {
          sessionReference: '7ca85f64-5717-4562-b3fc-2c963f66afa7',
          locationId: 1,
          scheduledStart: '2026-11-05T14:00:00Z',
          scheduledEnd: '2026-11-05T16:30:00Z',
          saleState: 'OPEN_FOR_SALE',
          customerSelectable: true,
          remainingCapacity: 0,
        },
      ];

      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => mockSessions,
      });

      const sessions = await listWorkshopSessions(1, { locationId: 1 });
      assert.strictEqual(sessions.length, 2);

      // Verify every sessionReference is an opaque canonical UUID (never a numeric legacy id)
      for (const s of sessions) {
        assert.ok(isValidUuid(s.sessionReference), `sessionReference must be a valid UUID: ${s.sessionReference}`);
        assert.notStrictEqual(typeof s.sessionReference, 'number');
        assert.ok(typeof s.remainingCapacity === 'number' && s.remainingCapacity >= 0);
      }
    });

    it('handles empty session availability list', async () => {
      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => [],
      });

      const sessions = await listWorkshopSessions(1, { locationId: 1, from: '2026-12-01', to: '2026-12-02' });
      assert.deepStrictEqual(sessions, []);
    });
  });

  describe('Contract Verification: No Duplicate Discovery in Booking Module', () => {
    it('proves 003.1 Booking OpenAPI defines no package discovery controller routes', () => {
      const candidatePaths = [
        path.resolve(__dirname, '../../../ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml'),
        path.resolve(process.cwd(), '../ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml'),
        path.resolve(process.cwd(), 'ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml'),
      ];

      let specContent = null;
      for (const p of candidatePaths) {
        if (fs.existsSync(p)) {
          specContent = fs.readFileSync(p, 'utf8');
          break;
        }
      }

      assert.ok(specContent, 'workshop-booking.openapi.yaml must exist');

      // The booking OpenAPI must NOT contain package discovery paths
      assert.strictEqual(specContent.includes('/workshop-packages:'), false,
        'Booking OpenAPI must not contain GET /workshop-packages');
      assert.strictEqual(specContent.includes('/workshop-packages/{packageId}:'), false,
        'Booking OpenAPI must not contain GET /workshop-packages/{packageId}');
      assert.strictEqual(specContent.includes('/workshop-packages/{packageId}/sessions:'), false,
        'Booking OpenAPI must not contain GET /workshop-packages/{packageId}/sessions');

      // Booking OpenAPI contains only booking-owned routes
      assert.ok(specContent.includes('/api/v1/workshop-bookings:'));
      assert.ok(specContent.includes('/api/v1/workshop-booking-quotes:'));
      assert.ok(specContent.includes('/api/v1/booking-lookups:'));
    });
  });

  describe('Booking Creation (003.1 Workshop Booking-owned)', () => {
    it('createWorkshopBooking validates required contact info and session UUIDs', async () => {
      await assert.rejects(
        () => createWorkshopBooking({}),
        { message: 'CONTACT_EMAIL_INVALID' }
      );

      await assert.rejects(
        () => createWorkshopBooking({ contactEmail: 'invalid-email' }),
        { message: 'CONTACT_EMAIL_INVALID' }
      );

      await assert.rejects(
        () => createWorkshopBooking({ contactEmail: 'guest@mland.vn', contactPhone: '123' }),
        { message: 'CONTACT_PHONE_INVALID' }
      );

      await assert.rejects(
        () => createWorkshopBooking({
          contactEmail: 'guest@mland.vn',
          contactPhone: '0901234567',
          packageId: 1,
          sessionReferences: ['not-a-uuid'],
          participantCount: 2,
        }),
        (err) => err.message.includes('INVALID_SESSION_REFERENCE_UUID')
      );
    });

    it('createWorkshopBooking creates a pending booking and sends Idempotency-Key header', async () => {
      let capturedHeaders;
      let capturedBody;

      const mockResponse = {
        bookingCode: 'WS-20261101-ABCD',
        bookingStatus: 'PENDING_PAYMENT',
        paymentStatus: 'UNPAID',
        packageId: 1,
        packageName: 'Signature Ring Workshop',
        participantCount: 2,
        holdExpiresAt: '2026-11-01T12:35:00Z',
        sessions: [
          {
            sessionReference: 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6',
            locationId: 1,
            ordinal: 1,
            scheduledStart: '2026-11-05T09:00:00Z',
            scheduledEnd: '2026-11-05T11:30:00Z',
          },
        ],
        tickets: [],
      };

      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        capturedBody = JSON.parse(options.body);
        assert.ok(url.pathname.endsWith('/api/v1/workshop-bookings'));
        assert.strictEqual(options.method, 'POST');
        return {
          ok: true,
          status: 201,
          json: async () => mockResponse,
        };
      };

      const result = await createWorkshopBooking({
        packageId: 1,
        sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
        participantCount: 2,
        contactEmail: 'guest@mland.vn',
        contactPhone: '0901234567',
        idempotencyKey: 'idemp-test-key-12345',
      });

      assert.strictEqual(capturedHeaders['Idempotency-Key'], 'idemp-test-key-12345');
      assert.strictEqual(capturedBody.packageId, 1);
      assert.strictEqual(capturedBody.participantCount, 2);
      assert.strictEqual(result.bookingCode, 'WS-20261101-ABCD');
      assert.strictEqual(result.bookingStatus, 'PENDING_PAYMENT');
      assert.strictEqual(result.paymentStatus, 'UNPAID');
    });

    it('surfaces INSUFFICIENT_CAPACITY problem details without exposing PII', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: new Headers({ 'x-correlation-id': 'corr-cap-err' }),
        json: async () => ({
          code: 'INSUFFICIENT_CAPACITY',
          detail: 'One or more requested workshop sessions do not have sufficient capacity',
          correlationId: 'corr-cap-err',
        }),
      });

      await assert.rejects(
        () => createWorkshopBooking({
          packageId: 1,
          sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
          participantCount: 10,
          contactEmail: 'customer@secret.vn',
          contactPhone: '0987654321',
          idempotencyKey: 'idemp-cap-err',
        }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 409);
          assert.strictEqual(err.code, 'INSUFFICIENT_CAPACITY');
          assert.strictEqual(err.correlationId, 'corr-cap-err');
          assert.strictEqual(err.detail.includes('customer@secret.vn'), false, 'PII must not leak into error detail');
          return true;
        }
      );
    });

    it('surfaces PACKAGE_SELECTION_INVALID problem details safely', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: new Headers({ 'x-correlation-id': 'corr-sel-err' }),
        json: async () => ({
          code: 'PACKAGE_SELECTION_INVALID',
          detail: 'Silverclay package requires exactly 2 sessions with minimum 1 slot gap',
          correlationId: 'corr-sel-err',
        }),
      });

      await assert.rejects(
        () => createWorkshopBooking({
          packageId: 2,
          sessionReferences: ['f81d4fae-7dec-11d0-a765-00a0c91e6bf6'],
          participantCount: 1,
          contactEmail: 'customer@mland.vn',
          contactPhone: '0987654321',
          idempotencyKey: 'idemp-sel-err',
        }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 409);
          assert.strictEqual(err.code, 'PACKAGE_SELECTION_INVALID');
          return true;
        }
      );
    });
  });
});
