import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { actionResult, categoryPage, managementProduct, publicProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
// US10: one branch in the account's list is chosen by default on the create form.
const BRANCHES = { 'GET /api/v1/workshop-locations': json(200, [{ id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true }]) };
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
    const calls = mockFetch({ ...BRANCHES,
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products`]: json(400, { code: 'VALIDATION_ERROR', message: 'Invalid.', correlationId: 'corr-1', fieldErrors: [{ field: 'variants[0].proposedPrice', message: 'Must be a positive amount.' }] }),
    });
    open('/catalogue-management/products/new');
    await user.type(await screen.findByLabelText('Tên sản phẩm'), 'Nhẫn mới');
    await user.type(screen.getByLabelText('Mô tả'), 'Mô tả');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.click(screen.getByRole('button', { name: '12' }));
    await user.type(screen.getByLabelText('Giá đề xuất size 12'), '1250000');
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));

    expect(await screen.findByText('Must be a positive amount.')).toBeInTheDocument();
    expect(screen.getByText('Mã tra cứu: corr-1')).toBeInTheDocument();
    expect(screen.getByLabelText('Tên sản phẩm')).toHaveValue('Nhẫn mới');
    const create = calls.find(call => call.method === 'POST');
    expect(create.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(create.body).toEqual({ name: 'Nhẫn mới', description: 'Mô tả', categoryId: '7', variants: [{ size: 12, proposedPrice: '1250000', quantity: '0' }], images: [],
      branches: [{ locationId: '1', quantities: [{ size: 12, quantity: '0' }] }] });
  });

  it('warns when other products already have the typed name, without blocking the save', async () => {
    const user = userEvent.setup();
    mockFetch({ ...BRANCHES,
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: json(200, { items: [
        managementProduct({ id: '19', name: 'Nhẫn bạc trơn' }),
        managementProduct({ id: '21', name: 'NHẪN BẠC TRƠN' }),
        managementProduct({ id: '22', name: 'Nhẫn bạc trơn', deletedAt: '2026-10-01T00:00:00Z' }),
        managementProduct({ id: '23', name: 'Nhẫn bạc trơn xinh' }),
      ], page: 0, pageSize: 20, totalElements: '4' }),
    });
    open('/catalogue-management/products/new');
    await user.type(await screen.findByLabelText('Tên sản phẩm'), ' Nhẫn bạc trơn ');
    expect(await screen.findByText('Đã có 2 sản phẩm cùng tên:')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Nhẫn bạc trơn (#19)' })).toHaveAttribute('href', '/catalogue-management/products/19');
    expect(screen.getByRole('link', { name: 'NHẪN BẠC TRƠN (#21)' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /#22|#23/ })).toBeNull();
    expect(screen.getByRole('button', { name: /Tạo sản phẩm|Lưu/ })).toBeEnabled();
  });

  it('checks the input before sending anything', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/new');
    await user.click(await screen.findByRole('button', { name: 'Tạo sản phẩm' }));
    expect(screen.getAllByText('Bắt buộc nhập.').length).toBeGreaterThan(0);
    expect(screen.getByText('Cần ít nhất một size.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
  });

  it('sizes are chosen as chips or a range, one price and quantity apply to all, rows stay editable', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...BRANCHES, [`GET ${M}/categories`]: json(200, categoryPage), [`POST ${M}/products`]: json(400, { code: 'VALIDATION_ERROR', message: 'x' }) });
    open('/catalogue-management/products/new');
    const chips = await screen.findByRole('group', { name: 'Chọn size' });
    expect(within(chips).getAllByRole('button')).toHaveLength(25);
    await user.selectOptions(screen.getByLabelText('Từ size'), '14');
    await user.selectOptions(screen.getByLabelText('Đến size'), '12');
    await user.click(screen.getByRole('button', { name: 'Chọn khoảng' }));
    await user.click(within(chips).getByRole('button', { name: '20' }));
    expect(within(chips).getByRole('button', { name: '13' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText(/Áp dụng cho 4 size đã chọn/)).toBeInTheDocument();

    const bulk = screen.getByText(/Áp dụng cho 4 size đã chọn/).parentElement;
    await user.type(within(bulk).getByLabelText('Giá đề xuất (₫)'), '350000');
    await user.type(within(bulk).getByLabelText('Số lượng'), '5');
    await user.click(within(bulk).getByRole('button', { name: 'Áp dụng' }));
    const price14 = screen.getByLabelText('Giá đề xuất size 14');
    await user.clear(price14);
    await user.type(price14, '370000');
    await user.click(within(chips).getByRole('button', { name: '13' }));

    await user.type(screen.getByLabelText('Tên sản phẩm'), 'Nhẫn');
    await user.type(screen.getByLabelText('Mô tả'), 'Bạc');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.click(screen.getByRole('button', { name: 'Tạo sản phẩm' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.variants).toEqual([
      { size: 12, proposedPrice: '350000', quantity: '5' },
      { size: 14, proposedPrice: '370000', quantity: '5' },
      { size: 20, proposedPrice: '350000', quantity: '5' },
    ]);
    expect(calls.find(call => call.method === 'POST').body.branches).toEqual([{ locationId: '1', quantities: [
      { size: 12, quantity: '5' }, { size: 14, quantity: '5' }, { size: 20, quantity: '5' }] }]);
  }, 20000);

  it('a 503 is retried with the same key and then opens the product', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    const calls = mockFetch({ ...BRANCHES,
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products`]: () => (++attempts === 1 ? json(503, { code: 'CATALOGUE_BUSY', message: 'Busy.' }) : json(201, actionResult('42', 'product.create'))),
      [`GET ${M}/products/42`]: json(200, managementProduct()),
    });
    open('/catalogue-management/products/new');
    await user.type(await screen.findByLabelText('Tên sản phẩm'), 'Nhẫn bạc');
    await user.type(screen.getByLabelText('Mô tả'), 'Bạc 925');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await user.click(screen.getByRole('button', { name: '12' }));
    await user.type(screen.getByLabelText('Giá đề xuất size 12'), '1250000');
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
    // Unticked, approving leaves the price to be applied later, as its own step.
    await user.click(within(prices).getByLabelText(/Áp dụng giá ngay sau khi duyệt/));
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
    expect(within(variants).getByText('SP000042-S12')).toBeInTheDocument();
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

