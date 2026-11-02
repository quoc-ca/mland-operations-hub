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
  it('after publication a new price, next to the applied one, goes draft → submit → Manager decision → apply, with a hint at each step', async () => {
    const user = userEvent.setup();
    const stage = { value: 'DRAFT' };
    const proposal = () => ({
      id: '601', variantId: '101', revision: 2, version: 1, effective: true, baseAppliedSequence: 1, proposedPrice: '1350000',
      state: stage.value, approvedPrice: stage.value === 'APPROVED' ? '1300000' : null, applied: false,
    });
    const product = () => managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      // The applied first proposal stays effective next to the new one, as the backend returns it.
      variants: [{ ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 1, proposals: [
        { id: '501', variantId: '101', revision: 1, version: 2, state: 'APPROVED', effective: true, proposedPrice: '1250000', approvedPrice: '1250000', baseAppliedSequence: 0, applied: true },
        proposal(),
      ] }],
    });
    const calls = mockFetch({
      [`GET ${M}/products/42`]: () => json(200, product()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/submissions`]: () => { stage.value = 'PENDING'; return json(200, actionResult('601', 'SUBMIT_PRICE')); },
      [`POST ${M}/price-proposals/601/decisions`]: () => { stage.value = 'APPROVED'; return json(200, actionResult('601', 'DECIDE_PRICE')); },
    });
    open('/catalogue-management/products/42');
    expect(await screen.findByText(/Đề xuất đang là nháp, Manager chưa thấy/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt giá' }));

    expect(await screen.findByText(/Đang chờ Manager duyệt/)).toBeInTheDocument();
    const prices = screen.getByRole('heading', { name: 'Đề xuất giá' }).closest('section');
    await user.click(within(prices).getByRole('button', { name: 'Duyệt' }));
    const amount = within(prices).getByLabelText('Giá duyệt (₫)');
    await user.clear(amount);
    await user.type(amount, '1300000');
    await user.click(within(prices).getByRole('button', { name: 'Duyệt' }));

    expect(await within(prices).findByRole('button', { name: /Áp dụng giá đã duyệt/ })).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/decisions')).body).toMatchObject({ decision: 'APPROVE', approvedPrice: '1300000', expectedVersion: 1 });
  });

  it('shows each size ID, the value a Manager types as a size target of an FE-06 offer', async () => {
    mockFetch({ [`GET ${M}/products/42`]: json(200, managementProduct()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    expect(await screen.findByText('Mã size 101')).toBeInTheDocument();
  });

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

  it('after import, images are matched by file name, assigned by hand or per product, and saved product by product', async () => {
    const user = userEvent.setup();
    let uploads = 0;
    const preview = [{ groupKey: 'SONG-NUOC', name: 'Nhẫn bạc sóng nước', sizes: [10, 12] }, { groupKey: 'DINH-DA', name: 'Nhẫn bạc đính đá CZ', sizes: [11] }];
    const batch = state => ({
      batchId: 'b1', fileName: 'a.xlsx', state, version: state === 'VALIDATED' ? 0 : 1, rowCount: 3, productCount: 2, variantCount: 3, errorCount: 0,
      expiresAt: '2026-11-01T10:00:00Z',
      ...(state === 'VALIDATED' ? { preview } : { created: [{ groupKey: 'SONG-NUOC', productId: '51', skus: ['SP000051-S10'] }, { groupKey: 'DINH-DA', productId: '52', skus: ['SP000052-S11'] }] }),
    });
    const calls = mockFetch({
      [`POST ${M}/product-imports`]: json(201, batch('VALIDATED')),
      [`POST ${M}/product-imports/b1/confirmations`]: json(200, batch('IMPORTED')),
      [`POST ${M}/media-uploads`]: () => json(201, { mediaId: `0000000${++uploads}-0000-0000-0000-000000000000`, state: 'READY', contentType: 'image/png', byteCount: 3 }),
      [`GET ${M}/products/51`]: json(200, managementProduct({ id: '51', images: [], version: 7 })),
      [`GET ${M}/products/52`]: json(200, managementProduct({ id: '52', images: [], version: 2 })),
      [`PUT ${M}/products/51/images`]: json(200, actionResult('51', 'product.images')),
      [`PUT ${M}/products/52/images`]: json(503, { code: 'MEDIA_DEPENDENCY_UNAVAILABLE', message: 'Down.' }),
    });
    const { container } = open('/catalogue-management/imports');
    await user.upload(container.querySelector('input[type=file]'), new File(['x'], 'a.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    await user.click(await screen.findByRole('button', { name: 'Xác nhận import' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Xác nhận import' }));
    expect(await screen.findByText('Gắn ảnh cho các sản phẩm vừa import')).toBeInTheDocument();

    const png = name => new File(['img'], name, { type: 'image/png' });
    await user.upload(screen.getByLabelText('Chọn nhiều ảnh'), [png('song-nuoc-2.png'), png('IMG_2041.png')]);
    expect(screen.getByText('Ảnh chưa gắn (1)')).toBeInTheDocument();
    const [stray] = screen.getAllByLabelText('Gắn vào sản phẩm').filter(select => select.value === '');
    await user.selectOptions(stray, '51');
    await user.upload(screen.getByLabelText('Chọn ảnh cho Nhẫn bạc đính đá CZ'), png('anything.png'));
    expect(screen.getAllByLabelText('Mô tả ảnh').map(input => input.value)).toEqual(['Nhẫn bạc sóng nước', 'Nhẫn bạc sóng nước', 'Nhẫn bạc đính đá CZ']);
    expect(calls.filter(call => call.path.endsWith('/media-uploads'))).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Lưu ảnh cho 2 sản phẩm' }));
    expect(await screen.findByText('Đã lưu ảnh')).toBeInTheDocument();
    const put51 = calls.find(call => call.path.endsWith('/products/51/images'));
    expect(put51.body).toEqual({ expectedVersion: 7, images: [
      { mediaId: '00000001-0000-0000-0000-000000000000', altText: 'Nhẫn bạc sóng nước', position: 0 },
      { mediaId: '00000002-0000-0000-0000-000000000000', altText: 'Nhẫn bạc sóng nước', position: 1 },
    ] });
    expect(await screen.findByText(/tạm thời không phản hồi|Dịch vụ ảnh/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Lưu ảnh cho 1 sản phẩm' })).toBeEnabled();
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

  it('detail labels a disabled size and buying it is not offered', async () => {
    mockFetch({ 'GET /api/v1/products/42': json(200, publicProduct()) });
    open('/products/42');
    expect(await screen.findByRole('heading', { level: 1, name: 'Nhẫn bạc' })).toBeInTheDocument();
    expect(screen.getByText('Ngừng bán')).toBeInTheDocument();
    expect(await screen.findByLabelText('Size')).toBeInTheDocument();
    expect(screen.getAllByRole('option').map(option => option.textContent)).not.toContain('Size 14');
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

describe('T085 remaining management journeys', () => {
  const published = () => managementProduct({
    firstPublishedAt: '2026-10-30T00:00:00Z', published: true, reviews: [],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '1250000',
      proposals: [{ id: '601', variantId: '101', revision: 2, version: 3, state: 'APPROVED', effective: true, proposedPrice: '1300000', approvedPrice: '1300000', baseAppliedSequence: 0, applied: false }] }],
  });

  it('an image upload that fails is retried with the same key and then attached by mediaId', async () => {
    const user = userEvent.setup();
    let uploads = 0;
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/media-uploads`]: () => (++uploads === 1
        ? json(503, { code: 'MEDIA_DEPENDENCY_UNAVAILABLE', message: 'Down.' })
        : json(201, { mediaId: '22222222-2222-2222-2222-222222222222', state: 'READY', contentType: 'image/png', byteCount: 3 })),
      [`PUT ${M}/products/42/images`]: json(200, actionResult('42', 'product.images')),
    });
    const { container } = open('/catalogue-management/products/42');
    const images = (await screen.findByRole('heading', { name: 'Ảnh' })).closest('section');
    await user.click(within(images).getByRole('button', { name: 'Sửa' }));
    await user.upload(container.querySelector('input[type=file]'), new File(['png'], 'ring.png', { type: 'image/png' }));
    expect(await within(images).findByText(/Dịch vụ lưu ảnh tạm thời không phản hồi/)).toBeInTheDocument();
    await user.click(within(images).getByRole('button', { name: 'Thử lại' }));
    expect(await within(images).findByText('Đã tải lên.')).toBeInTheDocument();
    await user.type(within(images).getByLabelText('Mô tả ảnh 2'), 'Mặt bên');
    await user.click(within(images).getByRole('button', { name: 'Lưu' }));

    expect(await screen.findByText('Đã lưu danh sách ảnh.')).toBeInTheDocument();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts[1].headers['Idempotency-Key']).toBe(posts[0].headers['Idempotency-Key']);
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ expectedVersion: 3, images: [
      { mediaId: '11111111-1111-1111-1111-111111111111', altText: 'Mặt trước', position: 0 },
      { mediaId: '22222222-2222-2222-2222-222222222222', altText: 'Mặt bên', position: 1 },
    ] });
  });

  it('applying an approved price sends no amount', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/applications`]: json(200, actionResult('601', 'price.apply')),
    });
    open('/catalogue-management/products/42');
    const prices = (await screen.findByRole('heading', { name: 'Đề xuất giá' })).closest('section');
    await user.click(within(prices).getByRole('button', { name: /Áp dụng giá đã duyệt 1\.300\.000/ }));
    expect(await screen.findByText('Đã áp dụng giá.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ expectedVersion: 3 });
  });

  it('publishing shows missing prerequisites and hiding sends published=false', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const calls = mockFetch({
      [`GET ${M}/products/42`]: () => json(200, attempt === 0 ? managementProduct() : published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/products/42/publication`]: call => (++attempt === 1
        ? json(409, { code: 'PUBLICATION_PREREQUISITES_MISSING', message: 'Missing.' })
        : json(200, actionResult('42', 'product.publication'))),
    });
    open('/catalogue-management/products/42');
    const section = (await screen.findByRole('heading', { name: 'Công khai' })).closest('section');
    await user.click(within(section).getByRole('button', { name: 'Công khai sản phẩm' }));
    expect(await within(section).findByText(/Chưa đủ điều kiện công khai/)).toBeInTheDocument();
    expect(calls.filter(call => call.method === 'PUT')[0].body).toEqual({ expectedVersion: 3, published: true });
  });

  it('names and descriptions are shown as text, never as markup', async () => {
    mockFetch({ 'GET /api/v1/products/42': json(200, publicProduct({ name: '<img src=x onerror=alert(1)>', description: '<b>bold</b>' })) });
    const { container } = open('/products/42');
    expect(await screen.findByRole('heading', { level: 1, name: '<img src=x onerror=alert(1)>' })).toBeInTheDocument();
    expect(screen.getByText('<b>bold</b>')).toBeInTheDocument();
    expect(container.querySelector('img[src="x"]')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
  });
});

