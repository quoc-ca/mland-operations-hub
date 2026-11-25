import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { actionResult, categoryPage, managementProduct, publicProduct, signInAs } from './fixtures';

// Phase 30 Convergence (2026-11-23): T220, T221, T225, T228–T232.
const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const LOCATIONS = 'GET /api/v1/workshop-locations';
const HK = { id: 1, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true };
const TH = { id: 2, code: 'HN-TAYHO', name: 'Tây Hồ', address: 'Hà Nội', active: true };
const COUNTS = { all: '9', draft: '2', pending: '1', rejected: '1', approved: '1', published: '3', hidden: '1', pricePending: '2', priceDraft: '1', priceRejected: '3' };
const page = items => ({ items, page: 0, pageSize: 20, totalElements: String(items.length) });
const open = path => renderRoutes(productManagementRoutes, { path });
const listCalls = calls => calls.filter(call => call.path === `${M}/products`);
const proposalResult = (id, version) => ({ commandId: 'c', resourceId: id, resourceType: 'PRICE_PROPOSAL', status: 'COMMITTED', version, changed: true });
const applied = (id, variantId, price, base = 0) => ({ id, variantId, revision: 1, version: 3, state: 'APPROVED', effective: true, proposedPrice: price, approvedPrice: price, baseAppliedSequence: base, applied: true });

beforeEach(() => {
  clearRoleCache();
  signInAs('STAFF');
  vi.stubGlobal('scrollTo', vi.fn());
});
afterEach(() => vi.unstubAllGlobals());

const sellingAtTwoBranches = (overrides = {}) => {
  const base = managementProduct();
  return managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true }, { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }],
    variants: [{ ...base.variants[0], branchStocks: [
      { locationId: '1', remainingUnsold: '4', saleEnabled: true, version: 0, availableQuantity: '4' },
      { locationId: '2', remainingUnsold: '5', saleEnabled: true, version: 0, availableQuantity: '5' },
    ] }],
    ...overrides,
  });
};

describe('T220 a failed row action says why', () => {
  it('reads the list again after a 409 so the row shows the latest state, and names the conflict', async () => {
    const user = userEvent.setup();
    let stopped = false;
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]),
      [`GET ${M}/products`]: () => json(200, page([sellingAtTwoBranches(stopped ? {
        branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: false }, { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }],
      } : {})])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
      // Someone else stopped selling at Hoàn Kiếm meanwhile.
      [`PUT ${M}/products/42/branches/1/sale-status`]: () => { stopped = true; return json(409, { code: 'STALE_VERSION', message: 'Conflict' }); },
    });
    open('/catalogue-management/products');
    await user.click(await screen.findByRole('button', { name: 'Ngừng bán tại chi nhánh' }));
    expect(await screen.findByText(/Danh sách đã tải lại theo trạng thái mới nhất./)).toBeInTheDocument();
    expect(screen.queryByText(/Mở chi tiết để xem còn thiếu gì/)).toBeNull();
    await waitFor(() => expect(listCalls(calls).length).toBeGreaterThanOrEqual(2));
    expect(await screen.findByRole('button', { name: 'Mở bán lại tại chi nhánh' })).toBeInTheDocument();
  });

  it('names a refused permission and a closed branch instead of the generic hint', async () => {
    const user = userEvent.setup();
    let answer = json(403, { code: 'CATALOGUE_ACCESS_DENIED', message: 'Denied.' });
    mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]),
      [`GET ${M}/products`]: json(200, page([sellingAtTwoBranches()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/products/42/branches/1/sale-status`]: () => answer,
    });
    open('/catalogue-management/products');
    await user.click(await screen.findByRole('button', { name: 'Ngừng bán tại chi nhánh' }));
    expect(await screen.findByText('Tài khoản của bạn không có quyền thực hiện thao tác này.')).toBeInTheDocument();

    answer = json(404, { code: 'BRANCH_NOT_FOUND', message: 'Gone.' });
    await user.click(screen.getByRole('button', { name: 'Ngừng bán tại chi nhánh' }));
    expect(await screen.findByText('Chi nhánh không tồn tại hoặc đã ngừng hoạt động.')).toBeInTheDocument();
  });
});

describe('T231 the Staff list waits for its default branch', () => {
  it('never reads every branch before the Staff branch is known', async () => {
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]),
      [`GET ${M}/products`]: json(200, page([sellingAtTwoBranches()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    await screen.findByRole('link', { name: 'Nhẫn bạc' });
    const reads = [...listCalls(calls), ...calls.filter(call => call.path === `${M}/products/tab-counts`)];
    expect(reads.length).toBeGreaterThan(0);
    expect(reads.every(call => call.query.locationId === '1')).toBe(true);
  });
});

describe('T232 list tabs follow the tabs pattern', () => {
  it('has one tab stop, a labelled panel, and moves with the arrow keys', async () => {
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/products`]: json(200, page([sellingAtTwoBranches()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    const all = await screen.findByRole('tab', { name: 'Tất cả 9' });
    expect(all).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('tab', { name: 'Bản nháp 2' })).toHaveAttribute('tabindex', '-1');
    expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', all.id);
    expect(all).toHaveAttribute('aria-controls', screen.getByRole('tabpanel').id);

    all.focus();
    fireEvent.keyDown(all, { key: 'ArrowRight' });
    await waitFor(() => expect(screen.getByRole('tab', { name: 'Bản nháp 2' })).toHaveAttribute('aria-selected', 'true'));
    expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'Bản nháp 2' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toMatchObject({ tab: 'DRAFT' }));
    fireEvent.keyDown(document.activeElement, { key: 'Home' });
    await waitFor(() => expect(screen.getByRole('tab', { name: 'Tất cả 9' })).toHaveAttribute('aria-selected', 'true'));
  });
});

