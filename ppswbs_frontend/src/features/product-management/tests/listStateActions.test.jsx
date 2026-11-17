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

  it('runs the whole path from the row for a never-published draft: submit, approve, apply, publish', async () => {
    const waiting = managementProduct();
    const draft = managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'DRAFT', effective: true }],
      variants: [{ ...waiting.variants[0], proposals: [{ ...waiting.variants[0].proposals[0], state: 'DRAFT' }] }] });
    const pending = managementProduct({ version: 4 });
    const approved = managementProduct({ version: 5, reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'APPROVED', effective: true }],
      variants: [{ ...waiting.variants[0], proposals: [{ ...waiting.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000' }] }] });
    const applied = managementProduct({ version: 6, reviews: approved.reviews,
      variants: [{ ...waiting.variants[0], appliedSequence: 1, proposals: [{ ...waiting.variants[0].proposals[0], state: 'APPROVED', approvedPrice: '1250000', applied: true }] }] });
    const reads = [pending, approved, applied];
    let read = 0;
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: page([draft]),
      [`GET ${M}/products/42`]: () => json(200, reads[Math.min(read++, reads.length - 1)]),
      [`POST ${M}/products/42/review-submissions`]: json(200, result('product.review.submit')),
      [`POST ${M}/products/42/review-decisions`]: json(200, result('product.review.decide')),
      [`POST ${M}/price-proposals/501/applications`]: json(200, result('price.apply')),
      [`PUT ${M}/products/42/publication`]: json(200, result('product.publish')),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Công khai' }));

    await screen.findByRole('link', { name: 'Chi tiết' });
    const writes = calls.filter(call => call.method !== 'GET').map(call => `${call.method} ${call.path.replace(M, '')}`);
    expect(writes).toEqual(['POST /products/42/review-submissions', 'POST /products/42/review-decisions',
      'POST /price-proposals/501/applications', 'PUT /products/42/publication']);
  });

  it('never approves a Staff submission from the row: it links to the initial review instead (FR-010)', async () => {
    const calls = mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: page([managementProduct()]), // PENDING review
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });

    const link = await screen.findByRole('link', { name: 'Xem để duyệt' });
    expect(link).toHaveAttribute('href', '/catalogue-management/products/42#review-title');
    expect(screen.getByText('Staff đã gửi duyệt')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Công khai' })).toBeNull();
    expect(calls.filter(call => call.method !== 'GET')).toHaveLength(0);
  });

  it('points a rejected review to its reason instead of trying to publish it', async () => {
    const rejected = managementProduct({ reviews: [{ id: '301', revision: 1, contentVersion: 1, state: 'REJECTED', effective: true, reason: 'Ảnh mờ' }] });
    mockFetch({
      [PROFILE]: json(200, { role: 'MANAGER' }),
      [`GET ${M}/categories`]: json(200, categoryPage),
      [`GET ${M}/products`]: page([rejected]),
    });
    renderRoutes(productManagementRoutes, { path: '/catalogue-management/products' });

    expect(await screen.findByRole('link', { name: 'Xem lý do' })).toHaveAttribute('href', '/catalogue-management/products/42#review-title');
    expect(screen.queryByRole('button', { name: 'Công khai' })).toBeNull();
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
