import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { actionResult, categoryPage, managementProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const LOCATIONS = 'GET /api/v1/workshop-locations';
const HK = { id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true };
const TH = { id: 2, code: 'HN-TAYHO', name: 'Tây Hồ', address: 'Hà Nội', active: true };

const atTwoBranches = managementProduct({
  branches: [
    { locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true },
    { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true },
  ],
  variants: [{
    ...managementProduct().variants[0], remainingUnsold: '5',
    branchStocks: [{ locationId: '1', remainingUnsold: '3', saleEnabled: true, version: 0 }, { locationId: '2', remainingUnsold: '2', saleEnabled: true, version: 7 }],
  }],
});

describe('US10 branches on the create form', () => {
  beforeEach(() => clearRoleCache());

  it('lists the account branches, one quantity column per chosen branch, and sends them', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, [HK, TH]),
      [`POST ${M}/products`]: json(400, { code: 'VALIDATION_ERROR', message: 'x' }),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/new' });

    const group = await screen.findByRole('group', { name: 'Chi nhánh bán' });
    await user.click(within(group).getByRole('checkbox', { name: /Hoàn Kiếm/ }));
    await user.click(within(group).getByRole('checkbox', { name: /Tây Hồ/ }));
    await user.click(screen.getByRole('button', { name: '12' }));
    expect(screen.getByRole('columnheader', { name: 'Số lượng tại Hoàn Kiếm' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng size 12 tại Tây Hồ' }));

    await user.type(screen.getByLabelText('Tên sản phẩm'), 'Nhẫn');
    await user.type(screen.getByLabelText('Mô tả'), 'Bạc');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.type(screen.getByLabelText('Giá đề xuất size 12'), '100000');
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));

    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    const post = calls.find(call => call.method === 'POST');
    expect(post.body.branches).toEqual([
      { locationId: '1', quantities: [{ size: 12, quantity: '0' }] },
      { locationId: '2', quantities: [{ size: 12, quantity: '1' }] },
    ]);
  });

  it('without a branch nothing is sent; a Staff member with no branch is told to ask an Admin', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, []) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/new' });
    expect(await screen.findByText(/chưa được gán chi nhánh nào/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));
    expect(screen.getByText('Chọn ít nhất một chi nhánh.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
  });
});

describe('US10 FR-051 branch filter on the product list', () => {
  beforeEach(() => clearRoleCache());

  it('offers every active branch and sends the chosen one as locationId', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      'GET /api/v1/product-selling-locations': json(200, { items: [{ id: '1', name: 'Hoàn Kiếm', address: '' }, { id: '2', name: 'Tây Hồ', address: '' }] }),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });
    await screen.findByRole('option', { name: 'Tây Hồ' });
    await user.selectOptions(screen.getByLabelText('Chi nhánh'), '2');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    await waitFor(() => expect(calls.filter(call => call.path === `${M}/products`).at(-1).query).toEqual({ locationId: '2' }));
  });
});

describe('US10 Q-B2 branches on the Excel import', () => {
  beforeEach(() => clearRoleCache());

  it('the chosen branches, in order, go to the template and to the upload', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    globalThis.URL.createObjectURL = globalThis.URL.createObjectURL || (() => 'blob:x');
    globalThis.URL.revokeObjectURL = globalThis.URL.revokeObjectURL || (() => {});
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]),
      [`GET ${M}/product-imports/template`]: () => new Response(new Blob(['x']), { status: 200 }),
      [`POST ${M}/product-imports`]: json(201, {
        batchId: 'b9', fileName: 'a.xlsx', state: 'REJECTED', version: 0, rowCount: 1, productCount: 0, variantCount: 0, errorCount: 1,
        errors: [{ row: 2, column: 'Số lượng Tây Hồ', value: '-1', code: 'OUT_OF_RANGE', message: 'x' }], expiresAt: '2026-11-01T10:00:00Z',
      }),
    });
    const { container } = renderRoutes(productManagementRoutes, { path: '/catalogue-management/imports' });

    const group = await screen.findByRole('group', { name: 'Chi nhánh bán' });
    expect(screen.getByRole('button', { name: 'Tải file mẫu' })).toBeDisabled();
    await user.click(within(group).getByRole('checkbox', { name: /Tây Hồ/ }));
    await user.click(within(group).getByRole('checkbox', { name: /Hoàn Kiếm/ }));
    expect(screen.getByText(/"Số lượng Tây Hồ", "Số lượng Hoàn Kiếm"/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tải file mẫu' }));
    await waitFor(() => expect(calls.some(call => call.path.endsWith('/template'))).toBe(true));
    expect(calls.find(call => call.path.endsWith('/template')).query).toEqual({ locationIds: '2,1' });

    await user.upload(container.querySelector('input[type=file]'), new File(['x'], 'a.xlsx'));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.getAll('locationIds')).toEqual(['2', '1']);
  });
});

describe('US10 branches on the detail page', () => {
  beforeEach(() => clearRoleCache());

  it('shows a tab per branch and changes the quantity at the chosen branch with its own command', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, [HK, TH]),
      [`GET ${M}/products/42`]: json(200, atTwoBranches),
      [`PUT ${M}/variants/101/branches/2/quantity`]: json(200, actionResult('101', 'variant.branch.quantity')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });

    await user.click(await screen.findByRole('tab', { name: /Tây Hồ/ }));
    const table = screen.getByRole('table', { name: 'Size và số lượng' });
    expect(within(table).getByRole('columnheader', { name: 'Số lượng tại Tây Hồ' })).toBeInTheDocument();
    await user.click(within(table).getAllByRole('button', { name: 'Sửa' })[0]);
    await user.click(screen.getByRole('button', { name: 'Tăng Số lượng mới của size 12' }));
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    const put = await screen.findAllByText(/Đã sửa số lượng size 12/).then(() => calls.find(call => call.method === 'PUT'));
    expect(put.body).toEqual({ expectedVersion: 7, quantity: '3' });
  });

  it('a product without a branch offers to assign one with its current quantities', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }), [`GET ${M}/categories`]: json(200, categoryPage), [LOCATIONS]: json(200, [HK]),
      [`GET ${M}/products/42`]: json(200, managementProduct({ branches: [] })),
      [`POST ${M}/products/42/branches`]: json(200, actionResult('42', 'product.branch.add')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });

    expect(await screen.findByText(/chưa được gán chi nhánh/)).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Gán chi nhánh' }));
    await user.click(screen.getByRole('button', { name: 'Lưu chi nhánh' }));
    await screen.findAllByText(/Đã thêm chi nhánh Hoàn Kiếm/);
    expect(calls.find(call => call.method === 'POST').body).toEqual({ expectedVersion: 3, locationId: '1', quantities: [{ size: 12, quantity: '5' }] });
  });
});
