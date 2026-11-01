import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes';
import { actionResult, categoryPage, managementProduct, publicProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const open = (path, options) => renderRoutes(productManagementRoutes, { path, ...options });

describe('management shell', () => {
  it('asks to sign in without a session and sends no request', async () => {
    const calls = mockFetch({});
    open('/catalogue-management/products', { session: fakeSession('signed-out') });
    expect(await screen.findByText(/cần đăng nhập/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('says when Firebase is not configured', async () => {
    mockFetch({});
    open('/catalogue-management/products', { session: fakeSession('unconfigured') });
    expect(await screen.findByText(/chưa được cấu hình Firebase/)).toBeInTheDocument();
  });
});

describe('T028 product list and create', () => {
  it('lists products with the filters taken from the URL', async () => {
    const calls = mockFetch({
      [`GET ${M}/products`]: json(200, { items: [managementProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?q=b%E1%BA%A1c&published=false');
    expect(await screen.findByRole('link', { name: 'Nhẫn bạc' })).toHaveAttribute('href', '/catalogue-management/products/42');
    const list = calls.find(call => call.path === `${M}/products`);
    expect(list.query).toEqual({ q: 'bạc', published: 'false' });
    expect(list.headers.Authorization).toBe('Bearer test-token');
  });

  it('shows an empty state different from an error', async () => {
    mockFetch({ [`GET ${M}/products`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products');
    expect(await screen.findByText('Chưa có sản phẩm nào khớp với bộ lọc.')).toBeInTheDocument();
  });

  it('keeps the typed data and puts server field errors on their field', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products`]: json(400, { code: 'VALIDATION_ERROR', message: 'Invalid.', correlationId: 'corr-1', fieldErrors: [{ field: 'variants[0].proposedPrice', message: 'Must be a positive amount.' }] }),
    });
    open('/catalogue-management/products/new');
    await user.type(await screen.findByLabelText('Tên sản phẩm'), 'Nhẫn mới');
    await user.type(screen.getByLabelText('Mô tả'), 'Mô tả');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.type(screen.getByLabelText('Size'), '12');
    await user.type(screen.getByLabelText('Giá đề xuất (₫)'), '1250000');
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));

    expect(await screen.findByText('Must be a positive amount.')).toBeInTheDocument();
    expect(screen.getByText('Mã tra cứu: corr-1')).toBeInTheDocument();
    expect(screen.getByLabelText('Tên sản phẩm')).toHaveValue('Nhẫn mới');
    const create = calls.find(call => call.method === 'POST');
    expect(create.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(create.body).toEqual({ name: 'Nhẫn mới', description: 'Mô tả', categoryId: '7', variants: [{ size: 12, proposedPrice: '1250000', quantity: '0' }], images: [] });
  });

  it('checks the input before sending anything', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/new');
    await user.click(await screen.findByRole('button', { name: 'Tạo sản phẩm' }));
    expect(screen.getAllByText('Bắt buộc nhập.').length).toBeGreaterThan(0);
    expect(screen.getByText('Size là số nguyên từ 5 đến 29.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
  });

  it('a 503 is retried with the same key and then opens the product', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products`]: () => (++attempts === 1 ? json(503, { code: 'CATALOGUE_BUSY', message: 'Busy.' }) : json(201, actionResult('42', 'product.create'))),
      [`GET ${M}/products/42`]: json(200, managementProduct()),
    });
    open('/catalogue-management/products/new');
    await user.type(await screen.findByLabelText('Tên sản phẩm'), 'Nhẫn bạc');
    await user.type(screen.getByLabelText('Mô tả'), 'Bạc 925');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.type(screen.getByLabelText('Size'), '12');
    await user.type(screen.getByLabelText('Giá đề xuất (₫)'), '1250000');
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));
    await user.click(await screen.findByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Nhẫn bạc' })).toBeInTheDocument();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts).toHaveLength(2);
    expect(posts[1].headers['Idempotency-Key']).toBe(posts[0].headers['Idempotency-Key']);
  });
});

describe('T050 product detail edits', () => {
  it('on 409 STALE_VERSION loads the latest product and keeps the typed content', async () => {
    const user = userEvent.setup();
    let reads = 0;
    const calls = mockFetch({
      [`GET ${M}/products/42`]: () => json(200, managementProduct({ version: reads++ === 0 ? 3 : 5 })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/products/42`]: json(409, { code: 'STALE_VERSION', message: 'Stale.' }),
    });
    open('/catalogue-management/products/42');
    const content = (await screen.findByRole('heading', { name: 'Nội dung' })).closest('section');
    await user.click(within(content).getByRole('button', { name: 'Sửa' }));
    const name = within(content).getByLabelText('Tên sản phẩm');
    await user.clear(name);
    await user.type(name, 'Tên đã sửa');
    await user.click(within(content).getByRole('button', { name: 'Lưu' }));

    expect(await within(content).findByText(/người khác thay đổi/)).toBeInTheDocument();
    await user.click(within(content).getByRole('button', { name: 'Tải bản mới nhất' }));
    await waitFor(() => expect(reads).toBe(2));
    expect(within(content).getByLabelText('Tên sản phẩm')).toHaveValue('Tên đã sửa');
    expect(calls.find(call => call.method === 'PUT').body.expectedVersion).toBe(3);
  });

  it('shows remaining, held and available apart and refuses a quantity below held', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/variants/101/quantity`]: json(409, { code: 'QUANTITY_BELOW_HELD', message: 'Below held.' }),
    });
    open('/catalogue-management/products/42');
    const variants = (await screen.findByRole('heading', { name: 'Size và số lượng' })).closest('section');
    expect(within(variants).getByText('P42-S12')).toBeInTheDocument();
    await user.click(within(variants).getByRole('button', { name: 'Sửa' }));
    const input = within(variants).getByLabelText('Số lượng mới của size 12');
    await user.clear(input);
    await user.type(input, '1');
    await user.click(within(variants).getByRole('button', { name: 'Lưu' }));
    expect(await within(variants).findByText('Số lượng mới thấp hơn số đang được giữ cho đơn hàng.')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/quantity')).body).toEqual({ expectedVersion: 4, quantity: '1' });
  });
});

describe('T034 review decision', () => {
  it('rejecting needs a reason and approving sends a price for every size', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products/42/review-decisions`]: json(200, actionResult('42', 'product.review.decide')),
    });
    open('/catalogue-management/products/42');
    const review = (await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).closest('section');
    await user.click(within(review).getByRole('button', { name: 'Từ chối' }));
    await user.click(within(review).getAllByRole('button', { name: 'Từ chối' }).at(-1));
    expect(within(review).getByText('Cần nhập lý do.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);

    await user.click(within(review).getByRole('button', { name: 'Hủy' }));
    await user.click(within(review).getByRole('button', { name: 'Duyệt' }));
    const price = within(review).getByLabelText('Giá duyệt size 12');
    await user.clear(price);
    await user.type(price, '1200000');
    await user.click(within(review).getByRole('button', { name: 'Duyệt toàn bộ hồ sơ' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body).toEqual({
      reviewId: '301', reviewRevision: 1, contentVersion: 1, decision: 'APPROVE',
      prices: [{ variantId: '101', proposalId: '501', approvedPrice: '1200000' }],
    });
  });
});

describe('T064 delete', () => {
  it('Cancel in the confirmation sends nothing; Confirm deletes with expectedVersion', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`DELETE ${M}/products/42`]: json(200, actionResult('42', 'product.delete')),
      [`GET ${M}/products`]: json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
    });
    open('/catalogue-management/products/42');
    const zone = (await screen.findByRole('heading', { name: 'Xóa sản phẩm' })).closest('section');
    await user.click(within(zone).getByRole('button', { name: 'Xóa sản phẩm' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Hủy' }));
    expect(calls.some(call => call.method === 'DELETE')).toBe(false);

    await user.click(within(zone).getByRole('button', { name: 'Xóa sản phẩm' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xóa sản phẩm' }));
    expect(await screen.findByText('Đã xóa sản phẩm.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'DELETE').query).toEqual({ expectedVersion: '3' });
  });
});

describe('T079 category activation', () => {
  it('shows the impact before deactivating and uses its version', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/categories/7/impact`]: json(200, { categoryId: '7', version: 2, active: true, affectedProducts: { items: [managementProduct()], page: 0, pageSize: 20, totalElements: '1' } }),
      [`PUT ${M}/categories/7/activation`]: json(200, actionResult('7', 'category.activation')),
    });
    open('/catalogue-management/categories');
    await user.click(await screen.findByRole('button', { name: 'Ngừng hoạt động' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('1 sản phẩm thuộc danh mục này.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Ngừng hoạt động' }));
    expect(await screen.findByText('Đã ngừng hoạt động danh mục.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ expectedVersion: 2, active: false });
  });
});

describe('T101 import', () => {
  it('shows row errors of a rejected file', async () => {
    const user = userEvent.setup();
    mockFetch({
      [`POST ${M}/product-imports`]: json(201, {
        batchId: 'b1', fileName: 'a.xlsx', state: 'REJECTED', version: 0, rowCount: 2, productCount: 0, variantCount: 0, errorCount: 1,
        errors: [{ row: 3, column: 'Size', value: '40', code: 'OUT_OF_RANGE', message: 'Out of range.' }], expiresAt: '2026-11-01T10:00:00Z',
      }),
    });
    const { container } = open('/catalogue-management/imports');
    const input = container.querySelector('input[type=file]');
    await user.upload(input, new File(['x'], 'a.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    expect(await screen.findByText('Ngoài khoảng cho phép.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tải báo cáo lỗi' })).toBeInTheDocument();
  });
});

describe('T071 public catalogue', () => {
  it('keeps search criteria in the URL and shows price ranges', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/products': json(200, { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      'GET /api/v1/product-categories': json(200, categoryPage),
    });
    const { router } = open('/products?sort=price-asc');
    expect(await screen.findByText('1.250.000 ₫ – 1.300.000 ₫')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Tìm kiếm'), 'bạc');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    await waitFor(() => expect(router.state.location.search).toBe('?q=b%E1%BA%A1c&sort=price-asc'));
    const reads = calls.filter(call => call.path === '/api/v1/products');
    expect(reads.at(-1).query).toEqual({ q: 'bạc', sort: 'price-asc' });
    expect(reads.every(call => !call.headers.Authorization)).toBe(true);
  });

  it('detail labels a disabled size and offers no buy action', async () => {
    mockFetch({ 'GET /api/v1/products/42': json(200, publicProduct()) });
    open('/products/42');
    expect(await screen.findByRole('heading', { level: 1, name: 'Nhẫn bạc' })).toBeInTheDocument();
    expect(screen.getByText('Ngừng bán')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /giỏ|mua/i })).toBeNull();
  });

  it('a hidden or deleted product reads as not found', async () => {
    mockFetch({ 'GET /api/v1/products/9': json(404, { code: 'PRODUCT_NOT_FOUND', message: 'Not found.' }) });
    open('/products/9');
    expect(await screen.findByText('Sản phẩm không tồn tại hoặc không còn được công khai')).toBeInTheDocument();
  });

  it('switches to English', async () => {
    mockFetch({ 'GET /api/v1/products': json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }), 'GET /api/v1/product-categories': json(200, categoryPage) });
    open('/products', { language: 'en' });
    expect(await screen.findByText('No product matches these criteria.')).toBeInTheDocument();
  });
});
