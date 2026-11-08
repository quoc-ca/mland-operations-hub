import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, managementProduct } from './fixtures';

const M = '/api/v1/catalogue-management';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const result = action => ({ commandId: 'c', resourceId: '42', resourceType: 'PRODUCT', action, status: 'COMMITTED', version: 9, changed: true });
const page = items => json(200, { items, page: 0, pageSize: 20, totalElements: String(items.length) });

describe('state button on the product list', () => {
  beforeEach(() => clearRoleCache());

  it('offers a Manager one "Công khai" button for a product waiting for review, marks it as submitted, and runs the whole path from the row', async () => {
    const waiting = managementProduct(); // PENDING review, PENDING proposal
    const approved = managementProduct({ version: 4, reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [{ ...waiting.variants[0], proposals: [{ ...waiting.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000' }] }] });
    const applied = managementProduct({ version: 5, reviews: approved.reviews,
      variants: [{ ...waiting.variants[0], appliedSequence: 1, proposals: [{ ...waiting.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000', applied: true }] }] });
    const reads = [approved, applied];
    let read = 0;
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: page([waiting]),
      [`GET ${M}/products/42`]: () => json(200, reads[Math.min(read++, reads.length - 1)]),
      [`POST ${M}/products/42/review-decisions`]: json(200, result('product.review.decide')),
      [`POST ${M}/price-proposals/501/applications`]: json(200, result('price.apply')),
      [`PUT ${M}/products/42/publication`]: json(200, result('product.publish')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });

    const publishButton = await screen.findByRole('button', { name: 'Công khai' });
    expect(screen.getByText('Staff đã gửi duyệt')).toBeInTheDocument();
    await userEvent.setup().click(publishButton);

    await screen.findByRole('link', { name: 'Chi tiết' });
    const writes = calls.filter(call => call.method !== 'GET').map(call => `${call.method} ${call.path.replace(M, '')}`);
    expect(writes).toEqual(['POST /products/42/review-decisions', 'POST /price-proposals/501/applications', 'PUT /products/42/publication']);
  });

  it('offers "Ẩn" for a published product and sends the hide', async () => {
    const live = managementProduct({ published: true, firstPublishedAt: '2026-10-30T00:00:00Z' });
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: page([live]),
      [`PUT ${M}/products/42/publication`]: json(200, result('product.unpublish')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Ẩn' }));

    const hide = calls.find(call => call.method === 'PUT' && call.path.endsWith('/publication'));
    expect(hide.body).toMatchObject({ published: false });
  });

  it('shows no state button to Staff', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [`GET ${M}/categories`]: json(200, categoryPage), [`GET ${M}/products`]: page([managementProduct()]) });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });
    await screen.findByRole('link', { name: 'Nhẫn bạc' });
    expect(screen.queryByRole('button', { name: 'Công khai' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Thao tác' })).toBeNull();
  });
});
