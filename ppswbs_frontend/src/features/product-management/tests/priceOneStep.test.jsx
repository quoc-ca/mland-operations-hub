import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { proposalStatus } from '../domain/productRules';
import { categoryPage, managementProduct } from './fixtures';
import { beforeEach as beforeEachSignIn } from 'vitest';
import { signInAs } from './fixtures';

// RouteAccessGuard (T219) reads the 001 Account bootstrap: an active Staff unless a test says otherwise.
beforeEachSignIn(() => { signInAs('STAFF'); });

// FR-066 / FR-067 (Session 2026-11-22): a Manager changes a published size's price in one save; the price history
// shows one status per revision.
const M = '/api/v1/catalogue-management';
const AS_MANAGER = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }) };
const AS_STAFF = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }) };
const open = path => renderRoutes(productManagementRoutes, { path });
const result = (id, version) => ({ commandId: 'c', resourceId: id, resourceType: 'PRICE_PROPOSAL', status: 'COMMITTED', version, changed: true });

beforeEach(() => {
  clearRoleCache();
  vi.stubGlobal('scrollTo', vi.fn());
});
afterEach(() => vi.unstubAllGlobals());

/** Published product, size 12 sold at 1.250.000 (revision 2 applied, revision 1 superseded), nothing pending. */
function published(extraProposals = []) {
  return managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [{ ...managementProduct().variants[0], appliedPrice: '1250000', appliedSequence: 2, proposals: [
      { id: '500', variantId: '101', revision: 1, version: 3, state: 'APPROVED', effective: true, proposedPrice: '1200000', approvedPrice: '1200000', baseAppliedSequence: 0, applied: false },
      { id: '501', variantId: '101', revision: 2, version: 3, state: 'APPROVED', effective: true, proposedPrice: '1250000', approvedPrice: '1250000', baseAppliedSequence: 1, applied: true },
      ...extraProposals,
    ] }],
  });
}

function priceSection() {
  return screen.getByRole('heading', { name: 'Đề xuất giá' }).closest('section');
}

async function changePrice(user, amount, { untickApply = false } = {}) {
  await screen.findByRole('heading', { name: 'Đề xuất giá' });
  const section = priceSection();
  await user.click(await within(section).findByRole('button', { name: 'Đổi giá' }));
  const form = within(section).getByRole('form', { name: 'Đổi giá' });
  await user.type(within(form).getByLabelText('Giá bán mới (VND)'), amount);
  if (untickApply) await user.click(within(form).getByLabelText(/Áp dụng giá ngay sau khi duyệt/));
  await user.click(within(form).getByRole('button', { name: 'Lưu' }));
}

