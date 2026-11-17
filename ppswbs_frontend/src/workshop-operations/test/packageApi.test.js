import { describe, expect, it, vi } from 'vitest';
import { createWorkshopPackageApi } from '../api/packageApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import { createWorkshopAddOnApi } from '../api/addOnApi';

describe('Workshop package management API client', () => {
  it('lists published packages using the public package endpoint', async () => {
    const http = vi.fn().mockResolvedValue({ items: [{ packageId: 17, state: 'PUBLISHED' }] });
    const api = createWorkshopPackageApi(http);

    await api.listPublished();

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-packages');
  });

  it('lists all package lifecycle states through the manager endpoint', async () => {
    const http = vi.fn().mockResolvedValue([
      { packageId: 17, state: 'PUBLISHED' },
      { packageId: 18, state: 'DRAFT' },
      { packageId: 19, state: 'RETIRED' },
    ]);
    const api = createWorkshopPackageApi(http);

    await api.listManaged();

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-package-management/packages');
  });

  it('filters managed packages by lifecycle state when requested', async () => {
    const http = vi.fn().mockResolvedValue([]);
    const api = createWorkshopPackageApi(http);

    await api.listManaged('DRAFT');

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-package-management/packages?state=DRAFT');
  });

  it('creates a draft with an idempotency key', async () => {
    const http = vi.fn().mockResolvedValue({ packageId: 18, state: 'DRAFT' });
    const api = createWorkshopPackageApi(http);
    const body = { name: 'Signature', publicPrice: 900000 };

    await api.createDraft(body, 'create-package-18');

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-packages', {
      method: 'POST',
      body,
      idempotencyKey: 'create-package-18',
    });
  });

  it('updates a package prospectively with PATCH', async () => {
    const http = vi.fn().mockResolvedValue({ packageId: 17, state: 'PUBLISHED' });
    const api = createWorkshopPackageApi(http);
    const body = { name: 'Updated signature', publicPrice: 950000 };

    await api.update(17, body, 'update-package-17');

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-packages/17', {
      method: 'PATCH',
      body,
      idempotencyKey: 'update-package-17',
    });
  });

  it('transitions a package lifecycle state', async () => {
    const http = vi.fn().mockResolvedValue({ packageId: 17, state: 'RETIRED' });
    const api = createWorkshopPackageApi(http);

    await api.transition(17, 'RETIRED', 'retire-package-17');

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-packages/17/lifecycle-transitions', {
      method: 'POST',
      body: { targetState: 'RETIRED' },
      idempotencyKey: 'retire-package-17',
    });
  });

  it('uses scoped administration endpoints for locations, slots and sessions', async () => {
    const http = vi.fn().mockResolvedValue([]);
    const api = createOperationsAdminApi(http);

    await api.listLocations();
    await api.listSlots(17);
    await api.listSessions({ state: 'DRAFT' });
    await api.createSession({ locationId: 17, slotId: 3, workshopDate: '2026-11-03' }, 'session-create-1');

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-locations');
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-locations/17/slots');
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-sessions', { query: { state: 'DRAFT' } });
    expect(http).toHaveBeenNthCalledWith(4, '/api/v1/workshop-sessions', {
      method: 'POST',
      body: { locationId: 17, slotId: 3, workshopDate: '2026-11-03' },
      idempotencyKey: 'session-create-1',
    });
  });

  it('supports slot activation and session lifecycle transitions through the admin contract', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createOperationsAdminApi(http);

    await api.updateSlot('slot-1', {
      label: 'Morning',
      startsAtLocal: '09:00',
      endsAtLocal: '12:00',
      defaultCapacity: 10,
      active: false,
    }, 'slot-update-1');
    await api.transitionSession('session-1', 'PUBLISHED', 'session-publish-1');

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-slots/slot-1', {
      method: 'PATCH',
      body: {
        label: 'Morning',
        startsAtLocal: '09:00',
        endsAtLocal: '12:00',
        defaultCapacity: 10,
        active: false,
      },
      idempotencyKey: 'slot-update-1',
    });
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-sessions/session-1/state-transitions', {
      method: 'POST',
      body: { targetState: 'PUBLISHED' },
      idempotencyKey: 'session-publish-1',
    });
  });

  it('uses the Manager inventory endpoint and carries operation keys for package/location writes', async () => {
    const http = vi.fn().mockResolvedValue([]);
    const api = createWorkshopAddOnApi(http);
    const body = { description: 'Extra silver', priceVnd: 900000 };

    await api.listManaged(17, 4);
    await api.create(17, 4, body, 'addon-create-1');
    await api.transition(17, 4, 'ADD-1', 'ACTIVE', 'addon-active-1');

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-package-management/packages/17/locations/4/add-ons');
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-packages/17/locations/4/add-ons', {
      method: 'POST', body, idempotencyKey: 'addon-create-1',
    });
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-packages/17/locations/4/add-ons/ADD-1/lifecycle-transitions', {
      method: 'POST', body: { targetState: 'ACTIVE' }, idempotencyKey: 'addon-active-1',
    });
  });
});
