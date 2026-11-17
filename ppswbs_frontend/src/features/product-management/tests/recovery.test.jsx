import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { actionResult, categoryPage, managementProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const MEDIA = '11111111-1111-1111-1111-111111111111';
const open = path => renderRoutes(productManagementRoutes, { path });
const lost = () => { throw new TypeError('Failed to fetch'); };
const receipt = (commandId, action, resourceId) => json(200, { commandId, action, result: actionResult(resourceId, action), committedAt: '2026-11-03T00:00:00Z' });

afterEach(() => { sessionStorage.clear(); });

describe('FR-003 management list', () => {
  it('shows the product number and marks the size whose SKU was searched', async () => {
    const product = managementProduct({ variants: [
      { ...managementProduct().variants[0] },
      { ...managementProduct().variants[0], id: '102', size: 14, sku: 'SP000042-S14' },
    ] });
    mockFetch({
      [`GET ${M}/products`]: json(200, { items: [product], page: 0, pageSize: 20, totalElements: '1' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?q=%20sp000042-s14%20');
    expect(await screen.findByText('SP000042')).toBeInTheDocument();
    expect(screen.getByText('14', { selector: 'mark' })).toBeInTheDocument();
    expect(screen.getByText('Khớp SKU SP000042-S14 (size 14)')).toBeInTheDocument();
  });

  it('marks no size when the keyword is not a SKU', async () => {
    mockFetch({
      [`GET ${M}/products`]: json(200, { items: [managementProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    const { container } = open('/catalogue-management/products?q=b%E1%BA%A1c');
    await screen.findByRole('link', { name: 'Nhẫn bạc' });
    expect(container.querySelector('mark')).toBeNull();
  });
});

describe('staff image preview', () => {
  it('fetches an existing image with the bearer and shows it from a blob URL', async () => {
    const createObjectURL = vi.fn(() => 'blob:preview-1');
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/media/${MEDIA}`]: new Response(new Blob(['png'], { type: 'image/png' }), { status: 200, headers: { 'Content-Type': 'image/png' } }),
    });
    open('/catalogue-management/products/42');
    const image = await screen.findByRole('img', { name: 'Mặt trước' });
    expect(image).toHaveAttribute('src', 'blob:preview-1');
    expect(calls.find(call => call.path === `${M}/media/${MEDIA}`).headers.Authorization).toBe('Bearer test-token');
  });

  it('keeps the numbered placeholder when the image cannot be read', async () => {
    mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/media/${MEDIA}`]: json(404, { code: 'MEDIA_NOT_FOUND', message: 'Not found.' }),
    });
    open('/catalogue-management/products/42');
    expect(await screen.findByText('#1')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Mặt trước' })).toBeNull();
  });
});

describe('FR-028 lost answers', () => {
  it('asks for the receipt before resending and does not send the write twice when it was saved', async () => {
    const user = userEvent.setup();
    let creates = 0;
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      [`POST ${M}/categories`]: () => { creates += 1; return lost(); },
    });
    open('/catalogue-management/categories');
    await user.type(await screen.findByLabelText('Tên danh mục'), 'Nhẫn đôi');
    await user.click(screen.getByRole('button', { name: 'Tạo danh mục' }));
    expect(await screen.findByText(/Không nhận được phản hồi/)).toBeInTheDocument();

    const key = calls.find(call => call.method === 'POST').headers['Idempotency-Key'];
    mockFetchMore(calls, { [`GET ${M}/command-receipts/${key}`]: receipt(key, 'category.create', '8') });
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findAllByText(/^Đã tạo danh mục "/)).toHaveLength(2); // page top and under the form
    expect(creates).toBe(1);
    expect(calls.find(call => call.path === `${M}/command-receipts/${key}`).query).toEqual({ action: 'category.create' });
  });

  it('resends the same key when there is no receipt', async () => {
    const user = userEvent.setup();
    const keys = [];
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      [`POST ${M}/categories`]: call => { keys.push(call.headers['Idempotency-Key']); return keys.length === 1 ? lost() : json(201, actionResult('8', 'category.create')); },
    });
    open('/catalogue-management/categories');
    await user.type(await screen.findByLabelText('Tên danh mục'), 'Nhẫn đôi');
    await user.click(screen.getByRole('button', { name: 'Tạo danh mục' }));
    await screen.findByText(/Không nhận được phản hồi/);
    mockFetchMore(calls, { [`GET ${M}/command-receipts/${keys[0]}`]: json(404, { code: 'COMMAND_RESULT_NOT_FOUND', message: 'No receipt.' }) });
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findAllByText(/^Đã tạo danh mục "/)).toHaveLength(2); // page top and under the form
    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  it('after a reload, links the product a lost create has saved', async () => {
    sessionStorage.setItem('pm.pendingCreate', JSON.stringify({ key: 'k-1' }));
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/command-receipts/k-1`]: receipt('k-1', 'product.create', '77'),
    });
    open('/catalogue-management/products/new');
    expect(await screen.findByRole('link', { name: 'Mở sản phẩm đó' })).toHaveAttribute('href', '/catalogue-management/products/77');
    expect(calls.find(call => call.path === `${M}/command-receipts/k-1`).query).toEqual({ action: 'product.create' });
    await waitFor(() => expect(sessionStorage.getItem('pm.pendingCreate')).toBeNull());
  });

  it('forgets a pending create that has no receipt', async () => {
    sessionStorage.setItem('pm.pendingCreate', JSON.stringify({ key: 'k-2' }));
    mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/command-receipts/k-2`]: json(404, { code: 'COMMAND_RESULT_NOT_FOUND', message: 'No receipt.' }),
    });
    open('/catalogue-management/products/new');
    await waitFor(() => expect(sessionStorage.getItem('pm.pendingCreate')).toBeNull());
    expect(screen.queryByRole('link', { name: 'Mở sản phẩm đó' })).toBeNull();
  });
});

/** Adds handlers to the fetch mock already installed (the key is known only after the first call). */
function mockFetchMore(calls, more) {
  const previous = globalThis.fetch;
  vi.stubGlobal('fetch', vi.fn(async (url, init = {}) => {
    const parsed = new URL(url);
    const handler = more[`${init.method || 'GET'} ${parsed.pathname}`];
    if (!handler) return previous(url, init);
    calls.push({ method: init.method || 'GET', path: parsed.pathname, query: Object.fromEntries(parsed.searchParams), headers: init.headers || {} });
    return handler.clone();
  }));
}