describe('bulk price decisions', () => {
  const variant = (id, size, proposal, extra = {}) => ({
    ...managementProduct().variants[0], id, size, sku: `SP000042-S${size}`, appliedPrice: null, ...extra,
    proposals: [{ id: proposal.id, variantId: id, revision: 1, version: 0, effective: true, baseAppliedSequence: 0, applied: false, approvedPrice: null, ...proposal }],
  });

  it('initial review: one amount fills the chosen sizes, the gap to the proposal shows, the whole file is still sent', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ variants: [
        variant('101', 12, { id: '501', state: 'PENDING', proposedPrice: '1250000' }),
        variant('102', 14, { id: '502', state: 'PENDING', proposedPrice: '1300000' }),
        variant('103', 16, { id: '503', state: 'PENDING', proposedPrice: '1350000' }),
      ] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products/42/review-decisions`]: json(200, actionResult('42', 'product.review.decide')),
    });
    open('/catalogue-management/products/42');
    const review = (await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).closest('section');
    await user.click(within(review).getByRole('button', { name: 'Duyệt' }));
    await user.click(within(review).getByLabelText('Chọn size 14'));
    await user.type(within(review).getByLabelText('Giá duyệt chung (₫)'), '1200000');
    await user.click(within(review).getByRole('button', { name: 'Áp dụng cho 2 size đã chọn' }));

    expect(within(review).getByLabelText('Giá duyệt size 12')).toHaveValue('1200000');
    expect(within(review).getByLabelText('Giá duyệt size 14')).toHaveValue('1300000');
    expect(within(review).getByLabelText('Giá duyệt size 16')).toHaveValue('1200000');
    expect(within(review).getByText('Bằng đề xuất')).toBeInTheDocument();
    await user.click(within(review).getByRole('button', { name: 'Duyệt toàn bộ hồ sơ' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.prices).toEqual([
      { variantId: '101', proposalId: '501', approvedPrice: '1200000' },
      { variantId: '102', proposalId: '502', approvedPrice: '1300000' },
      { variantId: '103', proposalId: '503', approvedPrice: '1200000' },
    ]);
  });

  it('after publication: approves the chosen sizes one by one with their own keys and reports each size', async () => {
    const user = userEvent.setup();
    const published = { published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] };
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ ...published, variants: [
        variant('101', 12, { id: '601', state: 'PENDING', proposedPrice: '1250000' }, { appliedPrice: '1200000' }),
        variant('102', 14, { id: '602', state: 'PENDING', proposedPrice: '1300000' }, { appliedPrice: '1200000' }),
        variant('103', 16, { id: '603', state: 'PENDING', proposedPrice: '1350000' }, { appliedPrice: '1200000' }),
      ] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, actionResult('601', 'price.decide')),
      [`POST ${M}/price-proposals/602/decisions`]: json(409, { code: 'STALE_VERSION', message: 'Stale.' }),
      [`POST ${M}/price-proposals/603/decisions`]: json(200, actionResult('603', 'price.decide')),
    });
    open('/catalogue-management/products/42');
    const batch = (await screen.findByRole('heading', { name: 'Giá đang chờ duyệt (3 size)' })).closest('.batch-prices');

    await user.click(within(batch).getByRole('button', { name: 'Từ chối 3 size đã chọn' }));
    await user.click(within(batch).getByRole('button', { name: 'Xác nhận từ chối 3 size' }));
    expect(within(batch).getByText('Cần nhập lý do.')).toBeInTheDocument();
    await user.click(within(batch).getByRole('button', { name: 'Hủy' }));

    await user.type(within(batch).getByLabelText('Giá duyệt chung (₫)'), '1280000');
    await user.click(within(batch).getByRole('button', { name: 'Áp dụng cho 3 size đã chọn' }));
    await user.click(within(batch).getByLabelText(/Áp dụng giá ngay sau khi duyệt/));
    await user.click(within(batch).getByRole('button', { name: 'Duyệt 3 size đã chọn' }));

    expect(await within(batch).findByText('Kết quả: 2/3 size thành công.')).toBeInTheDocument();
    expect(within(batch).getByText(/Size 14: Dữ liệu đã được người khác thay đổi/)).toBeInTheDocument();
    const decisions = calls.filter(call => call.path.endsWith('/decisions'));
    expect(decisions.map(call => call.path.split('/').at(-2))).toEqual(['601', '602', '603']);
    expect(decisions.map(call => call.body)).toEqual([601, 602, 603].map(() => ({ expectedVersion: 0, decision: 'APPROVE', approvedPrice: '1280000' })));
    expect(new Set(decisions.map(call => call.headers['Idempotency-Key'])).size).toBe(3);
  });

  it('approving several sizes applies each one right after its decision, using the approved revision', async () => {
    const user = userEvent.setup();
    const published = { published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] };
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ ...published, variants: [
        variant('101', 12, { id: '601', state: 'PENDING', proposedPrice: '1250000' }),
        variant('102', 14, { id: '602', state: 'PENDING', proposedPrice: '1300000' }),
      ] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, { ...actionResult('601', 'price.decide'), version: 1 }),
      [`POST ${M}/price-proposals/602/decisions`]: json(200, { ...actionResult('602', 'price.decide'), version: 1 }),
      [`POST ${M}/price-proposals/601/applications`]: json(200, actionResult('601', 'price.apply')),
      [`POST ${M}/price-proposals/602/applications`]: json(200, actionResult('602', 'price.apply')),
    });
    open('/catalogue-management/products/42');
    const batch = (await screen.findByRole('heading', { name: 'Giá đang chờ duyệt (2 size)' })).closest('.batch-prices');
    await user.click(within(batch).getByRole('button', { name: 'Duyệt 2 size đã chọn' }));
    expect(await within(batch).findByText('Đã duyệt và áp dụng giá 2 size.')).toBeInTheDocument();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts.map(call => call.path.replace(M, ''))).toEqual([
      '/price-proposals/601/decisions', '/price-proposals/601/applications', '/price-proposals/602/decisions', '/price-proposals/602/applications',
    ]);
    expect(posts[1].body).toEqual({ expectedVersion: 1 });
  });

  it('initial review: approving the whole file also applies every approved price, then says so', async () => {
    const user = userEvent.setup();
    const stage = { value: 'PENDING' };
    const product = () => managementProduct({
      reviews: [{ id: '301', revision: 1, contentVersion: 1, state: stage.value, effective: true }],
      variants: [
        variant('101', 12, { id: '501', state: stage.value, proposedPrice: '1250000', version: stage.value === 'APPROVED' ? 1 : 0 }),
        variant('102', 14, { id: '502', state: stage.value, proposedPrice: '1300000', version: stage.value === 'APPROVED' ? 1 : 0 }),
      ],
    });
    const calls = mockFetch({
      [`GET ${M}/products/42`]: () => json(200, product()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/products/42/review-decisions`]: () => { stage.value = 'APPROVED'; return json(200, actionResult('42', 'product.review.decide')); },
      [`POST ${M}/price-proposals/501/applications`]: json(200, actionResult('501', 'price.apply')),
      [`POST ${M}/price-proposals/502/applications`]: json(200, actionResult('502', 'price.apply')),
    });
    open('/catalogue-management/products/42');
    const review = (await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).closest('section');
    await user.click(within(review).getByRole('button', { name: 'Duyệt' }));
    await user.click(within(review).getByRole('button', { name: 'Duyệt toàn bộ hồ sơ' }));

    expect(await screen.findAllByText('Đã duyệt hồ sơ và áp dụng giá 2 size. Có thể Công khai sản phẩm.')).toHaveLength(2); // page top and under the section
    const applications = calls.filter(call => call.path.endsWith('/applications'));
    expect(applications.map(call => [call.path.split('/').at(-2), call.body])).toEqual([['501', { expectedVersion: 1 }], ['502', { expectedVersion: 1 }]]);
  });

  it('applies every approved, not yet applied price in one go, sending no amount', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({
        reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
        variants: [
          variant('101', 12, { id: '601', state: 'APPROVED', proposedPrice: '1250000', approvedPrice: '1250000' }),
          variant('102', 14, { id: '602', state: 'APPROVED', proposedPrice: '1300000', approvedPrice: '1280000' }),
        ],
      })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/applications`]: json(200, actionResult('601', 'price.apply')),
      [`POST ${M}/price-proposals/602/applications`]: json(200, actionResult('602', 'price.apply')),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Áp dụng giá đã duyệt cho 2 size' }));
    expect(await screen.findAllByText('Đã áp dụng giá đã duyệt cho 2 size.')).toHaveLength(2);
    const applications = calls.filter(call => call.path.endsWith('/applications'));
    expect(applications).toHaveLength(2);
    expect(applications.map(call => call.body)).toEqual([{ expectedVersion: 0 }, { expectedVersion: 0 }]);
  });

  it('Staff sets one proposed price for a size range: revises drafts, proposes for the rest, then submits each', async () => {
    const user = userEvent.setup();
    const published = { published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] };
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ ...published, variants: [
        variant('101', 12, { id: '601', state: 'DRAFT', proposedPrice: '1250000' }, { appliedPrice: '1200000' }),
        variant('102', 14, { id: '602', state: 'APPROVED', proposedPrice: '1300000', approvedPrice: '1300000', applied: true }, { appliedPrice: '1300000' }),
        variant('103', 20, { id: '603', state: 'APPROVED', proposedPrice: '1400000', approvedPrice: '1400000', applied: true }, { appliedPrice: '1400000' }),
      ] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/revisions`]: json(201, { ...actionResult('611', 'price.revise'), version: 0 }),
      [`POST ${M}/variants/102/price-proposals`]: json(201, { ...actionResult('612', 'price.propose'), version: 0 }),
      [`POST ${M}/price-proposals/611/submissions`]: json(200, actionResult('611', 'price.submit')),
      [`POST ${M}/price-proposals/612/submissions`]: json(200, actionResult('612', 'price.submit')),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Sửa giá đề xuất nhiều size' }));
    const batch = screen.getByRole('heading', { name: 'Sửa giá đề xuất nhiều size' }).closest('.batch-prices');
    await user.selectOptions(within(batch).getByLabelText('Từ size'), '5');
    await user.selectOptions(within(batch).getByLabelText('Đến size'), '19');
    await user.click(within(batch).getByRole('button', { name: 'Chọn khoảng size' }));
    await user.type(within(batch).getByLabelText('Giá đề xuất chung (₫)'), '1350000');
    await user.click(within(batch).getByRole('button', { name: 'Áp dụng cho 2 size đã chọn' }));
    expect(within(batch).getByLabelText('Giá đề xuất mới size 20')).toHaveValue('');
    await user.click(within(batch).getByRole('button', { name: 'Lưu và gửi duyệt 2 size' }));

    // Everything saved: one success line under the buttons; the typed amounts are cleared, so saving again is disabled.
    expect(await within(batch).findByText('Đã gửi duyệt giá 2 size.')).toBeInTheDocument();
    expect(within(batch).getByRole('button', { name: 'Lưu và gửi duyệt 0 size' })).toBeDisabled();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts.map(call => call.path.replace(M, ''))).toEqual([
      '/price-proposals/601/revisions', '/price-proposals/611/submissions', '/variants/102/price-proposals', '/price-proposals/612/submissions',
    ]);
    expect(posts[0].body).toEqual({ expectedVersion: 0, proposedPrice: '1350000' });
    expect(posts[2].body).toEqual({ expectedVersion: 4, proposedPrice: '1350000' });
    expect(posts[1].body).toEqual({ expectedVersion: 0 });
  });

  it('the save buttons stay disabled until a chosen size has a new price', async () => {
    const user = userEvent.setup();
    const published = { published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] };
    mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ ...published, variants: [
        variant('101', 12, { id: '601', state: 'DRAFT', proposedPrice: '1250000' }),
        variant('102', 14, { id: '602', state: 'DRAFT', proposedPrice: '1300000' }),
      ] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Sửa giá đề xuất nhiều size' }));
    expect(screen.getByRole('button', { name: 'Lưu và gửi duyệt 0 size' })).toBeDisabled();
    await user.type(screen.getByLabelText('Giá đề xuất mới size 14'), '1310000');
    expect(screen.getByRole('button', { name: 'Lưu và gửi duyệt 1 size' })).toBeEnabled();
  });
});

