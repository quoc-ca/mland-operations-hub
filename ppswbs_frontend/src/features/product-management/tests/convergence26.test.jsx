import fs from 'node:fs';
import path from 'node:path';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct, signInAs } from './fixtures';

// Phase 26 Convergence (T205, T207, T208, T209).
const M = '/api/v1/catalogue-management';
const AS_MANAGER = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }) };
const AS_STAFF = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }) };
const open = target => renderRoutes(productManagementRoutes, { path: target });
const result = (id, version) => ({ commandId: 'c', resourceId: id, resourceType: 'PRICE_PROPOSAL', status: 'COMMITTED', version, changed: true });

beforeEach(() => {
  signInAs('STAFF');
  clearRoleCache();
  vi.stubGlobal('scrollTo', vi.fn());
});
afterEach(() => vi.unstubAllGlobals());

const variant = (id, size, proposals) => ({ ...managementProduct().variants[0], id, size, sku: `SP000042-S${size}`, appliedPrice: proposals.length ? '200000' : null, appliedSequence: proposals.length ? 1 : 0, proposals });
const applied = id => [{ id: `${id}1`, variantId: id, revision: 1, version: 3, state: 'APPROVED', effective: true, proposedPrice: '200000', approvedPrice: '200000', baseAppliedSequence: 0, applied: true }];
const draft = id => [{ id: `${id}9`, variantId: id, revision: 1, version: 0, state: 'DRAFT', effective: true, proposedPrice: '210000', baseAppliedSequence: 0, applied: false }];
const approvedReview = [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }];

const settledFourSizes = () => managementProduct({
  published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: approvedReview,
  variants: [variant('101', 7, applied('101')), variant('102', 8, applied('102')), variant('103', 9, applied('103')), variant('104', 10, applied('104'))],
});

const toggleOf = name => screen.getByRole('button', { name });
const section = name => screen.getByRole('heading', { name }).closest('section');

describe('T205 FR-067 folding of the details page', () => {
  it('folds Size and Đề xuất giá with more than 3 settled sizes, keeps Công khai open, and heads each size with its price and status', async () => {
    const user = userEvent.setup();
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, settledFourSizes()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });

    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'false');
    expect(toggleOf('Size và số lượng')).toHaveAttribute('aria-expanded', 'false');
    expect(toggleOf('Công khai')).toHaveAttribute('aria-expanded', 'true');
    // Folded, the summary says how many sizes there are.
    expect(toggleOf('Đề xuất giá')).toHaveAccessibleDescription('4 size');

    await user.click(toggleOf('Đề xuất giá'));
    const size7 = within(section('Đề xuất giá')).getByRole('button', { name: /Size 7/ });
    expect(size7).toHaveAttribute('aria-expanded', 'false');
    // The size heading row has the applied price and the status of the current revision.
    expect(size7).toHaveAccessibleDescription(/200\.000.*Đang áp dụng/);
  });

  it('opens Đề xuất giá when a size needs someone even with more than 3 sizes', async () => {
    const product = settledFourSizes();
    product.variants[3] = variant('104', 10, [...applied('104'), { ...draft('104')[0], revision: 2, baseAppliedSequence: 1 }]);
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'true');
    expect(within(section('Đề xuất giá')).getByRole('button', { name: /Size 10/ })).toHaveAttribute('aria-expanded', 'true');
    expect(within(section('Đề xuất giá')).getByRole('button', { name: /Size 7/ })).toHaveAttribute('aria-expanded', 'false');
  });

  it('has no fold per size with 3 sizes or fewer', async () => {
    const product = managementProduct({
      published: true, firstPublishedAt: '2026-10-30T00:00:00Z', reviews: approvedReview,
      variants: [variant('101', 7, applied('101')), variant('102', 8, applied('102')), variant('103', 9, applied('103'))],
    });
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });
    const prices = section('Đề xuất giá');
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'true');
    expect(toggleOf('Size và số lượng')).toHaveAttribute('aria-expanded', 'true');
    expect(within(prices).queryByRole('button', { name: /Size 7/ })).not.toBeInTheDocument();
    expect(within(prices).getByRole('heading', { name: /Size 7.*Giá đang áp dụng/ })).toBeInTheDocument();
    expect(within(prices).getByRole('table', { name: 'Lịch sử giá size 7' })).toBeInTheDocument();
  });
});

