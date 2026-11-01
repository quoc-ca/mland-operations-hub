import { describe, expect, it, vi } from 'vitest';
import { catalogueApi } from '../api/catalogueApi';

describe('002 REST operations', () => {
  const request = vi.fn(async () => ({}));
  const api = catalogueApi(request);
  const last = () => request.mock.calls.at(-1);

  it('public reads are anonymous', async () => {
    await api.listPublicProducts({ q: 'x', sort: 'newest' });
    expect(last()).toEqual(['/api/v1/products', { query: { q: 'x', sort: 'newest' }, auth: false }]);
  });

  it('writes carry the key and expectedVersion in the body', async () => {
    await api.updateProduct('k1', '42', { expectedVersion: 3, name: 'A', description: 'B', categoryId: '7' });
    expect(last()).toEqual(['/api/v1/catalogue-management/products/42', { method: 'PUT', idempotencyKey: 'k1', body: { expectedVersion: 3, name: 'A', description: 'B', categoryId: '7' }, query: undefined }]);
  });

  it('DELETE sends expectedVersion as a query parameter', async () => {
    await api.deleteProduct('k2', '42', 3);
    expect(last()).toEqual(['/api/v1/catalogue-management/products/42', { method: 'DELETE', idempotencyKey: 'k2', body: undefined, query: { expectedVersion: 3 } }]);
  });

  it('applying a price sends no amount', async () => {
    await api.applyPrice('k3', '501', 2);
    expect(last()[1].body).toEqual({ expectedVersion: 2 });
  });

  it('quantity and amounts stay decimal strings', async () => {
    await api.setQuantity('k4', '101', 4, '7');
    expect(last()[1].body).toEqual({ expectedVersion: 4, quantity: '7' });
    await api.proposePrice('k5', '101', 4, '1250000');
    expect(last()[1].body).toEqual({ expectedVersion: 4, proposedPrice: '1250000' });
  });

  it('uploads one file per request as multipart', async () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    await api.uploadMedia('k6', file);
    const [path, options] = last();
    expect(path).toBe('/api/v1/catalogue-management/media-uploads');
    expect(options.form.getAll('file')).toHaveLength(1);
    expect(options.idempotencyKey).toBe('k6');
  });
});