describe('bulk quantities', () => {
  const sizes = [['101', 12, '1', '0'], ['102', 14, '2', '0'], ['103', 20, '5', '4']].map(([id, size, remaining, held]) => ({
    ...managementProduct().variants[0], id, size, sku: `SP000042-S${size}`, remainingUnsold: remaining, validHeldQuantity: held,
  }));

  it('adds one restock to a size range, sends only changed sizes, then closes with a notice', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ variants: sizes })),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/variants/101/quantity`]: json(200, actionResult('101', 'variant.quantity')),
      [`PUT ${M}/variants/102/quantity`]: json(200, actionResult('102', 'variant.quantity')),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Sửa số lượng nhiều size' }));
    const batch = screen.getByRole('heading', { name: 'Sửa số lượng nhiều size' }).closest('.batch-prices');
    expect(within(batch).getByRole('button', { name: 'Lưu số lượng 0 size' })).toBeDisabled();
    await user.selectOptions(within(batch).getByLabelText('Từ size'), '5');
    await user.selectOptions(within(batch).getByLabelText('Đến size'), '19');
    await user.click(within(batch).getByRole('button', { name: 'Chọn khoảng size' }));
    await user.type(within(batch).getByLabelText('Số lượng'), '3');
    await user.click(within(batch).getByRole('button', { name: 'Áp dụng cho 2 size đã chọn' }));

    expect(within(batch).getByLabelText('Số lượng mới size 12')).toHaveValue('4');
    expect(within(batch).getByLabelText('Số lượng mới size 14')).toHaveValue('5');
    expect(within(batch).getByLabelText('Số lượng mới size 20')).toHaveValue('5');
    await user.click(within(batch).getByRole('button', { name: 'Lưu số lượng 2 size' }));

    expect(await within(batch).findByText('Đã sửa số lượng 2 size.')).toBeInTheDocument();
    const puts = calls.filter(call => call.method === 'PUT');
    expect(puts.map(call => [call.path.replace(M, ''), call.body])).toEqual([
      ['/variants/101/quantity', { expectedVersion: 4, quantity: '4' }],
      ['/variants/102/quantity', { expectedVersion: 4, quantity: '5' }],
    ]);
  });

  it('refuses a number below the held quantity before sending anything', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ variants: sizes })),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Sửa số lượng nhiều size' }));
    const input = screen.getByLabelText('Số lượng mới size 20');
    await user.clear(input);
    await user.type(input, '3');
    await user.click(screen.getByRole('button', { name: 'Lưu số lượng 1 size' }));
    expect(screen.getByText('Không được thấp hơn số đang giữ (4).')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'PUT')).toBe(false);
  });
});

describe('missing requirements on submit', () => {
  it('without an image nothing is sent: the Images section turns red with what to do, other sections stay normal', async () => {
    const user = userEvent.setup();
    const draft = { reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'DRAFT', effective: true }] };
    const product = { value: managementProduct({ ...draft, images: [] }) };
    const calls = mockFetch({
      [`GET ${M}/products/42`]: () => json(200, product.value),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Gửi duyệt' }));

    const images = screen.getByRole('heading', { name: 'Ảnh' }).closest('section');
    expect(images).toHaveClass('missing');
    expect(within(images).getByRole('alert')).toHaveTextContent('Cần ít nhất một ảnh');
    expect(screen.getByText('Chưa gửi duyệt được, còn thiếu:')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    const prices = screen.getByRole('heading', { name: 'Đề xuất giá' }).closest('section');
    expect(prices).not.toHaveClass('missing');
  });

  it('Session 2026-11-04: a refused submit replaces the "created" notice instead of showing next to it', async () => {
    const user = userEvent.setup();
    const draft = { reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'DRAFT', effective: true }] };
    mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct({ ...draft, images: [] })),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open({ pathname: '/catalogue-management/products/42', state: { notice: { key: 'create.success', params: { name: 'Nhẫn bạc' } } } });
    expect(await screen.findByText(/Đã tạo sản phẩm "Nhẫn bạc"/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Gửi duyệt' }));
    expect(screen.getByText('Chưa gửi duyệt được, còn thiếu:')).toBeInTheDocument();
    expect(screen.queryByText(/Đã tạo sản phẩm "Nhẫn bạc"/)).not.toBeInTheDocument();
  });

  it('Session 2026-11-04: a field refused before sending also clears the previous notice', async () => {
    const user = userEvent.setup();
    mockFetch({
      [`GET ${M}/products/42`]: json(200, managementProduct()),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open({ pathname: '/catalogue-management/products/42', state: { notice: { key: 'create.success', params: { name: 'Nhẫn bạc' } } } });
    const content = (await screen.findByRole('heading', { name: 'Nội dung' })).closest('section');
    await user.click(within(content).getByRole('button', { name: 'Sửa' }));
    await user.clear(within(content).getByLabelText('Tên sản phẩm'));
    await user.click(within(content).getByRole('button', { name: 'Lưu' }));
    expect(within(content).getByText('Bắt buộc nhập.')).toBeInTheDocument();
    expect(screen.queryByText(/Đã tạo sản phẩm "Nhẫn bạc"/)).not.toBeInTheDocument();
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
    expect(await screen.findByText('Đã xóa sản phẩm "Nhẫn bạc".')).toBeInTheDocument();
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
    expect(await screen.findAllByText(/^Đã ngừng hoạt động danh mục "/)).toHaveLength(2);
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ expectedVersion: 2, active: false });
  });

  it('a new action clears the previous success notice, so a failure never shows next to it', async () => {
    const user = userEvent.setup();
    let creates = 0;
    mockFetch({
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/categories`]: () => (++creates === 1
        ? json(201, actionResult('9', 'category.create'))
        : json(409, { code: 'DUPLICATE_CATEGORY_NAME', message: 'Duplicate.', correlationId: 'c-1' })),
    });
    open('/catalogue-management/categories');
    const name = await screen.findByLabelText('Tên danh mục');
    await user.type(name, 'Nhẫn mới');
    await user.click(screen.getByRole('button', { name: 'Tạo danh mục' }));
    expect(await screen.findAllByText('Đã tạo danh mục "Nhẫn mới".')).toHaveLength(2);

    await user.type(name, 'NHẪN MỚI');
    await user.click(screen.getByRole('button', { name: 'Tạo danh mục' }));
    expect(await screen.findByText('Tên danh mục đã tồn tại.')).toBeInTheDocument();
    expect(screen.queryByText('Đã tạo danh mục "Nhẫn mới".')).not.toBeInTheDocument();

    await user.clear(name);
    await user.click(screen.getByRole('button', { name: 'Tạo danh mục' }));
    expect(creates).toBe(2);
    expect(screen.queryByText(/^Đã tạo danh mục/)).not.toBeInTheDocument();
  });
});

