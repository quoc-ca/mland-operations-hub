import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { invalidPriceFields } from '../ui/public/PublicProductListPage';
import { categoryPage, managementProduct, publicProduct } from './fixtures';
import { beforeEach as beforeEachSignIn } from 'vitest';
import { signInAs } from './fixtures';

// RouteAccessGuard (T219) reads the 001 Account bootstrap: an active Staff unless a test says otherwise.
beforeEachSignIn(() => { signInAs('STAFF'); });

/** speckit-converge Phase 23 (2026-11-20): T184, T185, T186, T188. */
const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const LOCATIONS = 'GET /api/v1/workshop-locations';
const open = (path, options) => renderRoutes(productManagementRoutes, { path, ...options });
const LIST = { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' };

beforeEach(() => {
  clearRoleCache();
  vi.stubGlobal('scrollTo', vi.fn());
});
afterEach(() => vi.unstubAllGlobals());

describe('T184 price filter on the public list (US8 scenario 8, FR-032)', () => {
  it('knows which price boxes are wrong', () => {
    expect(invalidPriceFields('', '')).toEqual([]);
    expect(invalidPriceFields('100000', '900000')).toEqual([]);
    expect(invalidPriceFields('100000', '100000')).toEqual([]);
    expect(invalidPriceFields('-5', '')).toEqual(['minimumPrice']);
    expect(invalidPriceFields('', '1,5')).toEqual(['maximumPrice']);
    expect(invalidPriceFields('900000', '100000')).toEqual(['minimumPrice', 'maximumPrice']);
  });

  it('a minimum above the maximum marks both boxes and does not change the URL', async () => {
    const user = userEvent.setup();
    mockFetch({ 'GET /api/v1/products': json(200, LIST), 'GET /api/v1/product-categories': json(200, categoryPage) });
    const { router } = open('/products');
    await screen.findByText('1 sản phẩm');
    await user.type(screen.getByLabelText('Giá từ (₫)'), '900000');
    await user.type(screen.getByLabelText('Giá đến (₫)'), '100000');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Giá từ không được lớn hơn Giá đến');
    expect(screen.getByLabelText('Giá từ (₫)')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Giá đến (₫)')).toHaveAttribute('aria-invalid', 'true');
    expect(router.state.location.search).toBe('');
  });

  it('a wrong filter in a shared link is marked from the backend field errors', async () => {
    mockFetch({
      'GET /api/v1/products': json(400, { code: 'VALIDATION_ERROR', message: 'Invalid.', fieldErrors: [{ field: 'minimumPrice', message: 'bad' }] }),
      'GET /api/v1/product-categories': json(200, categoryPage),
    });
    open('/products?minimumPrice=900000&maximumPrice=100000');
    expect(await screen.findByText(/Giá là số nguyên từ 0 trở lên/)).toHaveAttribute('id', 'price-filter-error');
    expect(screen.getByLabelText('Giá từ (₫)')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Giá đến (₫)')).not.toHaveAttribute('aria-invalid');
  });
});

describe('T185 branch filter on the management list (FR-038, FR-051)', () => {
  it('says the branches could not be read and reads them again on retry', async () => {
    const user = userEvent.setup();
    const HK = { id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true };
    let branchReads = 0;
    mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      'GET /api/v1/product-selling-locations': () => {
        branchReads += 1;
        return branchReads === 1 ? json(503, { code: 'LOCATION_DEPENDENCY_UNAVAILABLE', message: 'Down.' }) : json(200, { items: [HK] });
      },
      [LOCATIONS]: json(200, [HK]),
    });
    open('/catalogue-management/products');
    await user.click(await screen.findByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(screen.getByRole('option', { name: 'Hoàn Kiếm' })).toBeInTheDocument());
    expect(branchReads).toBe(2);
  });
});

describe('T186 create form without an active category (FR-004, FR-038)', () => {
  it('explains why no category can be chosen and links the category page', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }),
      [`GET ${M}/categories`]: json(200, { ...categoryPage, items: categoryPage.items.map(category => ({ ...category, active: false })) }),
      [LOCATIONS]: json(200, [{ id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true }]),
    });
    open('/catalogue-management/products/new');
    expect(await screen.findByText(/Chưa có danh mục nào đang hoạt động/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mở trang danh mục' })).toHaveAttribute('href', '/catalogue-management/categories');
  });
});

describe('T188 size conversion chart in English (FR-059)', () => {
  it('shows the English content when English is chosen', async () => {
    const user = userEvent.setup();
    mockFetch({ 'GET /api/v1/products/42': json(200, publicProduct()) });
    open('/products/42', { language: 'en' });
    await user.click(await screen.findByRole('button', { name: /Size conversion chart/ }));
    const dialog = screen.getByRole('dialog', { name: 'Ring size conversion chart' });
    expect(within(within(dialog).getByRole('region', { name: /Measure the diameter/ })).getAllByRole('listitem')).toHaveLength(4);
    expect(within(within(dialog).getByRole('region', { name: /Measure your finger/ })).getAllByRole('listitem')).toHaveLength(5);
  });
});

describe('US2 scenario 4: a rejected review is resubmitted after a change', () => {
  it('shows the reason and how to resubmit, without a submit button that would be refused', async () => {
    const rejected = managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'REJECTED', effective: true, reason: 'Ảnh mờ' }] });
    mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage),
      [LOCATIONS]: json(200, [{ id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true }]),
      [`GET ${M}/products/42`]: json(200, rejected),
    });
    open('/catalogue-management/products/42');
    const section = await screen.findByRole('region', { name: 'Duyệt hồ sơ ban đầu' });
    expect(within(section).getAllByText(/Ảnh mờ/).length).toBeGreaterThan(0);
    expect(within(section).getByText(/để tạo phiên bản mới, rồi bấm Gửi duyệt/)).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: 'Gửi duyệt' })).toBeNull();
  });
});
