import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';

const variant = { id: '101', size: 12, sku: 'SP000042-S12', remainingUnsold: '5', validHeldQuantity: '0', availableQuantity: '5', saleEnabled: true, appliedSequence: 0, version: 4,
  proposals: [{ id: '501', variantId: '101', revision: 1, version: 0, state: 'DRAFT', effective: true, proposedPrice: '1250000', baseAppliedSequence: 0, applied: false }] };
const withReview = state => managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state, effective: true }], variants: [variant] });

describe('Manager: no review round for their own product', () => {
  beforeEach(() => clearRoleCache());

  it('create offers publish now or keep hidden, names the price a selling price, and needs an image before sending', async () => {
    const calls = mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/new' });
    const user = userEvent.setup();

    expect(await screen.findByRole('radio', { name: 'Công khai ngay' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Chưa công khai/ })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: 'Công khai ngay' }));
    expect(screen.getByRole('button', { name: 'Tạo và công khai' })).toBeInTheDocument();

    await user.type(screen.getByLabelText('Tên sản phẩm'), 'Nhẫn bạc trơn');
    await user.click(screen.getByRole('button', { name: '12' }));
    expect(screen.getByRole('columnheader', { name: 'Giá bán (₫)' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tạo và công khai' }));

    expect((await screen.findAllByText(/Cần ít nhất một ảnh/)).length).toBeGreaterThan(0);
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0);
  });

  it('Staff keep the review wording and no publish choice', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/new' });
    expect(await screen.findByRole('button', { name: 'Tạo sản phẩm' })).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Công khai ngay' })).toBeNull();
  });

  it('the detail page hides the review section from a Manager unless a Staff submitted it', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, withReview('DRAFT')) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    expect(await screen.findByRole('button', { name: 'Công khai ngay' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).toBeNull();
  });

  it('a Manager still sees the review a Staff submitted', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, withReview('PENDING')) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    expect(await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).toBeInTheDocument();
  });
});

describe('quantity steppers', () => {
  beforeEach(() => clearRoleCache());

  it('− and + change a size quantity by one, never below zero, and typing still works', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/new' });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '12' }));

    const quantity = screen.getByLabelText('Số lượng size 12');
    expect(quantity).toHaveValue('0');
    expect(screen.getByRole('button', { name: 'Giảm Số lượng size 12' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng size 12' }));
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng size 12' }));
    expect(quantity).toHaveValue('2');
    await user.click(screen.getByRole('button', { name: 'Giảm Số lượng size 12' }));
    expect(quantity).toHaveValue('1');
    await user.clear(quantity);
    await user.type(quantity, '40');
    expect(quantity).toHaveValue('40');
  });

  it('the per-size quantity edit on the detail page has the steppers too', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, withReview('DRAFT')) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    const user = userEvent.setup();
    const table = await screen.findByRole('table', { name: 'Size và số lượng' });
    await user.click(within(table).getAllByRole('button', { name: 'Sửa' })[0]);
    const input = await screen.findByLabelText('Số lượng mới của size 12');
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng mới của size 12' }));
    expect(input).toHaveValue('6');
  });
});
