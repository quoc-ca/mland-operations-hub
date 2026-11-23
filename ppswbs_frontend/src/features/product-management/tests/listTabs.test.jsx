import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct } from './fixtures';
import { beforeEach as beforeEachSignIn } from 'vitest';
import { signInAs } from './fixtures';

// RouteAccessGuard (T219) reads the 001 Account bootstrap: an active Staff unless a test says otherwise.
beforeEachSignIn(() => { signInAs('STAFF'); });

// FR-064 / FR-065 (Session 2026-11-22): status tabs with counts, filters that apply at once, the Manager's price tab.
const M = '/api/v1/catalogue-management';
const AS_MANAGER = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }) };
const AS_STAFF = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }) };
const COUNTS = { all: '9', draft: '2', pending: '1', rejected: '1', approved: '1', published: '3', hidden: '1', pricePending: '2', priceDraft: '1', priceRejected: '3' };
const STAFF_COUNTS = COUNTS;
const page = items => ({ items, page: 0, pageSize: 20, totalElements: String(items.length) });
const open = path => renderRoutes(productManagementRoutes, { path });
const listCalls = calls => calls.filter(call => call.path === `${M}/products`);

beforeEach(() => clearRoleCache());
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('FR-064 status tabs', () => {
  it('shows every tab with its count, sends the chosen tab and goes back to the first page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products`]: json(200, page([managementProduct()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?page=2');
    const tabs = await screen.findByRole('tablist', { name: 'Trạng thái sản phẩm' });
    expect(await within(tabs).findByRole('tab', { name: 'Chờ duyệt giá 2' })).toBeInTheDocument();
    expect(within(tabs).getByRole('tab', { name: 'Tất cả 9' })).toHaveAttribute('aria-selected', 'true');
    // A Manager's work: waiting for review, ready to publish and price proposals are highlighted.
    expect(within(tabs).getByRole('tab', { name: 'Chờ duyệt sản phẩm 1' })).toHaveClass('attention');
    expect(within(tabs).getByRole('tab', { name: 'Chờ công khai 1' })).toHaveClass('attention');
    expect(within(tabs).getByRole('tab', { name: 'Bị từ chối 1' })).not.toHaveClass('attention');

    await user.click(within(tabs).getByRole('tab', { name: 'Đã ẩn 1' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'HIDDEN' }));
    expect(within(tabs).getByRole('tab', { name: 'Đã ẩn 1' })).toHaveAttribute('aria-selected', 'true');
  });

  it('gives Staff the price tab without highlight, and highlights the rejected and ready-to-publish tabs', async () => {
    mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([managementProduct()])),
      [`GET ${M}/products/tab-counts`]: json(200, STAFF_COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    const tabs = await screen.findByRole('tablist');
    expect(await within(tabs).findByRole('tab', { name: 'Bị từ chối 1' })).toHaveClass('attention');
    await waitFor(() => expect(within(tabs).getByRole('tab', { name: 'Chờ công khai 1' })).toHaveClass('attention'));
    expect(within(tabs).getByRole('tab', { name: 'Chờ duyệt sản phẩm 1' })).not.toHaveClass('attention');
    // FR-065: Staff follow their prices, read-only. Session 2026-11-23: the tab counts waiting prices for every role.
    expect(within(tabs).getByRole('tab', { name: 'Chờ duyệt giá 2' })).not.toHaveClass('attention');
  });

  it('shows the empty notice of the tab and keeps the tabs', async () => {
    mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([])),
      [`GET ${M}/products/tab-counts`]: json(200, { ...STAFF_COUNTS, rejected: '0' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=REJECTED');
    expect(await screen.findByText('Không có sản phẩm nào bị từ chối.')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Bị từ chối/ })).toHaveAttribute('aria-selected', 'true');
  });

  it('still lists the products when the counts cannot be read', async () => {
    mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([managementProduct()])),
      [`GET ${M}/products/tab-counts`]: json(503, { code: 'DEPENDENCY_UNAVAILABLE' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    expect(await screen.findByRole('link', { name: 'Nhẫn bạc' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Tất cả' })).toBeInTheDocument();
  });

  it('reads the counts again when the browser tab comes back into view', async () => {
    const calls = mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([])),
      [`GET ${M}/products/tab-counts`]: () => json(200, STAFF_COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    await screen.findByRole('tab', { name: 'Tất cả 9' });
    const before = calls.filter(call => call.path.endsWith('/tab-counts')).length;
    act(() => { document.dispatchEvent(new Event('visibilitychange')); });
    await waitFor(() => expect(calls.filter(call => call.path.endsWith('/tab-counts')).length).toBe(before + 1));
  });
});

describe('FR-064 state column names the tab', () => {
  it('tells a hidden product from one ready to publish, and words the button "Hiện lại" for the hidden one', async () => {
    const ready = managementProduct({ id: '42', name: 'Mới duyệt', reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] });
    const hidden = managementProduct({ id: '43', name: 'Đã gỡ', published: false, firstPublishedAt: '2026-10-30T00:00:00Z',
      reviews: [{ id: '302', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }] });
    const rejected = managementProduct({ id: '44', name: 'Bị trả', reviews: [{ id: '303', revision: 1, contentVersion: 1, state: 'REJECTED', effective: true }] });
    const draft = managementProduct({ id: '45', name: 'Nháp mới', reviews: [] });
    mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products`]: json(200, page([ready, hidden, rejected, draft])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    const row = async name => (await screen.findByRole('link', { name })).closest('[role=row]');

    const readyRow = await row('Mới duyệt');
    await waitFor(() => expect(within(readyRow).getByRole('button', { name: 'Công khai' })).toBeInTheDocument());
    expect(within(readyRow).getByText('Chờ công khai', { selector: '.status' })).toHaveClass('warn');
    const hiddenRow = await row('Đã gỡ');
    expect(within(hiddenRow).getByText('Đã ẩn', { selector: '.status' })).toBeInTheDocument();
    expect(within(hiddenRow).getByRole('button', { name: 'Hiện lại' })).toBeInTheDocument();
    expect(within(hiddenRow).queryByRole('button', { name: 'Công khai' })).not.toBeInTheDocument();
    expect(within(await row('Bị trả')).getByText('Bị từ chối', { selector: '.status' })).toHaveClass('danger');
    expect(within(await row('Nháp mới')).getByText('Bản nháp', { selector: '.status' })).toBeInTheDocument();
  });
});

describe('FR-064 filters apply at once', () => {
  it('filters on a category change with no Apply button, and the counts follow the filters', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([])),
      [`GET ${M}/products/tab-counts`]: json(200, STAFF_COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=DRAFT&page=1');
    await screen.findByRole('option', { name: 'Nhẫn cưới' });
    expect(screen.queryByRole('button', { name: 'Áp dụng' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Công khai')).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Danh mục'), '7');
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'DRAFT', categoryId: '7' }));
    await waitFor(() => expect(calls.filter(call => call.path.endsWith('/tab-counts')).at(-1).query).toEqual({ categoryId: '7' }));

    await user.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'DRAFT' }));
  });

  it('searches once after typing stops, or at once with Enter', async () => {
    const calls = mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([])),
      [`GET ${M}/products/tab-counts`]: json(200, STAFF_COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    const box = await screen.findByLabelText('Tìm kiếm');
    await waitFor(() => expect(listCalls(calls)).toHaveLength(1));
    vi.useFakeTimers();
    fireEvent.change(box, { target: { value: 'n' } });
    fireEvent.change(box, { target: { value: 'nh' } });
    fireEvent.change(box, { target: { value: 'nhẫn' } });
    act(() => { vi.advanceTimersByTime(299); });
    expect(listCalls(calls)).toHaveLength(1);
    act(() => { vi.advanceTimersByTime(1); });
    vi.useRealTimers();
    await waitFor(() => expect(listCalls(calls)).toHaveLength(2));
    expect(listCalls(calls).at(-1).query).toEqual({ q: 'nhẫn' });

    fireEvent.change(box, { target: { value: 'SP000042' } });
    fireEvent.submit(box.closest('form'));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ q: 'SP000042' }));
  });
});

describe('FR-065 price proposals tab', () => {
  const pendingProduct = () => managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 1, proposals: [
      { id: '601', variantId: '101', revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '1350000', baseAppliedSequence: 1, applied: false,
        submittedBy: { kind: 'ACCOUNT', displayName: 'Nhân Viên A' }, submittedAt: '2026-11-22T08:00:00Z' },
    ] }],
  });

  it('lists each waiting size with the decision form and reloads the list and counts after a decision', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products`]: json(200, page([pendingProduct()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, { commandId: 'c', resourceId: '601', status: 'COMMITTED', version: 2, changed: true }),
      [`POST ${M}/price-proposals/601/applications`]: json(200, { commandId: 'c', resourceId: '601', status: 'COMMITTED', version: 3, changed: true }),
    });
    open('/catalogue-management/products?tab=PRICE_PENDING');
    const table = await screen.findByRole('table', { name: 'Chờ duyệt giá' });
    expect(await within(table).findByText('Nhân Viên A')).toBeInTheDocument();
    expect(within(table).getByText(/1.350.000/)).toBeInTheDocument();
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'PRICE_PENDING' }));
    const listsBefore = listCalls(calls).length;
    const countsBefore = calls.filter(call => call.path.endsWith('/tab-counts')).length;

    await user.click(within(table).getByRole('button', { name: 'Duyệt' }));
    await user.click(within(table).getByRole('button', { name: 'Duyệt' }));

    await waitFor(() => expect(calls.some(call => call.path.endsWith('/applications'))).toBe(true));
    expect(calls.find(call => call.path.endsWith('/decisions')).body).toMatchObject({ decision: 'APPROVE', approvedPrice: '1350000', expectedVersion: 1 });
    await waitFor(() => expect(listCalls(calls).length).toBeGreaterThan(listsBefore));
    expect(calls.filter(call => call.path.endsWith('/tab-counts')).length).toBeGreaterThan(countsBefore);
  });
});