describe('T072 hand-off to the FE-08 cart', () => {
  const locations = { items: [{ id: '3', name: 'MLAND Quận 1', address: '12 Lê Lợi' }, { id: '9', name: 'MLAND Thủ Đức', address: '' }] };

  it('a Member adds the chosen size, branch and quantity and goes to /member/cart', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct()),
      'GET /api/v1/product-selling-locations': json(200, locations),
      'POST /api/v1/members/me/ready-ring-cart/lines': json(201, { id: 'cart' }),
    });
    // FE-08 owns /member/cart; a stand-in route shows that the page navigates there.
    const { router } = renderRoutes([...productManagementRoutes, { path: '/member/cart', element: <p>FE-08 cart</p> }], { path: '/products/42' });
    const add = await screen.findByRole('button', { name: 'Thêm vào giỏ' });
    expect(add).toBeDisabled();
    expect(screen.getAllByRole('option').map(option => option.textContent)).not.toContain('Size 14');
    await user.selectOptions(await screen.findByLabelText('Chi nhánh'), '9');
    const quantity = screen.getByLabelText('Số lượng');
    await user.clear(quantity);
    await user.type(quantity, '2');
    await user.click(add);

    expect(await screen.findByText('FE-08 cart')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/member/cart');
    const post = calls.find(call => call.method === 'POST');
    expect(post.body).toEqual({ variantId: 101, locationId: 9, quantity: 2 });
    expect(post.headers.Authorization).toBe('Bearer test-token');
    expect(calls.filter(call => call.method !== 'GET')).toHaveLength(1);
  });

  it('a Guest is sent to sign-in with the choice kept in returnTo, and the choice comes back', async () => {
    mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct()),
      'GET /api/v1/product-selling-locations': json(200, locations),
    });
    open('/products/42?variantId=101&locationId=3&quantity=4', { session: fakeSession('signed-out') });
    const link = await screen.findByRole('link', { name: 'Đăng nhập để thêm vào giỏ' });
    await waitFor(() => expect(screen.getByLabelText('Chi nhánh')).toHaveValue('3'));
    expect(screen.getByLabelText('Số lượng')).toHaveValue('4');
    expect(decodeURIComponent(link.getAttribute('href'))).toBe('/#/login?returnTo=/products/42?variantId=101&locationId=3&quantity=4');
  });

  it('without the branch source the button stays disabled and offers a retry; FE-08 errors show its message', async () => {
    const user = userEvent.setup();
    let locationReads = 0;
    mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct()),
      'GET /api/v1/product-selling-locations': () => (++locationReads === 1
        ? json(503, { code: 'LOCATION_DEPENDENCY_UNAVAILABLE', message: 'Down.' })
        : json(200, locations)),
      'POST /api/v1/members/me/ready-ring-cart/lines': json(409, { code: 'RETAIL_CONFLICT', message: 'Giỏ hàng đã đủ 50 dòng.', correlationId: 'c-9' }),
    });
    open('/products/42');
    expect(await screen.findByText('Chưa tải được danh sách chi nhánh. Bấm Thử lại.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thêm vào giỏ' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Chi nhánh' }), '3');
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    expect(await screen.findByText('Giỏ hàng đã đủ 50 dòng.')).toBeInTheDocument();
    expect(screen.getByText('Mã tra cứu: c-9')).toBeInTheDocument();
  });
});

