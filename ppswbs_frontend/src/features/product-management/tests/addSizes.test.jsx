import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { actionResult, categoryPage, managementProduct } from './fixtures';
import { beforeEach as beforeEachSignIn } from 'vitest';
import { signInAs } from './fixtures';

// RouteAccessGuard (T219) reads the 001 Account bootstrap: an active Staff unless a test says otherwise.
beforeEachSignIn(() => { signInAs('STAFF'); });

// FR-069 (Session 2026-11-23): adding sizes after creation, at add-branch time and later at a selling branch.
const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const LOCATIONS = 'GET /api/v1/workshop-locations';
const HK = { id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true };
const TH = { id: 2, code: 'HN-TAYHO', name: 'Tây Hồ', address: 'Hà Nội', active: true };

const applied = { id: '500', variantId: '101', revision: 1, version: 2, state: 'APPROVED', effective: true, proposedPrice: '1250000', approvedPrice: '1250000', baseAppliedSequence: 0, applied: true };
const size12 = {
  ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 1, proposals: [applied],
  branchStocks: [{ locationId: '1', remainingUnsold: '3', saleEnabled: true, version: 0 }, { locationId: '2', remainingUnsold: '2', saleEnabled: true, version: 7 }],
};
// Size 6 was added at Tây Hồ only: Hoàn Kiếm has no row for it.
const size6AtTayHo = {
  id: '106', size: 6, sku: 'SP000042-S06', remainingUnsold: '1', validHeldQuantity: '0', availableQuantity: '1', saleEnabled: true,
  appliedPrice: '900000', appliedSequence: 1, version: 1, proposals: [{ ...applied, id: '506', variantId: '106', approvedPrice: '900000', proposedPrice: '900000' }],
  branchStocks: [{ locationId: '2', remainingUnsold: '1', saleEnabled: true, version: 0 }],
};
const published = managementProduct({
  published: true, firstPublishedAt: '2026-11-01T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
  branches: [
    { locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true },
    { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true },
  ],
  variants: [size6AtTayHo, size12],
});
const withNewSize8 = {
  ...published, version: 4,
  variants: [size6AtTayHo, {
    id: '108', size: 8, sku: 'SP000042-S08', remainingUnsold: '2', validHeldQuantity: '0', availableQuantity: '2', saleEnabled: true,
    appliedSequence: 0, version: 0,
    proposals: [{ id: '608', variantId: '108', revision: 1, version: 0, state: 'DRAFT', effective: true, proposedPrice: '950000', baseAppliedSequence: 0, applied: false }],
    branchStocks: [{ locationId: '1', remainingUnsold: '2', saleEnabled: true, version: 0 }],
  }, size12],
};

function handlers(role, after) {
  let added = false;
  return {
    [PROFILE]: json(200, { role }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, role === 'MANAGER' ? [HK, TH] : [HK]),
    [`GET ${M}/products/42`]: () => json(200, added ? after : published),
    [`POST ${M}/products/42/branches/1/sizes`]: () => { added = true; return json(200, actionResult('42', 'product.size.add')); },
    [`POST ${M}/price-proposals/608/submissions`]: json(200, { ...actionResult('608', 'price.submit'), version: 1 }),
    [`POST ${M}/price-proposals/608/decisions`]: json(200, { ...actionResult('608', 'price.decide'), version: 2 }),
    [`POST ${M}/price-proposals/608/applications`]: json(200, actionResult('608', 'price.apply')),
  };
}

async function openAddSizes(user) {
  await user.click(await screen.findByRole('tab', { name: /Hoàn Kiếm/ }));
  await user.click(screen.getByRole('button', { name: 'Thêm size' }));
  return screen.getByRole('form', { name: 'Thêm size tại Hoàn Kiếm' });
}