describe('FR-065 price proposals tab for Staff', () => {
  it('lists the waiting sizes read-only, with no decision form', async () => {
    const product = managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
      reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [{ ...managementProduct().variants[0], appliedPrice: '590000', appliedSequence: 1, proposals: [
        { id: '602', variantId: '101', revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '600000', baseAppliedSequence: 1, applied: false,
          submittedBy: { kind: 'ACCOUNT', displayName: 'Nhân Viên A' }, submittedAt: '2026-11-22T08:00:00Z' },
      ] }],
    });
    const calls = mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([product])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_PENDING');
    const table = await screen.findByRole('table', { name: 'Chờ duyệt giá' });
    expect(await within(table).findByText('Chờ Manager duyệt')).toBeInTheDocument();
    expect(within(table).getByText(/600.000/)).toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: 'Từ chối' })).not.toBeInTheDocument();
    expect(listCalls(calls).at(-1).query).toEqual({ tab: 'PRICE_PENDING' });
  });
});

describe('FR-065 unsent price drafts (Staff sub-filter)', () => {
  const withDraft = () => managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '590000', appliedSequence: 1, proposals: [
      { id: '603', variantId: '101', revision: 2, version: 0, state: 'DRAFT', effective: true, proposedPrice: '600000', baseAppliedSequence: 1, applied: false },
    ] }],
  });

  it('lets Staff find a price saved as a draft and send it from the row', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_STAFF,
      [`GET ${M}/products`]: json(200, page([withDraft()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/603/submissions`]: json(200, { commandId: 'c', resourceId: '603', status: 'COMMITTED', version: 1, changed: true }),
    });
    open('/catalogue-management/products?tab=PRICE_PENDING');
    const sub = await screen.findByRole('group', { name: 'Loại đề xuất giá' });
    const draftsChip = await within(sub).findByRole('button', { name: 'Nháp chưa gửi 1' });
    expect(draftsChip).toHaveClass('attention');
    // The top tab stays selected while the sub-filter changes.
    await user.click(draftsChip);
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'PRICE_DRAFT' }));
    expect(screen.getByRole('tab', { name: 'Chờ duyệt giá 2' })).toHaveAttribute('aria-selected', 'true');

    const table = await screen.findByRole('table', { name: 'Nháp chưa gửi' });
    expect(within(table).getByText('Chưa gửi')).toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: 'Giá nháp' })).toBeInTheDocument();
    const listsBefore = listCalls(calls).length;
    await user.click(within(table).getByRole('button', { name: 'Gửi duyệt giá' }));
    await waitFor(() => expect(calls.find(call => call.path.endsWith('/603/submissions')).body).toMatchObject({ expectedVersion: 0 }));
    await waitFor(() => expect(listCalls(calls).length).toBeGreaterThan(listsBefore));
  });

  it('shows a Manager no draft sub-filter, and the waiting prices for a draft link', async () => {
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products`]: json(200, page([])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_DRAFT');
    await screen.findByRole('tab', { name: 'Chờ duyệt giá 2' });
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'PRICE_PENDING' }));
    // Session 2026-11-23: a Manager has the waiting and rejected sub-filters, not the drafts.
    const sub = await screen.findByRole('group', { name: 'Loại đề xuất giá' });
    expect(within(sub).getByRole('button', { name: 'Chờ duyệt 2' })).toHaveClass('attention');
    expect(within(sub).getByRole('button', { name: 'Bị từ chối 3' })).not.toHaveClass('attention');
    expect(within(sub).queryByRole('button', { name: /Nháp chưa gửi/ })).not.toBeInTheDocument();
  });
});

