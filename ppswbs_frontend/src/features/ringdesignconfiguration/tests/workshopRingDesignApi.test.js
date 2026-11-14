import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  workshopRingDesignApi,
  GUEST_SESSION_KEY,
  getStoredGuestSession,
  setStoredGuestSession,
  clearStoredGuestSession,
} from '../api/workshopRingDesignApi';

describe('workshopRingDesignApi & Session Storage', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('manages guest design session in sessionStorage correctly', () => {
    expect(getStoredGuestSession()).toBeNull();

    setStoredGuestSession('guest-token-123');
    expect(sessionStorage.getItem(GUEST_SESSION_KEY)).toBe('guest-token-123');
    expect(getStoredGuestSession()).toBe('guest-token-123');

    clearStoredGuestSession();
    expect(getStoredGuestSession()).toBeNull();
    expect(sessionStorage.getItem(GUEST_SESSION_KEY)).toBeNull();
  });

  it('setStoredGuestSession with null clears the stored session', () => {
    setStoredGuestSession('token-abc');
    expect(getStoredGuestSession()).toBe('token-abc');

    setStoredGuestSession(null);
    expect(getStoredGuestSession()).toBeNull();
  });

  describe('API Endpoints', () => {
    const request = vi.fn(async () => ({}));
    const api = workshopRingDesignApi(request);
    const last = () => request.mock.calls.at(-1);

    beforeEach(() => {
      request.mockClear();
      sessionStorage.clear();
    });

    it('getDesignOptions requests options with packageReference and optional branchReference', async () => {
      await api.getDesignOptions('PKG-BASIC', 'BR-HCM-01');
      expect(last()).toEqual([
        '/api/v1/workshop-design-options',
        {
          method: 'GET',
          query: { packageReference: 'PKG-BASIC', branchReference: 'BR-HCM-01' },
          auth: false,
        },
      ]);
    });

    it('createDesign includes Idempotency-Key and stored guest session header', async () => {
      setStoredGuestSession('stored-session-token');

      const body = {
        packageReference: 'PKG-BASIC',
        startMode: 'BLANK',
      };

      await api.createDesign('idem-key-001', body, null, false);

      expect(last()).toEqual([
        '/api/v1/workshop-designs',
        {
          method: 'POST',
          body,
          idempotencyKey: 'idem-key-001',
          headers: {
            'X-Guest-Design-Session': 'stored-session-token',
          },
          auth: false,
        },
      ]);
    });

    it('createDesign uses explicit guestSession parameter over stored session if provided', async () => {
      setStoredGuestSession('stored-token');

      await api.createDesign('idem-key-002', { packageReference: 'PKG-002' }, 'explicit-session-token', true);

      expect(last()).toEqual([
        '/api/v1/workshop-designs',
        {
          method: 'POST',
          body: { packageReference: 'PKG-002' },
          idempotencyKey: 'idem-key-002',
          headers: {
            'X-Guest-Design-Session': 'explicit-session-token',
          },
          auth: true,
        },
      ]);
    });

    it('getDesign fetches design by ID with headers', async () => {
      setStoredGuestSession('session-xyz');

      await api.getDesign('design-uuid-999');

      expect(last()).toEqual([
        '/api/v1/workshop-designs/design-uuid-999',
        {
          method: 'GET',
          headers: {
            'X-Guest-Design-Session': 'session-xyz',
          },
          auth: false,
        },
      ]);
    });

    it('saveVersion sends POST with If-Match-Version and Idempotency-Key', async () => {
      const payload = {
        materialReference: 'MAT-SILVER-925',
        shapeReference: 'SHP-ROUND',
        ringSize: 14,
      };

      await api.saveVersion('save-key-1', 'design-123', payload, 2, 'session-456', false);

      expect(last()).toEqual([
        '/api/v1/workshop-designs/design-123/versions',
        {
          method: 'POST',
          body: payload,
          idempotencyKey: 'save-key-1',
          headers: {
            'X-Guest-Design-Session': 'session-456',
            'If-Match-Version': '2',
          },
          auth: false,
        },
      ]);
    });

    it('evaluateDesign calls evaluate endpoint with version headers', async () => {
      const payload = {
        materialReference: 'MAT-GOLD-10K',
        shapeReference: 'SHP-ROUND',
      };

      await api.evaluateDesign('eval-key-1', 'design-123', payload, 3, 'guest-token-1', false);

      expect(last()).toEqual([
        '/api/v1/workshop-designs/design-123/evaluate',
        {
          method: 'POST',
          body: payload,
          idempotencyKey: 'eval-key-1',
          headers: {
            'X-Guest-Design-Session': 'guest-token-1',
            'If-Match-Version': '3',
          },
          auth: false,
        },
      ]);
    });

    it('archiveDesign calls archive endpoint with version headers', async () => {
      await api.archiveDesign('archive-key-1', 'design-123', 4, 'guest-token-1', false);

      expect(last()).toEqual([
        '/api/v1/workshop-designs/design-123/archive',
        {
          method: 'POST',
          idempotencyKey: 'archive-key-1',
          headers: {
            'X-Guest-Design-Session': 'guest-token-1',
            'If-Match-Version': '4',
          },
          auth: false,
        },
      ]);
    });

    it('attachToBooking calls attach-to-booking endpoint with booking body', async () => {
      const body = { bookingReference: 'WB-2026-0012' };

      await api.attachToBooking('attach-key-1', 'design-123', body, 5, 'guest-token-1', false);

      expect(last()).toEqual([
        '/api/v1/workshop-designs/design-123/attach-to-booking',
        {
          method: 'POST',
          body,
          idempotencyKey: 'attach-key-1',
          headers: {
            'X-Guest-Design-Session': 'guest-token-1',
            'If-Match-Version': '5',
          },
          auth: false,
        },
      ]);
    });
  });
});