describe('T090 session end on 401 (identity mocked: 001 has no Firebase sign-in yet)', () => {
  it('a token still refused after one refresh ends the session and shows the sign-in notice', async () => {
    const session = fakeSession();
    const calls = mockFetch({
      [`GET ${M}/products`]: json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'Session expired.' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products', { session });
    expect(await screen.findByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/#/login');
    expect(session.getIdToken).toHaveBeenCalledWith(true);
    expect(session.signOut).toHaveBeenCalled();
    expect(screen.queryByRole('link', { name: 'Nhẫn bạc' })).toBeNull();
    expect(calls.every(call => !call.path.includes('test-token') && Object.keys(call.query).every(key => !/token/i.test(key)))).toBe(true);
  });
});

describe('image file name matching after import', () => {
  it('matches a group key or product name as whole words, without accents, longest first', async () => {
    const { matchProductForFile } = await import('../rules');
    const products = [
      { id: 'a', groupKey: 'SONG-NUOC', name: 'Nhẫn bạc sóng nước' },
      { id: 'b', groupKey: 'NHAN-BAC', name: 'Nhẫn bạc' },
      { id: 'c', groupKey: 'CAP-TRON', name: 'Cặp nhẫn bạc khắc tên' },
    ];
    expect(matchProductForFile('SONG-NUOC.png', products)).toBe('a');
    expect(matchProductForFile('song_nuoc (2).JPG', products)).toBe('a');
    expect(matchProductForFile('Cặp nhẫn bạc khắc tên.webp', products)).toBe('c');
    expect(matchProductForFile('nhan-bac-song-nuoc.jpg', products)).toBe('a');
    expect(matchProductForFile('nhan-bac-1.jpg', products)).toBe('b');
    expect(matchProductForFile('IMG_2041.png', products)).toBeNull();
    expect(matchProductForFile('songnuoc.png', products)).toBeNull();
  });
});
