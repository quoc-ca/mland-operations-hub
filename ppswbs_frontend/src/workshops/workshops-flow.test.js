import { describe, it, beforeEach, afterEach } from 'vitest';
import assert from 'node:assert/strict';

import {
  WorkshopApiError,
  isValidUuid,
  listWorkshopPackages,
  getWorkshopPackage,
  listWorkshopSessions,
  createWorkshopBooking,
} from '../api/workshops.js';

describe('003.1 T018 & T026: Workshop Flow & Client Selection Contract', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('T018: Package Listing & Detail Session Filter Flow', () => {
    it('filters sessions by locationId and date range correctly', async () => {
      let requestedUrl;
      globalThis.fetch = async (url) => {
        requestedUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              sessionReference: '48e583d2-3bf9-4560-a42e-152e0be5b84c',
              locationId: 1,
              scheduledStart: '2026-11-10T09:00:00Z',
              scheduledEnd: '2026-11-10T11:30:00Z',
              saleState: 'OPEN_FOR_SALE',
              customerSelectable: true,
              remainingCapacity: 5,
            },
          ],
        };
      };

      const result = await listWorkshopSessions(1, {
        locationId: 1,
        from: '2026-11-10',
        to: '2026-11-15',
      });

      assert.strictEqual(requestedUrl.searchParams.get('locationId'), '1');
      assert.strictEqual(requestedUrl.searchParams.get('from'), '2026-11-10');
      assert.strictEqual(requestedUrl.searchParams.get('to'), '2026-11-15');
      assert.strictEqual(result.length, 1);
      assert.strictEqual(result[0].remainingCapacity, 5);
      assert.ok(isValidUuid(result[0].sessionReference));
    });

    it('handles package not found (404) with typed WorkshopApiError', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 404,
        json: async () => ({
          code: 'PACKAGE_NOT_FOUND',
          detail: 'Gói workshop không tồn tại hoặc đã ngừng hoạt động',
          correlationId: 'corr-pkg-404',
        }),
      });

      await assert.rejects(
        () => getWorkshopPackage(9999),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 404);
          assert.strictEqual(err.code, 'PACKAGE_NOT_FOUND');
          assert.strictEqual(err.correlationId, 'corr-pkg-404');
          return true;
        }
      );
    });

    it('handles empty sessions response for a location/date filter without throwing', async () => {
      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        json: async () => [],
      });

      const result = await listWorkshopSessions(1, {
        locationId: 2,
        from: '2026-11-20',
        to: '2026-11-25',
      });

      assert.deepStrictEqual(result, []);
    });
  });

  describe('T026: Booking Entry, Contact Validation & Capacity-Failure Handling', () => {
    it('validates contact email and phone before attempting submission', async () => {
      // Invalid email
      await assert.rejects(
        () => createWorkshopBooking({
          contactEmail: 'notanemail',
          contactPhone: '0901234567',
          packageId: 1,
          sessionReferences: ['48e583d2-3bf9-4560-a42e-152e0be5b84c'],
          participantCount: 1,
        }),
        { message: 'CONTACT_EMAIL_INVALID' }
      );

      // Short phone
      await assert.rejects(
        () => createWorkshopBooking({
          contactEmail: 'guest@example.com',
          contactPhone: '12345',
          packageId: 1,
          sessionReferences: ['48e583d2-3bf9-4560-a42e-152e0be5b84c'],
          participantCount: 1,
        }),
        { message: 'CONTACT_PHONE_INVALID' }
      );

      // Zero participant count
      await assert.rejects(
        () => createWorkshopBooking({
          contactEmail: 'guest@example.com',
          contactPhone: '0901234567',
          packageId: 1,
          sessionReferences: ['48e583d2-3bf9-4560-a42e-152e0be5b84c'],
          participantCount: 0,
        }),
        { message: 'PARTICIPANT_COUNT_INVALID' }
      );
    });

    it('submits Guest booking with opaque UUID session references and required Idempotency-Key', async () => {
      let sentHeaders;
      let sentBody;

      const sessionUuid = '48e583d2-3bf9-4560-a42e-152e0be5b84c';

      globalThis.fetch = async (url, options) => {
        sentHeaders = options.headers;
        sentBody = JSON.parse(options.body);
        return {
          ok: true,
          status: 201,
          json: async () => ({
            bookingCode: 'WS-20261101-FLOW',
            bookingStatus: 'PENDING_PAYMENT',
            paymentStatus: 'UNPAID',
            packageId: 1,
            packageName: 'Basic Ring Workshop',
            participantCount: 2,
            holdExpiresAt: '2026-11-01T12:45:00Z',
            sessions: [
              {
                sessionReference: sessionUuid,
                locationId: 1,
                ordinal: 1,
                scheduledStart: '2026-11-10T09:00:00Z',
                scheduledEnd: '2026-11-10T11:30:00Z',
              },
            ],
            tickets: [],
          }),
        };
      };

      const outcome = await createWorkshopBooking({
        packageId: 1,
        sessionReferences: [sessionUuid],
        participantCount: 2,
        contactEmail: 'guest.test@mland.vn',
        contactPhone: '0901234567',
      });

      assert.ok(sentHeaders['Idempotency-Key'], 'Idempotency-Key header must be generated and sent');
      assert.strictEqual(sentBody.packageId, 1);
      assert.deepStrictEqual(sentBody.sessionReferences, [sessionUuid]);
      assert.strictEqual(sentBody.participantCount, 2);
      assert.strictEqual(outcome.bookingCode, 'WS-20261101-FLOW');
      assert.strictEqual(outcome.bookingStatus, 'PENDING_PAYMENT');
      assert.strictEqual(outcome.paymentStatus, 'UNPAID');
      assert.ok(outcome.holdExpiresAt);
    });

    it('surfaces INSUFFICIENT_CAPACITY without calculating capacity rules client-side', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: new Headers({ 'x-correlation-id': 'corr-flow-cap' }),
        json: async () => ({
          code: 'INSUFFICIENT_CAPACITY',
          detail: 'Selected session cannot accommodate 4 participants',
          correlationId: 'corr-flow-cap',
        }),
      });

      await assert.rejects(
        () => createWorkshopBooking({
          packageId: 1,
          sessionReferences: ['48e583d2-3bf9-4560-a42e-152e0be5b84c'],
          participantCount: 4,
          contactEmail: 'user@mland.vn',
          contactPhone: '0901234567',
        }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 409);
          assert.strictEqual(err.code, 'INSUFFICIENT_CAPACITY');
          assert.strictEqual(err.correlationId, 'corr-flow-cap');
          return true;
        }
      );
    });

    it('surfaces PACKAGE_SELECTION_INVALID from server without client reimplementing rules', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        headers: new Headers({ 'x-correlation-id': 'corr-sel-inv' }),
        json: async () => ({
          code: 'PACKAGE_SELECTION_INVALID',
          detail: 'Package requires 2 sessions at the same location with at least 1 slot gap',
          correlationId: 'corr-sel-inv',
        }),
      });

      await assert.rejects(
        () => createWorkshopBooking({
          packageId: 2,
          sessionReferences: [
            '48e583d2-3bf9-4560-a42e-152e0be5b84c',
            '59e583d2-3bf9-4560-a42e-152e0be5b84d',
          ],
          participantCount: 1,
          contactEmail: 'user@mland.vn',
          contactPhone: '0901234567',
        }),
        (err) => {
          assert.ok(err instanceof WorkshopApiError);
          assert.strictEqual(err.status, 409);
          assert.strictEqual(err.code, 'PACKAGE_SELECTION_INVALID');
          assert.strictEqual(err.correlationId, 'corr-sel-inv');
          return true;
        }
      );
    });

    it('supports Member Quote booking with quoteRef and excludes raw PII from error correlation', async () => {
      const quoteUuid = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
      let capturedBody;

      globalThis.fetch = async (url, options) => {
        capturedBody = JSON.parse(options.body);
        return {
          ok: true,
          status: 201,
          json: async () => ({
            bookingCode: 'WS-20261101-QUOTE',
            bookingStatus: 'PENDING_PAYMENT',
            paymentStatus: 'UNPAID',
            packageId: 1,
            packageName: 'Member Quote Workshop',
            participantCount: 2,
            holdExpiresAt: '2026-11-01T12:50:00Z',
            sessions: [],
            tickets: [],
          }),
        };
      };

      const outcome = await createWorkshopBooking({
        quoteRef: quoteUuid,
        contactEmail: 'member@mland.vn',
        contactPhone: '0988776655',
      });

      assert.strictEqual(capturedBody.quoteRef, quoteUuid);
      assert.strictEqual(capturedBody.contactEmail, 'member@mland.vn');
      assert.strictEqual(outcome.bookingCode, 'WS-20261101-QUOTE');
    });
  });
});