describe('Session 2026-11-22 Staff state buttons on the list', () => {
  // Session 2026-11-23: hiding acts on every branch, so Staff never get Ẩn / Hiện lại; on their own branch they stop or
  // resume selling there only (FR-049), on another branch they only get the details.
  const MY_BRANCH = { 'GET /api/v1/workshop-locations': json(200, [{ id: 1, code: 'HK', name: 'Hoàn Kiếm', address: '', active: true }]) };
  const twoBranches = (overrides = {}) => {
    const base = managementProduct();
    return managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
      branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: true }, { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }],
      variants: [{ ...base.variants[0], remainingUnsold: '9', availableQuantity: '7', branchStocks: [
        { locationId: '1', remainingUnsold: '4', saleEnabled: true, version: 0, availableQuantity: '4' },
        { locationId: '2', remainingUnsold: '5', saleEnabled: true, version: 0, availableQuantity: '3' },
      ] }],
      ...overrides,
    });
  };

  it('gives Staff no Ẩn or Hiện lại, and stops selling at their own branch only', async () => {
    const user = userEvent.setup();
    const live = twoBranches({ id: '42', name: 'Đang bán' });
    const hidden = twoBranches({ id: '43', name: 'Đã gỡ', published: false,
      branches: [{ locationId: '1', name: 'Hoàn Kiếm', active: true, saleEnabled: false }, { locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }] });
    const calls = mockFetch({ ...AS_STAFF, ...MY_BRANCH,
      [`GET ${M}/products`]: json(200, page([live, hidden])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`PUT ${M}/products/42/branches/1/sale-status`]: json(200, { commandId: 'c', resourceId: '42', status: 'COMMITTED', version: 1, changed: true }),
    });
    open('/catalogue-management/products');
    const row = async name => (await screen.findByRole('link', { name })).closest('[role=row]');
    await waitFor(async () => expect(within(await row('Đang bán')).getByRole('button', { name: 'Ngừng bán tại chi nhánh' })).toBeInTheDocument());
    const hiddenRow = await row('Đã gỡ');
    expect(within(hiddenRow).getByRole('button', { name: 'Mở bán lại tại chi nhánh' })).toBeInTheDocument();
    expect(within(hiddenRow).getByText('Đã ngừng bán tại chi nhánh này')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ẩn' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hiện lại' })).toBeNull();
    // Stock column: the quantities at the filtered branch (Hoàn Kiếm: 4), not the sizes.
    expect(screen.getByRole('columnheader', { name: 'Tồn kho' })).toBeInTheDocument();
    expect(within(await row('Đang bán')).getByText('4')).toBeInTheDocument();

    await user.click(within(await row('Đang bán')).getByRole('button', { name: 'Ngừng bán tại chi nhánh' }));
    await waitFor(() => expect(calls.some(call => call.method === 'PUT')).toBe(true));
    const put = calls.find(call => call.method === 'PUT');
    expect(put.path).toBe(`${M}/products/42/branches/1/sale-status`);
    expect(put.body).toEqual({ saleEnabled: false, expectedSaleEnabled: true });
  });

  it('gives Staff only the details on another branch, and totals the stock of every branch without a filter', async () => {
    mockFetch({ ...AS_STAFF, ...MY_BRANCH,
      [`GET ${M}/products`]: json(200, page([twoBranches({ name: 'Hai nơi' })])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    const { unmount } = open('/catalogue-management/products?locationId=2');
    const row = async () => (await screen.findByRole('link', { name: 'Hai nơi' })).closest('[role=row]');
    await waitFor(async () => expect(within(await row()).getByText('5')).toBeInTheDocument());
    expect(within(await row()).getByText('Khả dụng 3')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ngừng bán tại chi nhánh' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Ẩn' })).toBeNull();
    unmount();

    open('/catalogue-management/products?locationId=all');
    await waitFor(async () => expect(within(await row()).getByText('9')).toBeInTheDocument());
    expect(within(await row()).getByText('Khả dụng 7')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ngừng bán tại chi nhánh' })).toBeNull();
  });
});