describe('FR-066 Manager price change in one step', () => {
  it('proposes, submits, approves at the typed amount and applies, then says the new price is in effect', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => json(200, published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/variants/101/price-proposals`]: json(201, result('601', 0)),
      [`POST ${M}/price-proposals/601/submissions`]: json(200, result('601', 1)),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, result('601', 2)),
      [`POST ${M}/price-proposals/601/applications`]: json(200, result('601', 3)),
    });
    open('/catalogue-management/products/42');
    await changePrice(user, '1400000');

    expect(await screen.findAllByText(/Đã duyệt và áp dụng giá 1.400.000/)).not.toHaveLength(0);
    const writes = calls.filter(call => call.method === 'POST').map(call => call.path.replace(M, ''));
    expect(writes).toEqual(['/variants/101/price-proposals', '/price-proposals/601/submissions',
      '/price-proposals/601/decisions', '/price-proposals/601/applications']);
    expect(calls.find(call => call.path.endsWith('/price-proposals')).body).toMatchObject({ expectedVersion: 4, proposedPrice: '1400000' });
    expect(calls.find(call => call.path.endsWith('/submissions')).body).toMatchObject({ expectedVersion: 0 });
    expect(calls.find(call => call.path.endsWith('/decisions')).body).toEqual({ expectedVersion: 1, decision: 'APPROVE', approvedPrice: '1400000' });
    expect(calls.find(call => call.path.endsWith('/applications')).body).toMatchObject({ expectedVersion: 2 });
  });

  it('leaves the approved price to be applied later when "apply now" is unticked', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => json(200, published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/variants/101/price-proposals`]: json(201, result('601', 0)),
      [`POST ${M}/price-proposals/601/submissions`]: json(200, result('601', 1)),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, result('601', 2)),
    });
    open('/catalogue-management/products/42');
    await changePrice(user, '1400000', { untickApply: true });

    expect(await screen.findAllByText(/Đã duyệt giá 1.400.000.*Giá chưa áp dụng/)).not.toHaveLength(0);
    expect(calls.some(call => call.path.endsWith('/applications'))).toBe(false);
  });

  it('revises the current draft instead of creating a second proposal', async () => {
    const user = userEvent.setup();
    const draft = { id: '600', variantId: '101', revision: 3, version: 0, state: 'DRAFT', effective: true, proposedPrice: '1300000', baseAppliedSequence: 2, applied: false };
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => json(200, published([draft])),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/price-proposals/600/revisions`]: json(201, result('601', 0)),
      [`POST ${M}/price-proposals/601/submissions`]: json(200, result('601', 1)),
      [`POST ${M}/price-proposals/601/decisions`]: json(200, result('601', 2)),
      [`POST ${M}/price-proposals/601/applications`]: json(200, result('601', 3)),
    });
    open('/catalogue-management/products/42');
    await changePrice(user, '1400000');

    await waitFor(() => expect(calls.some(call => call.path.endsWith('/applications'))).toBe(true));
    expect(calls.some(call => call.path.endsWith('/variants/101/price-proposals'))).toBe(false);
    expect(calls.find(call => call.path.endsWith('/revisions')).body).toMatchObject({ expectedVersion: 0, proposedPrice: '1400000' });
  });

  it('keeps the steps done when one fails, reloads and says the change is not finished', async () => {
    const user = userEvent.setup();
    let reads = 0;
    const calls = mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => { reads += 1; return json(200, published()); },
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`POST ${M}/variants/101/price-proposals`]: json(201, result('601', 0)),
      [`POST ${M}/price-proposals/601/submissions`]: json(200, result('601', 1)),
      [`POST ${M}/price-proposals/601/decisions`]: json(409, { code: 'VERSION_CONFLICT', message: 'stale' }),
    });
    open('/catalogue-management/products/42');
    await screen.findByRole('heading', { name: 'Đề xuất giá' });
    const before = reads;
    await changePrice(user, '1400000');

    expect(await screen.findByText(/Chưa đổi xong giá 1.400.000.*cho size 12/)).toBeInTheDocument();
    expect(reads).toBeGreaterThan(before);
    expect(calls.some(call => call.path.endsWith('/applications'))).toBe(false);
  });

  it('gives Staff the propose → submit flow, not the one-step change', async () => {
    mockFetch({ ...AS_STAFF,
      [`GET ${M}/products/42`]: () => json(200, published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products/42');
    const section = await screen.findByRole('heading', { name: 'Đề xuất giá' }).then(heading => heading.closest('section'));
    expect(await within(section).findByRole('button', { name: 'Đề xuất giá mới' })).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: 'Đổi giá' })).not.toBeInTheDocument();
  });
});

describe('FR-067 one status per price revision', () => {
  it('shows the applied revision as applied and the older one as superseded, without an "applied" column', async () => {
    mockFetch({ ...AS_MANAGER,
      [`GET ${M}/products/42`]: () => json(200, published()),
      [`GET ${M}/categories`]: json(200, categoryPage),
    });
    open('/catalogue-management/products/42');
    const table = await screen.findByRole('table', { name: 'Lịch sử giá size 12' });
    expect(within(table).getByText('Đang áp dụng')).toBeInTheDocument();
    expect(within(table).getByText('Đã thay thế')).toBeInTheDocument();
    expect(within(table).queryByRole('columnheader', { name: 'Đã áp dụng' })).not.toBeInTheDocument();
  });

  it('derives the status from state, effective, applied and the applied sequence', () => {
    const variant = { appliedSequence: 2 };
    expect(proposalStatus(variant, { state: 'APPROVED', effective: true, applied: true, baseAppliedSequence: 1 })).toBe('APPLIED');
    expect(proposalStatus(variant, { state: 'APPROVED', effective: true, applied: false, baseAppliedSequence: 2 })).toBe('APPROVED_WAITING');
    expect(proposalStatus(variant, { state: 'APPROVED', effective: true, applied: false, baseAppliedSequence: 0 })).toBe('SUPERSEDED');
    expect(proposalStatus(variant, { state: 'APPROVED', effective: false, applied: false, baseAppliedSequence: 2 })).toBe('SUPERSEDED');
    expect(proposalStatus(variant, { state: 'PENDING', effective: false, applied: false })).toBe('SUPERSEDED');
    expect(proposalStatus(variant, { state: 'PENDING', effective: true, applied: false })).toBe('PENDING');
    expect(proposalStatus(variant, { state: 'DRAFT', effective: true, applied: false })).toBe('DRAFT');
    expect(proposalStatus(variant, { state: 'REJECTED', effective: false, applied: false })).toBe('REJECTED');
  });
});

describe('Session 2026-11-22 price sizes fold when there are many', () => {
  const variant = (id, size, proposals) => ({ ...managementProduct().variants[0], id, size, sku: `SP000042-S${size}`, appliedPrice: '200000', appliedSequence: 1, proposals });
  const appliedOnly = id => [{ id: `${id}1`, variantId: id, revision: 1, version: 3, state: 'APPROVED', effective: true, proposedPrice: '200000', approvedPrice: '200000', baseAppliedSequence: 0, applied: true }];
  const manySizes = () => managementProduct({
    published: true, firstPublishedAt: '2026-10-30T00:00:00Z',
    reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
    variants: [
      variant('101', 7, appliedOnly('101')), variant('102', 8, appliedOnly('102')), variant('103', 9, appliedOnly('103')),
      variant('104', 10, [...appliedOnly('104'), { id: '1049', variantId: '104', revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '210000', baseAppliedSequence: 1, applied: false }]),
    ],
  });

  it('folds settled sizes, keeps a size waiting for a decision open, and folds the whole section', async () => {
    const user = userEvent.setup();
    mockFetch({ ...AS_MANAGER, [`GET ${M}/products/42`]: () => json(200, manySizes()), [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/products/42');
    const size7 = await screen.findByRole('button', { name: /Size 7/ });
    expect(size7).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('table', { name: 'Lịch sử giá size 7' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Size 10/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('table', { name: 'Lịch sử giá size 10' })).toBeInTheDocument();

    await user.click(size7);
    expect(screen.getByRole('table', { name: 'Lịch sử giá size 7' })).toBeInTheDocument();
    // The whole section folds too (no expand/collapse-all buttons).
    expect(screen.queryByRole('button', { name: 'Mở tất cả size' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đề xuất giá' }));
    expect(screen.queryByRole('table', { name: /Lịch sử giá size/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đề xuất giá' }));
    expect(screen.getByRole('table', { name: 'Lịch sử giá size 10' })).toBeInTheDocument();
  });
});
