import { afterEach, describe, expect, it, vi } from 'vitest';
import { createRetailClient } from './retail';
import { listWorkshopPackages, WorkshopApiError } from './workshops';

afterEach(() => vi.unstubAllGlobals());

describe('API layer carries codes, not language-bound text', () => {
  it('a lost workshop response has code NETWORK_ERROR and no fixed-language text', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const error = await listWorkshopPackages().catch(failure => failure);
    expect(error).toBeInstanceOf(WorkshopApiError);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', message: '', detail: '' });
  });

  it('retail errors without a session or server text have an empty message so the screen maps the code', async () => {
    const noToken = await createRetailClient(() => null).getCart().catch(failure => failure);
    expect(noToken).toMatchObject({ code: 'AUTHENTICATION_REQUIRED', message: '' });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500, json: async () => ({}) }));
    const failed = await createRetailClient(() => 'token').getCart().catch(failure => failure);
    expect(failed).toMatchObject({ code: 'HTTP_500', message: '' });
  });
});