describe('Session 2026-11-23 rejected prices (price tab sub-filter)', () => {
  const LOCATIONS = { 'GET /api/v1/workshop-locations': json(200, [{ id: 1, code: 'HK', name: 'Hoàn Kiếm', address: '', active: true }]) };
  const rejected = (overrides = {}) => managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '590000', appliedSequence: 1, proposals: [
      { id: '604', variantId: '101', revision: 2, version: 2, state: 'REJECTED', effective: true, proposedPrice: '700000', baseAppliedSequence: 1, applied: false,
        reason: 'Giá cao hơn mặt bằng', submittedBy: { kind: 'ACCOUNT', displayName: 'Nhân Viên A' }, submittedAt: '2026-11-22T08:00:00Z',
        decidedBy: { kind: 'ACCOUNT', displayName: 'Quản Lý B' }, decidedAt: '2026-11-22T09:00:00Z' },
    ] }],
    ...overrides,
  });

  it('lists a rejected size for Staff with the Manager and the reason, and sends a new price from the row', async () => {
    const user = userEvent.setup();
    const product = rejected();
    const calls = mockFetch({ ...AS_STAFF, ...LOCATIONS,
      [`POST ${M}/variants/101/price-proposals`]: json(201, { commandId: 'c1', resourceId: '606', status: 'COMMITTED', version: 0, changed: true }),
      [`POST ${M}/price-proposals/606/submissions`]: json(200, { commandId: 'c2', resourceId: '606', status: 'COMMITTED', version: 1, changed: true }),
      [`GET ${M}/products`]: json(200, page([product])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_PENDING');
    const sub = await screen.findByRole('group', { name: 'Loại đề xuất giá' });
    // For information only: the old price stays, so the chip is not highlighted and the tab counts waiting prices.
    const chip = await within(sub).findByRole('button', { name: 'Bị từ chối 3' });
    expect(chip).not.toHaveClass('attention');
    expect(within(sub).getByRole('button', { name: 'Nháp chưa gửi 1' })).toHaveClass('attention');
    await user.click(chip);
    // A Staff member with one branch lists that branch by default (Session 2026-11-23).
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ tab: 'PRICE_REJECTED', locationId: '1' }));
    expect(screen.getByRole('tab', { name: 'Chờ duyệt giá 2' })).toHaveAttribute('aria-selected', 'true');

    const table = await screen.findByRole('table', { name: 'Bị từ chối' });
    expect(within(table).getByText('Quản Lý B')).toBeInTheDocument();
    expect(within(table).getByText('Giá cao hơn mặt bằng')).toBeInTheDocument();
    expect(within(table).getByText(/700.000/)).toBeInTheDocument();
    await user.click(within(table).getByRole('button', { name: 'Đề xuất giá mới' }));
    await user.type(within(table).getByLabelText('Giá bán mới (VND)'), '650000');
    const listsBefore = listCalls(calls).length;
    await user.click(within(table).getByRole('button', { name: 'Gửi duyệt giá' }));
    await waitFor(() => expect(calls.find(call => call.path.endsWith('/variants/101/price-proposals')).body).toMatchObject({ proposedPrice: '650000' }));
    await waitFor(() => expect(calls.find(call => call.path.endsWith('/606/submissions')).body).toMatchObject({ expectedVersion: 0 }));
    await waitFor(() => expect(listCalls(calls).length).toBeGreaterThan(listsBefore));
    // The product tab "Bị từ chối" is for never-published reviews only and keeps its own count.
    expect(screen.getByRole('tab', { name: 'Bị từ chối 1' })).toBeInTheDocument();
  });

  it('gives a Manager the rejected rows with Đổi giá', async () => {
    mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products`]: json(200, page([rejected()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_REJECTED');
    const table = await screen.findByRole('table', { name: 'Bị từ chối' });
    expect(within(table).getByRole('link', { name: 'Đổi giá' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Chờ duyệt giá 2' })).toHaveAttribute('aria-selected', 'true');
  });

  it('lists a rejected price outside the Staff branches without Đề xuất giá mới (T182)', async () => {
    mockFetch({ ...AS_STAFF, ...LOCATIONS,
      [`GET ${M}/products`]: json(200, page([rejected({ branches: [{ locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }] })])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_REJECTED');
    const table = await screen.findByRole('table', { name: 'Bị từ chối' });
    expect(within(table).getByText('Giá cao hơn mặt bằng')).toBeInTheDocument();
    await waitFor(() => expect(within(table).queryByRole('button', { name: 'Đề xuất giá mới' })).not.toBeInTheDocument());
  });

  it('lists an unsent draft outside the Staff branches without Gửi duyệt (T182)', async () => {
    const base = rejected({ branches: [{ locationId: '2', name: 'Tây Hồ', active: true, saleEnabled: true }] });
    const draft = { ...base, variants: [{ ...base.variants[0], proposals: [
      { id: '605', variantId: '101', revision: 3, version: 0, state: 'DRAFT', effective: true, proposedPrice: '650000', baseAppliedSequence: 1, applied: false },
    ] }] };
    mockFetch({ ...AS_STAFF, ...LOCATIONS,
      [`GET ${M}/products`]: json(200, page([draft])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products?tab=PRICE_DRAFT');
    const table = await screen.findByRole('table', { name: 'Nháp chưa gửi' });
    expect(within(table).getByText(/650.000/)).toBeInTheDocument();
    await waitFor(() => expect(within(table).queryByRole('button', { name: 'Gửi duyệt giá' })).not.toBeInTheDocument());
  });
});

describe('Session 2026-11-23 Staff list opens on their branch', () => {
  const ONE_BRANCH = { 'GET /api/v1/workshop-locations': json(200, [{ id: 1, code: 'HK', name: 'Hoàn Kiếm', address: '', active: true }]) };
  const SELLING = { 'GET /api/v1/product-selling-locations': json(200, { items: [{ id: '1', name: 'Hoàn Kiếm' }, { id: '2', name: 'Tây Hồ' }] }) };

  it('filters list and counts by the Staff branch, and "Tất cả chi nhánh" shows every branch', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_STAFF, ...ONE_BRANCH, ...SELLING,
      [`GET ${M}/products`]: json(200, page([managementProduct()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ locationId: '1' }));
    expect(calls.filter(call => call.path.endsWith('/tab-counts')).at(-1).query).toMatchObject({ locationId: '1' });
    // Only the default view: no "Xóa lọc".
    expect(screen.queryByRole('button', { name: 'Xóa lọc' })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Chi nhánh'), '');
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({}));
    await user.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ locationId: '1' }));
  });

  it('keeps every branch for a Manager', async () => {
    const calls = mockFetch({ ...AS_MANAGER, ...ONE_BRANCH,
      [`GET ${M}/products`]: json(200, page([managementProduct()])),
      [`GET ${M}/products/tab-counts`]: json(200, COUNTS),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products');
    await screen.findByRole('tab', { name: 'Tất cả 9' });
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({}));
  });
});
