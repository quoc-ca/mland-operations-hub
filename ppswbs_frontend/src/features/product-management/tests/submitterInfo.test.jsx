import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct } from './fixtures';

// FR-060/FR-061 (Session 2026-11-16): submitter, the submitter's branches at submit time, and decider.
const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';

const staff = { kind: 'ACCOUNT', displayName: 'Nguyễn Văn Staff' };
const manager = { kind: 'ACCOUNT', displayName: 'Trần Thị Manager' };
const hoanKiem = { kind: 'LISTED', branches: [{ locationId: '3', name: 'Hoàn Kiếm' }] };

function render(role, product) {
  mockFetch({ [PROFILE]: json(200, { role }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, product) });
  renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
}

describe('who submitted and decided (FR-060)', () => {
  beforeEach(() => clearRoleCache());

  it('a pending initial review shows the Staff submitter and their branch to the Manager', async () => {
    const product = managementProduct({
      reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'PENDING', effective: true,
        submittedBy: staff, submittedAt: '2026-11-16T03:00:00Z', submitterBranches: hoanKiem }],
    });
    render('MANAGER', product);

    const review = (await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' })).closest('section');
    expect(within(review).getAllByText('Nguyễn Văn Staff').length).toBeGreaterThan(0);
    expect(within(review).getAllByText('Hoàn Kiếm').length).toBeGreaterThan(0);
    expect(within(review).queryByText('Người quyết định')).toBeNull();
  });

  it('the price history shows submitter, branches and decider per revision; a draft shows none', async () => {
    const product = managementProduct({
      firstPublishedAt: '2026-11-01T00:00:00Z', published: true,
      reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true,
        submittedBy: { kind: 'SYSTEM', displayName: 'Hệ thống' }, submittedAt: '2026-11-01T00:00:00Z',
        submitterBranches: { kind: 'SYSTEM', branches: [] } }],
      variants: [{
        id: '101', size: 12, sku: 'SP000042-S12', remainingUnsold: '5', validHeldQuantity: '0', availableQuantity: '5', saleEnabled: true,
        appliedPrice: '1000000', appliedSequence: 1, version: 4, branchStocks: [],
        proposals: [
          { id: '500', variantId: '101', revision: 1, version: 2, state: 'APPROVED', effective: true, proposedPrice: '1000000', approvedPrice: '1000000',
            baseAppliedSequence: 0, applied: true, submittedBy: manager, submittedAt: '2026-11-02T03:00:00Z',
            submitterBranches: { kind: 'ALL', branches: [] }, decidedBy: manager, decidedAt: '2026-11-02T04:00:00Z' },
          { id: '501', variantId: '101', revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '1250000',
            baseAppliedSequence: 1, applied: false, submittedBy: staff, submittedAt: '2026-11-16T03:00:00Z', submitterBranches: hoanKiem },
          { id: '502', variantId: '101', revision: 0, version: 0, state: 'REJECTED', effective: false, proposedPrice: '900000', reason: 'Thấp',
            baseAppliedSequence: 0, applied: false, submittedBy: { kind: 'ACCOUNT', displayName: 'Tài khoản #9' }, submittedAt: '2026-10-30T03:00:00Z',
            submitterBranches: { kind: 'NOT_RECORDED', branches: [] }, decidedBy: manager, decidedAt: '2026-10-30T04:00:00Z' },
        ],
      }],
    });
    render('MANAGER', product);

    const history = await screen.findByRole('table', { name: 'Lịch sử giá size 12' });
    const rows = within(history).getAllByRole('row');
    const pending = rows.find(row => within(row).queryByText(/Nguyễn Văn Staff/));
    expect(within(pending).getByText('Hoàn Kiếm')).toBeInTheDocument();
    expect(within(pending).queryByText('Người quyết định')).toBeNull();
    const approved = rows.find(row => within(row).queryByText('Tất cả chi nhánh'));
    expect(within(approved).getByText('Người quyết định')).toBeInTheDocument();
    expect(within(approved).getAllByText('Trần Thị Manager')).toHaveLength(2); // submitted and decided
    const old = rows.find(row => within(row).queryByText(/Tài khoản #9/));
    expect(within(old).getByText('Chưa ghi nhận')).toBeInTheDocument();
  });

  it('the multi-size approval table names who sent each pending size', async () => {
    const pendingSize = (id, size) => ({
      id, size, sku: `SP000042-S${size}`, remainingUnsold: '5', validHeldQuantity: '0', availableQuantity: '5', saleEnabled: true,
      appliedPrice: '1000000', appliedSequence: 1, version: 4, branchStocks: [],
      proposals: [{ id: `${id}9`, variantId: id, revision: 2, version: 1, state: 'PENDING', effective: true, proposedPrice: '1250000',
        baseAppliedSequence: 1, applied: false, submittedBy: staff, submittedAt: '2026-11-16T03:00:00Z', submitterBranches: hoanKiem }],
    });
    render('MANAGER', managementProduct({
      firstPublishedAt: '2026-11-01T00:00:00Z', published: true,
      reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [pendingSize('101', 12), pendingSize('102', 13)],
    }));

    const batch = await screen.findByRole('table', { name: 'Giá đang chờ duyệt (2 size)' });
    expect(within(batch).getAllByText('Nguyễn Văn Staff')).toHaveLength(2);
    expect(within(batch).getAllByText('Hoàn Kiếm')).toHaveLength(2);
  });

  it('a draft never submitted shows no submitter', async () => {
    const product = managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'DRAFT', effective: true }] });
    render('STAFF', product);
    await screen.findByRole('heading', { name: 'Duyệt hồ sơ ban đầu' });
    expect(screen.queryByText('Người gửi')).toBeNull();
  });
});
