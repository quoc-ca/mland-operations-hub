import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';

const draft = managementProduct({
  reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'DRAFT', effective: true }],
  variants: [{ id: '101', size: 12, sku: 'SP000042-S12', remainingUnsold: '5', validHeldQuantity: '0', availableQuantity: '5', saleEnabled: true, appliedSequence: 0, version: 4,
    proposals: [{ id: '501', variantId: '101', revision: 1, version: 0, state: 'DRAFT', effective: true, proposedPrice: '1250000', baseAppliedSequence: 0, applied: false }] }],
});
const pending = managementProduct({
  version: 4, reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'PENDING', effective: true }],
  variants: [{ ...draft.variants[0], proposals: [{ ...draft.variants[0].proposals[0], state: 'PENDING' }] }],
});
const approved = managementProduct({
  version: 5, reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
  variants: [{ ...draft.variants[0], proposals: [{ ...draft.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000' }] }],
});
const applied = managementProduct({
  version: 6, reviews: approved.reviews,
  variants: [{ ...draft.variants[0], appliedSequence: 1, proposals: [{ ...draft.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000', applied: true }] }],
});
const result = (action) => ({ commandId: 'c', resourceId: '42', resourceType: 'PRODUCT', action, status: 'COMMITTED', version: 7, changed: true });

describe('a Manager publishing a new product without a review round', () => {
  beforeEach(() => clearRoleCache());

  it('submits, approves, applies the proposed prices and publishes from one button', async () => {
    const reads = [draft, pending, approved, applied];
    let read = 0;
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products/42`]: () => json(200, reads[Math.min(read++, reads.length - 1)]),
      [`POST ${M}/products/42/review-submissions`]: json(200, result('product.review.submit')),
      [`POST ${M}/products/42/review-decisions`]: json(200, result('product.review.decide')),
      [`POST ${M}/price-proposals/501/applications`]: json(200, result('price.apply')),
      [`PUT ${M}/products/42/publication`]: json(200, result('product.publish')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Công khai ngay' }));

    await screen.findAllByText(/Đã công khai sản phẩm/);
    const order = calls.filter(call => call.method !== 'GET').map(call => `${call.method} ${call.path.replace(M, '')}`);
    expect(order).toEqual([
      'POST /products/42/review-submissions',
      'POST /products/42/review-decisions',
      'POST /price-proposals/501/applications',
      'PUT /products/42/publication',
    ]);
    expect(calls.find(call => call.path.endsWith('/review-decisions')).body).toMatchObject({ decision: 'APPROVE', prices: [{ variantId: '101', proposalId: '501', approvedPrice: '1250000' }] });
  });

  it('does not offer it to Staff', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products/42`]: json(200, draft) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products/42' });
    await screen.findByRole('heading', { name: /Công khai/ });
    expect(screen.queryByRole('button', { name: 'Công khai ngay' })).toBeNull();
  });
});