describe('T205/T207 a price section held open for missing prices', () => {
  // Never published, no review yet: sizes 7–9 have a price, size 10 has none, so nothing waits and the section starts folded.
  const missingOnePrice = withSize10 => managementProduct({
    reviews: [],
    variants: [variant('101', 7, applied('101')), variant('102', 8, applied('102')), variant('103', 9, applied('103')),
      variant('104', 10, withSize10 ? draft('104') : [])],
  });

  it('opens and locks the section while prices are missing, and keeps it open once they are entered', async () => {
    const user = userEvent.setup();
    let priced = false;
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => json(200, missingOnePrice(priced)),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/variants/104/price-proposals`]: () => { priced = true; return json(201, result('1049', 0)); },
    });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'false');

    // "Công khai ngay" with a size lacking a price marks what is missing without sending anything.
    await user.click(within(section('Công khai')).getByRole('button', { name: 'Công khai ngay' }));
    const head = toggleOf('Đề xuất giá');
    expect(head).toHaveAttribute('aria-expanded', 'true');
    expect(head).toBeDisabled();
    expect(within(section('Đề xuất giá')).getByText('Có size chưa có giá đề xuất: nhập giá ở mục Đề xuất giá.')).toBeInTheDocument();
    await user.click(head); // a locked toggle does nothing
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'true');
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0);

    // Enter the missing price: the mark goes away, the section stays open and can fold again.
    const prices = section('Đề xuất giá');
    await user.click(within(prices).getByRole('button', { name: /Size 10/ }));
    await user.click(within(prices).getByRole('button', { name: 'Đề xuất giá mới' }));
    await user.type(within(prices).getByLabelText('Đề xuất giá mới'), '210000');
    await user.click(within(prices).getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(screen.queryByText('Có size chưa có giá đề xuất: nhập giá ở mục Đề xuất giá.')).not.toBeInTheDocument());
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'true');
    expect(toggleOf('Đề xuất giá')).toBeEnabled();
    await user.click(toggleOf('Đề xuất giá'));
    expect(toggleOf('Đề xuất giá')).toHaveAttribute('aria-expanded', 'false');
  });
});

describe('T208 FR-017/FR-038 the publish button follows the initial review', () => {
  const publicationButtons = () => within(section('Công khai')).queryAllByRole('button').filter(button => /^(Công khai sản phẩm|Công khai ngay)$/.test(button.textContent));

  it('gives a Manager no publish button for a never-published product whose review was rejected', async () => {
    const product = managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'REJECTED', effective: true, reason: 'Ảnh mờ' }] });
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Công khai' });
    expect(publicationButtons()).toEqual([]);
    expect(within(section('Công khai')).getByText(/Hồ sơ đã bị từ chối/)).toBeInTheDocument();
  });

  it('gives a Manager no publish button while the initial review waits for a decision', async () => {
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, managementProduct()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Công khai' });
    expect(publicationButtons()).toEqual([]);
  });

  it('keeps "Công khai sản phẩm" for a hidden product that was published before', async () => {
    const product = { ...settledFourSizes(), published: false };
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Công khai' });
    expect(publicationButtons().map(button => button.textContent)).toEqual(['Công khai sản phẩm']);
  });

  it('gives Staff no publish button even with an approved review', async () => {
    const product = managementProduct({ reviews: approvedReview });
    mockFetch({ ...AS_STAFF, [`GET ${M}/products/42`]: () => json(200, product), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Công khai' });
    expect(publicationButtons()).toEqual([]);
  });
});

describe('T209 no import cycle through the list page', () => {
  it('the details page and its sections do not import ProductListPage', () => {
    const management = path.resolve(__dirname, '../ui/management');
    const files = ['ProductDetailPage.jsx', ...fs.readdirSync(path.join(management, 'sections')).map(name => `sections/${name}`)];
    const offenders = files.filter(file => /from\s+['"][./]*ProductListPage['"]/.test(fs.readFileSync(path.join(management, file), 'utf8')));
    expect(offenders).toEqual([]);
  });
});