// Published product with more than three sizes: everything settled folds.
const sizeWith = (id, size, proposals, extra = {}) => ({ ...managementProduct().variants[0], id, size, sku: `SP000042-S${size}`, appliedPrice: '200000', appliedSequence: 1, proposals, ...extra });
const manySizes = (fourth = [applied('1041', '104', '200000')]) => managementProduct({
  published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
  reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
  variants: [
    sizeWith('101', 7, [applied('1011', '101', '200000')]), sizeWith('102', 8, [applied('1021', '102', '200000')]),
    sizeWith('103', 9, [applied('1031', '103', '200000')]), sizeWith('104', 10, fourth),
  ],
});
const sizeToggle = size => screen.getByRole('button', { name: `Size ${size}` });

describe('T225 / T232 price section', () => {
  it('opens the size named by ?priceSize (the Đổi giá link of a rejected price) and keeps the others folded', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/products/42`]: json(200, manySizes()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42?priceSize=102');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });
    expect(sizeToggle(8)).toHaveAttribute('aria-expanded', 'true');
    expect(sizeToggle(7)).toHaveAttribute('aria-expanded', 'false');
    expect(sizeToggle(9)).toHaveAttribute('aria-expanded', 'false');
  });

  it('describes a folded section by its summary and points aria-controls at the body only while it exists', async () => {
    const user = userEvent.setup();
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/products/42`]: json(200, manySizes()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    const toggle = await screen.findByRole('button', { name: 'Đề xuất giá' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(toggle).not.toHaveAttribute('aria-controls');
    expect(toggle).toHaveAccessibleDescription('4 size');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-controls', 'price-title-body');
    expect(document.getElementById('price-title-body')).not.toBeNull();
    expect(toggle).not.toHaveAttribute('aria-describedby');
  });

  it('gives a Manager the decision form and no Đổi giá on a size with a waiting Staff proposal (FR-066)', async () => {
    const waiting = { id: '1049', variantId: '101', revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '210000', baseAppliedSequence: 1, applied: false };
    const product = managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [sizeWith('101', 12, [applied('1011', '101', '200000'), waiting])],
    });
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/products/42`]: json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    const section = (await screen.findByRole('heading', { name: 'Đề xuất giá' })).closest('section');
    expect(await within(section).findByRole('button', { name: 'Duyệt' })).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: 'Đổi giá' })).toBeNull();
  });

  it('offers Áp dụng giá only for an approved revision still waiting to be applied (T228, FR-067)', async () => {
    // Revision 3 was approved on applied sequence 1, then another price was applied (sequence 2): it is superseded.
    const superseded = { id: '503', variantId: '101', revision: 3, version: 2, state: 'APPROVED', effective: true, proposedPrice: '230000', approvedPrice: '230000', baseAppliedSequence: 1, applied: false };
    const product = waitingBase => managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [sizeWith('101', 12, [applied('1011', '101', '200000'), { ...superseded, baseAppliedSequence: waitingBase }], { appliedSequence: 2 })],
    });
    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/products/42`]: json(200, product(1)), [`GET ${M}/categories`]: json(200, categoryPage) });
    const { unmount } = open('/catalogue-management/products/42');
    let section = (await screen.findByRole('heading', { name: 'Đề xuất giá' })).closest('section');
    expect(await within(section).findByText('Đã thay thế')).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: /Áp dụng giá/ })).toBeNull();
    unmount();

    mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/products/42`]: json(200, product(2)), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    section = (await screen.findByRole('heading', { name: 'Đề xuất giá' })).closest('section');
    expect(await within(section).findByRole('button', { name: /Áp dụng giá đã duyệt 230.000/ })).toBeInTheDocument();
  });
});

describe('T230 the approved-review hint follows the role', () => {
  const approvedNeverPublished = managementProduct({
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 1, proposals: [applied('500', '101', '1250000')] }],
  });
  const openAs = role => {
    mockFetch({ [PROFILE]: json(200, { role }), [LOCATIONS]: json(200, [HK]), [`GET ${M}/products/42`]: json(200, approvedNeverPublished), [`GET ${M}/categories`]: json(200, categoryPage) });
    return open('/catalogue-management/products/42');
  };

  it('tells Staff the product waits for a Manager to publish it, not to press Công khai', async () => {
    openAs('STAFF');
    expect(await screen.findByText('Hồ sơ đã được duyệt và giá đã áp dụng. Sản phẩm chờ Manager công khai.')).toBeInTheDocument();
    expect(screen.queryByText(/Bấm Công khai khi muốn mở bán/)).toBeNull();
  });

  it('gives a Manager the publish action without the Staff wording', async () => {
    openAs('MANAGER');
    expect((await screen.findAllByRole('button', { name: /Công khai/ })).length).toBeGreaterThan(0);
    expect(screen.queryByText(/chờ Manager công khai/)).toBeNull();
  });
});

describe('T221 / T225 adding sizes', () => {
  const size12 = { ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 1, proposals: [applied('500', '101', '1250000')],
    branchStocks: [{ locationId: '2', remainingUnsold: '2', saleEnabled: true, version: 0 }] };
  const atTayHo = managementProduct({
    published: true, firstPublishedAt: '2026-11-01T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    branches: [{ locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }], variants: [size12],
  });
  const withSize8 = { ...atTayHo, version: 4, variants: [{
    id: '108', size: 8, sku: 'SP000042-S08', remainingUnsold: '2', validHeldQuantity: '0', availableQuantity: '2', saleEnabled: true, appliedSequence: 0, version: 0,
    proposals: [{ id: '608', variantId: '108', revision: 1, version: 0, state: 'DRAFT', effective: true, proposedPrice: '950000', baseAppliedSequence: 0, applied: false }],
    branchStocks: [{ locationId: '1', remainingUnsold: '2', saleEnabled: true, version: 0 }],
  }, size12] };

  it('lets a Manager adding a branch with a new size leave its price to be applied later (T221)', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    let added = false;
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }), [LOCATIONS]: json(200, [HK, TH]), [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products/42`]: () => json(200, added ? withSize8 : atTayHo),
      [`POST ${M}/products/42/branches`]: () => { added = true; return json(200, actionResult('42', 'product.branch.add')); },
      [`POST ${M}/price-proposals/608/submissions`]: json(200, proposalResult('608', 1)),
      [`POST ${M}/price-proposals/608/decisions`]: json(200, proposalResult('608', 2)),
      [`POST ${M}/price-proposals/608/applications`]: json(200, proposalResult('608', 3)),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('button', { name: 'Thêm chi nhánh bán' }));
    expect(screen.queryByLabelText(/Áp dụng giá ngay sau khi duyệt/)).toBeNull();
    await user.click(screen.getByRole('checkbox', { name: /^8/ }));
    const applyNow = screen.getByLabelText(/Áp dụng giá ngay sau khi duyệt/);
    expect(applyNow).toBeChecked();
    await user.click(applyNow);
    await user.type(screen.getByLabelText('Giá bán size 8'), '950000');
    await user.click(screen.getByRole('button', { name: 'Lưu chi nhánh' }));

    await waitFor(() => expect(calls.some(call => call.path.endsWith('/608/decisions'))).toBe(true));
    expect(calls.some(call => call.path.endsWith('/608/applications'))).toBe(false);
  });

  it('says the size was added but its price is not finished when sending the price fails (FR-069)', { timeout: 20000 }, async () => {
    const user = userEvent.setup();
    const sellingAtHk = { ...atTayHo, branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true }],
      variants: [{ ...size12, branchStocks: [{ locationId: '1', remainingUnsold: '2', saleEnabled: true, version: 0 }] }] };
    let added = false;
    mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]), [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products/42`]: () => json(200, added ? { ...withSize8, branches: sellingAtHk.branches } : sellingAtHk),
      [`POST ${M}/products/42/branches/1/sizes`]: () => { added = true; return json(200, actionResult('42', 'product.size.add')); },
      [`POST ${M}/price-proposals/608/submissions`]: json(500, { code: 'INTERNAL_ERROR', message: 'Boom.', retryable: false }),
    });
    open('/catalogue-management/products/42');
    await user.click(await screen.findByRole('tab', { name: /Hoàn Kiếm/ }));
    await user.click(screen.getByRole('button', { name: 'Thêm size' }));
    const form = screen.getByRole('form', { name: 'Thêm size tại Hoàn Kiếm' });
    await user.click(within(form).getByRole('checkbox', { name: /^8/ }));
    await user.type(within(form).getByLabelText('Giá đề xuất size 8'), '950000');
    await user.click(within(form).getByRole('button', { name: 'Thêm size' }));
    expect((await screen.findAllByText(/nhưng chưa xử lí xong giá của size 8/)).length).toBeGreaterThan(0);
  });

  it('hides Thêm size when the branch already sells all 25 sizes', async () => {
    const all = Array.from({ length: 25 }, (_, index) => sizeWith(String(200 + index), 5 + index, [applied(`9${index}`, String(200 + index), '200000')],
      { branchStocks: [{ locationId: '1', remainingUnsold: '1', saleEnabled: true, version: 0 }] }));
    const full = managementProduct({
      published: true, firstPublishedAt: '2026-11-01T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true }], variants: all,
    });
    const user = userEvent.setup();
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, full) });
    open('/catalogue-management/products/42');
    const toggle = await screen.findByRole('button', { name: 'Size và số lượng' });
    if (toggle.getAttribute('aria-expanded') === 'false') await user.click(toggle);
    await user.click(await screen.findByRole('tab', { name: /Hoàn Kiếm/ }));
    expect(screen.queryByRole('button', { name: 'Thêm size' })).toBeNull();
  });
});

describe('T225 rejected price row', () => {
  it('keeps the new price as an unsent draft and says so when submitting it fails', async () => {
    const user = userEvent.setup();
    const product = managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [{ ...managementProduct().variants[0], appliedPrice: '590000', appliedSequence: 1, proposals: [
        { id: '604', variantId: '101', revision: 2, version: 2, state: 'REJECTED', effective: true, proposedPrice: '700000', baseAppliedSequence: 1, applied: false, reason: 'Cao quá' },
      ] }],
    });
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'STAFF' }), [LOCATIONS]: json(200, [HK]),
      [`POST ${M}/variants/101/price-proposals`]: json(201, proposalResult('606', 0)),
      [`POST ${M}/price-proposals/606/submissions`]: json(500, { code: 'INTERNAL_ERROR', message: 'Boom.', retryable: false }),
      [`GET ${M}/products`]: json(200, page([product])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_REJECTED&locationId=all');
    const table = await screen.findByRole('table', { name: 'Bị từ chối' });
    await user.click(within(table).getByRole('button', { name: 'Đề xuất giá mới' }));
    await user.type(within(table).getByLabelText('Giá bán mới (VND)'), '650000');
    const lists = listCalls(calls).length;
    await user.click(within(table).getByRole('button', { name: 'Gửi duyệt giá' }));
    expect(await screen.findByText(/Đã lưu nháp giá 650.000\s?₫ cho size 12/)).toBeInTheDocument();
    await waitFor(() => expect(listCalls(calls).length).toBeGreaterThan(lists));
  });
});

describe('T229 public detail starts on the first image of every product', () => {
  it('goes back to the first image when another product opens', async () => {
    const user = userEvent.setup();
    const images = id => [
      { id: `${id}1`, url: `/api/v1/product-media/${id}1`, altText: `Ảnh 1 của ${id}`, position: 0 },
      { id: `${id}2`, url: `/api/v1/product-media/${id}2`, altText: `Ảnh 2 của ${id}`, position: 1 },
    ];
    mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct({ id: '42', images: images('42') })),
      'GET /api/v1/products/43': json(200, publicProduct({ id: '43', name: 'Nhẫn khác', images: images('43') })),
    });
    const { router } = open('/products/42');
    await user.click(await screen.findByRole('button', { name: 'Ảnh sau' }));
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    await act(() => router.navigate('/products/43'));
    await waitFor(() => expect(document.querySelector('.detail-image')).toHaveAttribute('alt', 'Ảnh 1 của 43'));
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
  });
});
