import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderRoutes, fakeSession, json, mockFetch } from '../../test/render';
import { otaConnectivityRoutes } from '../routes';

const open = (path, options = {}) => renderRoutes(otaConnectivityRoutes, { path, language: 'en', ...options });

describe('OTA Connectivity Pages UI & Integration Tests (T025)', () => {

  const sampleMappings = [
    {
      mappingId: 'f0000000-0000-0000-0000-000000000001',
      channelId: 'c0000000-0000-0000-0000-000000000001',
      provider: 'KLOOK',
      state: 'SALES_CLOSED',
      commercialStatus: 'APPROVED',
      bookingEnabled: false,
      safeReadinessStatus: 'INCOMPLETE',
      version: 1,
      salesControlStatus: 'NONE',
      providerProductReference: 'KLK-RING-01',
      providerOptionReference: 'KLK-OPT-01',
      workshopOfferReference: 'offer-basic-ring',
      sessionReference: 'sess-morning-01',
    },
    {
      mappingId: 'f0000000-0000-0000-0000-000000000002',
      channelId: 'c0000000-0000-0000-0000-000000000002',
      provider: 'GETYOURGUIDE',
      state: 'SYNC_SUSPENDED',
      commercialStatus: 'PENDING_COMMERCIAL_CONFIRMATION',
      bookingEnabled: false,
      safeReadinessStatus: 'REJECTED',
      version: 3,
      salesControlStatus: 'QUEUED',
      providerProductReference: 'GYG-RING-02',
      providerOptionReference: 'GYG-OPT-02',
      workshopOfferReference: 'offer-silverclay',
      sessionReference: 'sess-afternoon-02',
    },
  ];

  const sampleWorkItems = [
    {
      workItemId: 'w0000000-0000-0000-0000-000000000001',
      mappingId: 'f0000000-0000-0000-0000-000000000002',
      status: 'OPEN',
      correlationId: 'corr-rec-001',
      safeSummary: 'SYNC_SUSPENDED: Source version gap detected; authoritative resync required',
    },
    {
      workItemId: 'w0000000-0000-0000-0000-000000000002',
      mappingId: 'f0000000-0000-0000-0000-000000000001',
      status: 'RESOLVED',
      correlationId: 'corr-rec-002',
      safeSummary: 'SALES_CLOSED: Emergency sales close accepted',
    },
  ];

  describe('OtaChannelConfigurationPage', () => {

    it('renders channel mappings and never displays credentials, secrets, or provider tokens', async () => {
      mockFetch({
        'GET /api/v1/ota-channels': json(200, { items: sampleMappings }),
      });

      open('/ota-connectivity/channels');

      expect(await screen.findByText('OTA Channel Configuration')).toBeInTheDocument();
      expect(screen.getByText('KLOOK')).toBeInTheDocument();
      expect(screen.getByText('GETYOURGUIDE')).toBeInTheDocument();

      // Check badges
      expect(screen.getByText('SALES_CLOSED')).toBeInTheDocument();
      expect(screen.getByText('SYNC_SUSPENDED')).toBeInTheDocument();
      expect(screen.getByText('APPROVED')).toBeInTheDocument();
      expect(screen.getByText('PENDING_COMMERCIAL_CONFIRMATION')).toBeInTheDocument();

      // Check workshop references
      expect(screen.getByText(/KLK-RING-01/)).toBeInTheDocument();
      expect(screen.getByText(/offer-basic-ring/)).toBeInTheDocument();

      // Privacy check: Verify no secret/token/credential leaks in DOM
      const domText = document.body.textContent;
      expect(domText).not.toMatch(/secret/i);
      expect(domText).not.toMatch(/password/i);
      expect(domText).not.toMatch(/X-Provider-Auth-Ref/i);
      expect(domText).not.toMatch(/api_key/i);
    });

    it('displays empty state when no channel mappings exist', async () => {
      mockFetch({
        'GET /api/v1/ota-channels': json(200, { items: [] }),
      });

      open('/ota-connectivity/channels');

      expect(await screen.findByText('No OTA channel mappings configured.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Create First Mapping' })).toBeInTheDocument();
    });

    it('displays authentication required prompt on 401 Unauthorized', async () => {
      open('/ota-connectivity/channels', {
        session: fakeSession('signed-out'),
      });

      expect(await screen.findByText(/Authentication required/i)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Sign In' })).toBeInTheDocument();
    });

    it('surfaces 403 Forbidden error notice when user lacks Manager/Admin authority', async () => {
      mockFetch({
        'GET /api/v1/ota-channels': json(403, {
          code: 'FORBIDDEN',
          message: 'Caller lacks global Manager or Admin access for OTA operations',
        }),
      });

      open('/ota-connectivity/channels');

      expect(await screen.findByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/You are not allowed to do this|Caller lacks global Manager or Admin access/i)).toBeInTheDocument();
    });

    it('surfaces 409 Conflict stale-version error notice when update conflicts', async () => {
      const calls = mockFetch({
        'GET /api/v1/ota-channels': json(200, { items: sampleMappings }),
        'PUT /api/v1/ota-channel-mappings/f0000000-0000-0000-0000-000000000001': json(409, {
          code: 'STALE_VERSION',
          message: 'Mapping was modified by another transaction; version mismatch',
        }),
      });

      const user = userEvent.setup();
      open('/ota-connectivity/channels');

      await screen.findByText('KLOOK');

      // Click Edit Mapping on first card
      const editButtons = screen.getAllByRole('button', { name: 'Edit Mapping' });
      await user.click(editButtons[0]);

      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      const saveButton = screen.getByRole('button', { name: 'Update Mapping' });
      await user.click(saveButton);

      expect(await screen.findByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/data changed|version mismatch|stale/i)).toBeInTheDocument();
    });

    it('opens price terms modal, validates inputs, and records confirmed terms', async () => {
      let recordedBody;
      mockFetch({
        'GET /api/v1/ota-channels': json(200, { items: sampleMappings }),
        'POST /api/v1/ota-channel-mappings/f0000000-0000-0000-0000-000000000001/price-terms': (call) => {
          recordedBody = call.body;
          return json(201, { priceTermsId: 'pt-101', status: 'APPROVED' });
        },
      });

      const user = userEvent.setup();
      open('/ota-connectivity/channels');

      await screen.findByText('KLOOK');

      const priceButtons = screen.getAllByRole('button', { name: 'Price Terms' });
      await user.click(priceButtons[0]);

      expect(await screen.findByRole('dialog', { name: 'Record Confirmed Price Terms' })).toBeInTheDocument();

      // Submit empty to trigger validation
      const submitButton = screen.getByRole('button', { name: 'Record Terms' });
      await user.click(submitButton);

      expect(await screen.findByText(/Retail amount must be a positive number/i)).toBeInTheDocument();

      // Fill in valid price terms
      await user.type(screen.getByPlaceholderText('e.g. 500000'), '450000');
      await user.type(screen.getByPlaceholderText('e.g. 400000'), '360000');
      await user.type(screen.getByPlaceholderText('e.g. CONF-KLOOK-PRICE-20261101'), 'CONF-KLOOK-COMMERCIAL-20261101');
      await user.type(screen.getByPlaceholderText('Record why these price terms are approved...'), 'Approved holiday pricing terms');

      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(recordedBody.retailAmount.amount).toBe('450000');
      expect(recordedBody.targetNetAmount.amount).toBe('360000');
      expect(recordedBody.providerConfirmationEvidenceReference).toBe('CONF-KLOOK-COMMERCIAL-20261101');
    });

    it('Admin booking enable modal renders all 12 readiness checklist gates', async () => {
      let enableBody;
      mockFetch({
        'GET /api/v1/ota-channels': json(200, { items: sampleMappings }),
        'POST /api/v1/ota-channel-mappings/f0000000-0000-0000-0000-000000000001/booking-enable': (call) => {
          enableBody = call.body;
          return json(200, { decisionId: 'dec-1', outcome: 'APPROVED' });
        },
      });

      const user = userEvent.setup();
      open('/ota-connectivity/channels');

      await screen.findByText('KLOOK');

      // Admin Enable is visible on SALES_CLOSED mapping
      const enableButton = screen.getByRole('button', { name: 'Admin Enable' });
      await user.click(enableButton);

      expect(await screen.findByRole('dialog', { name: 'Admin Booking Enablement' })).toBeInTheDocument();

      // Verify all 12 gates are present in the dialog
      expect(screen.getByText('CONTRACT_ONBOARDING')).toBeInTheDocument();
      expect(screen.getByText('COMMERCIAL_TERMS')).toBeInTheDocument();
      expect(screen.getByText('CONNECTION_OWNERSHIP')).toBeInTheDocument();
      expect(screen.getByText('CALLBACK_AUTHENTICATION')).toBeInTheDocument();
      expect(screen.getByText('QUOTA_DELIVERY')).toBeInTheDocument();
      expect(screen.getByText('PRODUCT_SESSION_MAPPING')).toBeInTheDocument();
      expect(screen.getByText('DATA_ALLOWLIST')).toBeInTheDocument();
      expect(screen.getByText('PREPARATION_SYNC')).toBeInTheDocument();
      expect(screen.getByText('TICKET_DELIVERY_SCAN')).toBeInTheDocument();
      expect(screen.getByText('CANCELLATION_REFUND')).toBeInTheDocument();
      expect(screen.getByText('LATE_BOOKING_CAPACITY')).toBeInTheDocument();
      expect(screen.getByText('FAILURE_RECONCILIATION')).toBeInTheDocument();

      // Fill required fields
      await user.type(screen.getByPlaceholderText('e.g. DOC-KLOOK-READINESS-REC-20261101'), 'DOC-KLOOK-AUDIT-20261101');
      await user.type(screen.getByPlaceholderText('e.g. Reviewed and verified all 12 operational readiness checks'), 'All verified by Admin');

      const submitEnable = screen.getByRole('button', { name: 'Enable Booking' });
      await user.click(submitEnable);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(enableBody.readinessRecordReference).toBe('DOC-KLOOK-AUDIT-20261101');
      expect(enableBody.checks).toHaveLength(12);
    });
  });

  describe('OtaReconciliationPage', () => {

    it('renders reconciliation work items and filters by status', async () => {
      const calls = mockFetch({
        'GET /api/v1/ota-reconciliation-work-items': (call) => {
          if (call.query.status === 'OPEN') {
            return json(200, { items: [sampleWorkItems[0]] });
          }
          return json(200, { items: sampleWorkItems });
        },
      });

      const user = userEvent.setup();
      open('/ota-connectivity/reconciliation');

      expect(await screen.findByText('OTA Reconciliation Queue')).toBeInTheDocument();
      expect(screen.getByText(/Item: w0000000-0000-0000-0000-000000000001/)).toBeInTheDocument();
      expect(screen.getByText(/Item: w0000000-0000-0000-0000-000000000002/)).toBeInTheDocument();

      // Check suspended badge
      expect(screen.getByText('MAPPING SUSPENDED')).toBeInTheDocument();

      // Filter by OPEN
      const openFilter = screen.getByRole('button', { name: 'OPEN' });
      await user.click(openFilter);

      await waitFor(() => {
        expect(calls.some(c => c.query.status === 'OPEN')).toBe(true);
      });
      expect(screen.getByText(/Item: w0000000-0000-0000-0000-000000000001/)).toBeInTheDocument();
      expect(screen.queryByText(/Item: w0000000-0000-0000-0000-000000000002/)).not.toBeInTheDocument();
    });

    it('opens resolve modal and submits resolution with Idempotency-Key', async () => {
      let capturedCall;
      mockFetch({
        'GET /api/v1/ota-reconciliation-work-items': json(200, { items: [sampleWorkItems[0]] }),
        'POST /api/v1/ota-reconciliation-work-items/w0000000-0000-0000-0000-000000000001/resolve': (call) => {
          capturedCall = call;
          return json(200, {
            workItemId: 'w0000000-0000-0000-0000-000000000001',
            status: 'IN_PROGRESS',
            correlationId: 'corr-res-001',
          });
        },
      });

      const user = userEvent.setup();
      open('/ota-connectivity/reconciliation');

      await screen.findByText(/Item: w0000000-0000-0000-0000-000000000001/);

      const resolveBtn = screen.getByRole('button', { name: 'Resolve Item' });
      await user.click(resolveBtn);

      expect(await screen.findByRole('dialog', { name: 'Resolve Reconciliation Work Item' })).toBeInTheDocument();

      const reasonInput = screen.getByPlaceholderText('e.g. Requested authoritative resync to reconcile gap version...');
      await user.type(reasonInput, 'Authoritative resync requested by Manager to restore continuous sequence');

      const submitBtn = screen.getByRole('button', { name: 'Record Decision' });
      await user.click(submitBtn);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });

      expect(capturedCall.headers['Idempotency-Key']).toBeDefined();
      expect(capturedCall.headers['Idempotency-Key'].length).toBeGreaterThanOrEqual(8);
      expect(capturedCall.body.resolution).toBe('RESYNC_REQUESTED');
      expect(capturedCall.body.reason).toBe('Authoritative resync requested by Manager to restore continuous sequence');
    });

    it('displays network error with retry button on network failure', async () => {
      let attempts = 0;
      mockFetch({
        'GET /api/v1/ota-reconciliation-work-items': () => {
          attempts++;
          if (attempts === 1) {
            return json(503, { code: 'SERVICE_UNAVAILABLE', message: 'Backend database connection timeout' });
          }
          return json(200, { items: [] });
        },
      });

      const user = userEvent.setup();
      open('/ota-connectivity/reconciliation');

      expect(await screen.findByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/The system could not process this right now|Backend database connection timeout/i)).toBeInTheDocument();

      // Click retry
      const retryBtn = screen.getByRole('button', { name: /Try again/i });
      await user.click(retryBtn);

      expect(await screen.findByText(/No reconciliation work items found/i)).toBeInTheDocument();
      expect(attempts).toBe(2);
    });
  });
});
