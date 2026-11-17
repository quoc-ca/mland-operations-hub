import { describe, expect, it, vi } from 'vitest';
import { createPackageAvailabilityApi } from '../api/packageAvailabilityApi';

describe('Package availability API client', () => {
  it('reads the matrix with GET on the exact path', async () => {
    const http = vi.fn().mockResolvedValue({ packages: [], locations: [], cells: [] });
    await createPackageAvailabilityApi(http).readMatrix();
    expect(http).toHaveBeenCalledWith('/api/v1/workshop-package-availability');
  });

  it('sets a cell with PUT, body {state, version} and the supplied key', async () => {
    const http = vi.fn().mockResolvedValue({});
    await createPackageAvailabilityApi(http).setAvailability(17, 4, 'ACTIVE', 0, 'avail-1');
    expect(http).toHaveBeenCalledWith('/api/v1/workshop-packages/17/locations/4/availability', {
      method: 'PUT',
      body: { state: 'ACTIVE', version: 0 },
      idempotencyKey: 'avail-1',
    });
  });

  it('generates an idempotency key when none is supplied, new on each call', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createPackageAvailabilityApi(http);
    await api.setAvailability(17, 4, 'INACTIVE', 3);
    await api.setAvailability(17, 4, 'INACTIVE', 3);
    const k1 = http.mock.calls[0][1].idempotencyKey;
    const k2 = http.mock.calls[1][1].idempotencyKey;
    expect(typeof k1).toBe('string');
    expect(k1.length).toBeGreaterThan(0);
    expect(k1).not.toBe(k2);
    expect(http.mock.calls[0][1].body).toEqual({ state: 'INACTIVE', version: 3 });
  });

  it('URL-encodes package and location ids', async () => {
    const http = vi.fn().mockResolvedValue({});
    await createPackageAvailabilityApi(http).setAvailability('a/b', 'c d', 'ACTIVE', 1, 'k');
    expect(http.mock.calls[0][0]).toBe('/api/v1/workshop-packages/a%2Fb/locations/c%20d/availability');
  });
});