describe('T101 import', () => {
  it('shows row errors of a rejected file', async () => {
    const user = userEvent.setup();
    mockFetch({ ...BRANCHES,
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

  it('explains the Size column and shows each previewed product with its sizes folded into ranges', async () => {
    const user = userEvent.setup();
    mockFetch({ ...BRANCHES,
      [`POST ${M}/product-imports`]: json(201, {
        batchId: 'b2', fileName: 'a.xlsx', state: 'VALIDATED', version: 0, rowCount: 2, productCount: 1, variantCount: 7, errorCount: 0,
        expiresAt: '2026-11-01T10:00:00Z', preview: [{ groupKey: 'R1', name: 'Nhẫn khoảng size', sizes: [5, 6, 7, 8, 9, 14, 12] }],
      }),
    });
    const { container } = open('/catalogue-management/imports');
    expect(screen.getByText(/Cột Size nhận một size \(12\), một khoảng \(12-20\)/)).toBeInTheDocument();
    await user.upload(container.querySelector('input[type=file]'), new File(['x'], 'a.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    expect(await screen.findByText(/7 size: 5–9, 12, 14/)).toBeInTheDocument();
  });

  it('points out products of the file whose name an existing product has, and still allows the import', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...BRANCHES,
      [`POST ${M}/product-imports`]: json(201, {
        batchId: 'b3', fileName: 'a.xlsx', state: 'VALIDATED', version: 0, rowCount: 2, productCount: 2, variantCount: 2, errorCount: 0,
        expiresAt: '2026-11-01T10:00:00Z',
        preview: [{ groupKey: 'A', name: 'Nhẫn bạc trơn', sizes: [10] }, { groupKey: 'B', name: 'Nhẫn mới tinh', sizes: [11] }],
      }),
      [`GET ${M}/products`]: call => json(200, call.query.q === 'Nhẫn bạc trơn'
        ? { items: [managementProduct({ id: '19', name: ' nhẫn bạc  TRƠN ' }), managementProduct({ id: '20', name: 'Nhẫn bạc trơn xinh' })], page: 0, pageSize: 20, totalElements: '2' }
        : { items: [], page: 0, pageSize: 20, totalElements: '0' }),
    });
    const { container } = open('/catalogue-management/imports');
    await user.upload(container.querySelector('input[type=file]'), new File(['x'], 'a.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
    await user.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    expect(await screen.findByText(/1 sản phẩm trong file trùng tên với sản phẩm đã có/)).toBeInTheDocument();
    const row = screen.getByText('Nhẫn bạc trơn', { selector: 'strong' }).closest('li');
    expect(within(row).getByText('Trùng tên với')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: /#19/ })).toHaveAttribute('href', '/catalogue-management/products/19');
    expect(within(row).queryByRole('link', { name: /#20/ })).toBeNull();
    expect(within(screen.getByText('Nhẫn mới tinh', { selector: 'strong' }).closest('li')).queryByText('Trùng tên với')).toBeNull();
    expect(screen.getByRole('button', { name: 'Xác nhận import' })).toBeEnabled();
    expect(calls.filter(call => call.path === `${M}/products`).map(call => call.query.q).sort()).toEqual(['Nhẫn bạc trơn', 'Nhẫn mới tinh']);
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
    const calls = mockFetch({ ...BRANCHES,
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
    expect(screen.getByText('Đã lưu ảnh thành công cho 1 sản phẩm.')).toBeInTheDocument();
    expect(screen.getByText(/Chưa lưu được ảnh cho 1 sản phẩm/)).toBeInTheDocument();
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

  it('takes an automatic discount off the shown price and badges vouchers (006 offers)', async () => {
    const promotions = { items: [
      { id: '1', kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', code: null, name: 'Giảm 11.11', minimumEligibleAmount: '0',
        discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '500000' }, targets: { productIds: ['42'] } },
      { id: '2', kind: 'VOUCHER', scope: 'PRODUCT', code: 'MLAND20', name: 'Mã 20k', minimumEligibleAmount: '1000000',
        discount: { kind: 'FIXED', amount: '20000', maxDiscountAmount: '20000' }, targets: { variantIds: ['101'] } },
    ], nextCursor: null };
    const calls = mockFetch({
      'GET /api/v1/products': json(200, { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      'GET /api/v1/product-categories': json(200, categoryPage),
      'GET /api/v1/promotions': json(200, promotions),
      'GET /api/v1/products/42': json(200, publicProduct()),
    });
    open('/products');
    expect(await screen.findByText('1.125.000 ₫ – 1.170.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('1.250.000 ₫ – 1.300.000 ₫').tagName).toBe('S');
    expect(screen.getByText('Giảm 10%')).toBeInTheDocument();
    // The voucher covers only size 12 of the two sizes: the badge says so.
    expect(screen.getByText('Mã giảm 20.000 ₫ · size 12')).toBeInTheDocument();
    expect(calls.find(call => call.path === '/api/v1/promotions').query).toEqual({ scope: 'PRODUCT', limit: '100' });
  });

  it('detail lists each offer and says which sizes a partial one covers', async () => {
    mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct()),
      'GET /api/v1/promotions': json(200, { items: [
        { id: '3', kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', code: null, name: 'Giảm size 14', minimumEligibleAmount: '0',
          discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '500000' }, targets: { variantIds: ['102'] } },
      ], nextCursor: null }),
    });
    open('/products/42');
    expect(await screen.findByText('Chỉ áp dụng cho size 14.')).toBeInTheDocument();
    expect(screen.getByText('Giảm 10% · size 14')).toBeInTheDocument();
    expect(screen.getByText('1.170.000 ₫')).toBeInTheDocument();
  });

  it('shows normal prices when offers cannot be read', async () => {
    mockFetch({
      'GET /api/v1/products': json(200, { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      'GET /api/v1/product-categories': json(200, categoryPage),
      'GET /api/v1/promotions': json(503, { code: 'SERVICE_UNAVAILABLE', message: 'Down.' }),
    });
    open('/products');
    expect(await screen.findByText('1.250.000 ₫ – 1.300.000 ₫')).toBeInTheDocument();
    expect(screen.queryByText(/Giảm|Mã giảm/)).toBeNull();
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

    expect(await screen.findAllByText(/^Đã lưu \d+ ảnh của sản phẩm\./)).toHaveLength(2);
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
    expect(await screen.findAllByText(/^Đã áp dụng giá .+ cho size 12\./)).toHaveLength(2);
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
    // Branches load after the page: wait for the option, or the choice races the list under a loaded test run.
    await screen.findByRole('option', { name: /MLAND Thủ Đức/ });
    await user.selectOptions(screen.getByLabelText('Chi nhánh'), '9');
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

  it('the cart button of a list card adds without leaving the list', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/products': json(200, { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      'GET /api/v1/product-categories': json(200, categoryPage),
      'GET /api/v1/product-selling-locations': json(200, locations),
      'POST /api/v1/members/me/ready-ring-cart/lines': json(201, { id: 'cart' }),
    });
    const { router } = open('/products');
    expect(await screen.findByText('Còn hàng')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thêm Nhẫn bạc vào giỏ hàng' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vào giỏ: Nhẫn bạc' });
    await user.selectOptions(await within(dialog).findByLabelText('Chi nhánh'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Thêm vào giỏ' }));
    expect(await screen.findByText('Đã thêm "Nhẫn bạc" vào giỏ hàng.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(router.state.location.pathname).toBe('/products');
    expect(calls.find(call => call.method === 'POST').body).toEqual({ variantId: 101, locationId: 3, quantity: 1 });
  });

  it('a Guest using the list cart button comes back to the product page after sign-in', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/products': json(200, { items: [publicProduct()], page: 0, pageSize: 20, totalElements: '1' }),
      'GET /api/v1/product-categories': json(200, categoryPage),
      'GET /api/v1/product-selling-locations': json(200, locations),
    });
    open('/products', { session: fakeSession('signed-out') });
    await user.click(await screen.findByRole('button', { name: 'Thêm Nhẫn bạc vào giỏ hàng' }));
    const link = await screen.findByRole('link', { name: 'Đăng nhập để thêm vào giỏ' });
    expect(decodeURIComponent(link.getAttribute('href'))).toContain('/products/42?variantId=101');
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
    expect(decodeURIComponent(link.getAttribute('href'))).toBe('/login?returnTo=/products/42?variantId=101&locationId=3&quantity=4');
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
    expect(await screen.findByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login?returnTo=%2Fcatalogue-management%2Fproducts');
    expect(session.getIdToken).toHaveBeenCalledWith(true);
    expect(session.signOut).toHaveBeenCalled();
    expect(screen.queryByRole('link', { name: 'Nhẫn bạc' })).toBeNull();
    expect(calls.every(call => !call.path.includes('test-token') && Object.keys(call.query).every(key => !/token/i.test(key)))).toBe(true);
  });
});

describe('image file name matching after import', () => {
  it('matches a group key or product name as whole words, without accents, longest first', async () => {
    const { matchProductForFile } = await import('../domain/productRules');
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