describe('FR-069 adding sizes at a selling branch', () => {
  beforeEach(() => clearRoleCache());

  it('shows a size of another branch as not sold here, without a quantity edit', async () => {
    mockFetch(handlers('STAFF', published));
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    await userEvent.setup().click(await screen.findByRole('tab', { name: /Hoàn Kiếm/ }));
    const row = within(screen.getByRole('table', { name: 'Size và số lượng' })).getAllByRole('row')[1];
    expect(within(row).getByText('Chưa bán ở chi nhánh này')).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: 'Sửa' })).toBeNull();
  });

  it('Staff add a new size with a proposed price and an existing size with a quantity only, then submit the new price', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const calls = mockFetch(handlers('STAFF', withNewSize8));
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    const form = await openAddSizes(user);

    await user.click(within(form).getByRole('checkbox', { name: /^6/ }));
    expect(within(form).getByText('Giá dùng chung của sản phẩm')).toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Tăng Số lượng size 6 tại Hoàn Kiếm' }));
    await user.click(within(form).getByRole('checkbox', { name: /^8/ }));
    await user.click(within(form).getByRole('button', { name: 'Thêm size' }));
    expect(within(form).getByText(/Số tiền là số nguyên dương/)).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);

    await user.type(within(form).getByLabelText('Giá đề xuất size 8'), '950000');
    await user.click(within(form).getByRole('button', { name: 'Tăng Số lượng size 8 tại Hoàn Kiếm' }));
    await user.click(within(form).getByRole('button', { name: 'Tăng Số lượng size 8 tại Hoàn Kiếm' }));
    await user.click(within(form).getByRole('button', { name: 'Thêm size' }));

    expect((await screen.findAllByText(/Đã thêm size 8 tại Hoàn Kiếm và gửi duyệt giá/)).length).toBeGreaterThan(0);
    const add = calls.find(call => call.path.endsWith('/branches/1/sizes'));
    expect(add.body).toEqual({ expectedVersion: 3, sizes: [{ size: 6, quantity: '1' }, { size: 8, quantity: '2', proposedPrice: '950000' }] });
    expect(calls.some(call => call.path.endsWith('/608/submissions'))).toBe(true);
    expect(calls.some(call => call.path.endsWith('/608/decisions'))).toBe(false);
  });

  it('a size range takes one shared price and quantity; the price only goes to new sizes, rows stay editable', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const calls = mockFetch(handlers('STAFF', withNewSize8));
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    const form = await openAddSizes(user);

    await user.selectOptions(within(form).getByLabelText('Từ size'), '6');
    await user.selectOptions(within(form).getByLabelText('Đến size'), '8');
    await user.click(within(form).getByRole('button', { name: 'Chọn khoảng size' }));
    expect(within(form).getByText('Áp dụng cho 3 size đã chọn (giá chỉ áp dụng cho size mới):')).toBeInTheDocument();
    await user.type(within(form).getByLabelText('Giá đề xuất chung (₫)'), '950000');
    await user.type(within(form).getByRole('textbox', { name: 'Số lượng chung' }), '3');
    await user.click(within(form).getByRole('button', { name: 'Áp dụng' }));
    await user.clear(within(form).getByLabelText('Giá đề xuất size 7'));
    await user.type(within(form).getByLabelText('Giá đề xuất size 7'), '900000');
    await user.click(within(form).getByRole('button', { name: 'Thêm size' }));

    await screen.findAllByText(/Đã thêm size/);
    const add = calls.find(call => call.path.endsWith('/branches/1/sizes'));
    expect(add.body).toEqual({ expectedVersion: 3, sizes: [
      { size: 6, quantity: '3' }, { size: 7, quantity: '3', proposedPrice: '900000' }, { size: 8, quantity: '3', proposedPrice: '950000' },
    ] });
  });

  it('a Manager adding a new size approves and applies its price in the same save', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const calls = mockFetch(handlers('MANAGER', withNewSize8));
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    const form = await openAddSizes(user);

    await user.click(within(form).getByRole('checkbox', { name: /^8/ }));
    await user.type(within(form).getByLabelText('Giá bán size 8'), '950000');
    await user.click(within(form).getByRole('button', { name: 'Thêm size' }));

    expect((await screen.findAllByText(/đã duyệt và áp dụng giá/)).length).toBeGreaterThan(0);
    const decide = calls.find(call => call.path.endsWith('/608/decisions'));
    expect(decide.body).toEqual({ expectedVersion: 1, decision: 'APPROVE', approvedPrice: '950000' });
    expect(calls.some(call => call.path.endsWith('/608/applications'))).toBe(true);
  });

  it('adding a branch can bring new sizes; before the first publication their prices join the review draft', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const draft = managementProduct({
      branches: [{ locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }],
      variants: [{ ...managementProduct().variants[0], branchStocks: [{ locationId: '2', remainingUnsold: '5', saleEnabled: true, version: 0 }] }],
    });
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, [HK]),
      [`GET ${M}/products/42`]: json(200, draft),
      [`POST ${M}/products/42/branches`]: json(200, actionResult('42', 'product.branch.add')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });

    await user.click(await screen.findByRole('button', { name: 'Thêm chi nhánh bán' }));
    await user.click(screen.getByRole('checkbox', { name: /^7/ }));
    await user.type(screen.getByLabelText('Giá đề xuất size 7'), '800000');
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng size 7 tại Hoàn Kiếm' }));
    await user.click(screen.getByRole('button', { name: 'Lưu chi nhánh' }));

    expect((await screen.findAllByText(/Hồ sơ sản phẩm đang ở Nháp/)).length).toBeGreaterThan(0);
    const add = calls.find(call => call.method === 'POST' && call.path.endsWith('/products/42/branches'));
    expect(add.body).toEqual({
      expectedVersion: 3, locationId: '1',
      quantities: [{ size: 12, quantity: '0' }, { size: 7, quantity: '1' }],
      newSizes: [{ size: 7, proposedPrice: '800000' }],
    });
    expect(calls.some(call => call.path.includes('/price-proposals/'))).toBe(false);
  });
});
