import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage, signInAs } from './fixtures';

// T219: /catalogue-management/* is a STAFF and MANAGER route family (route-access-matrix.md, FR-002). Any other role
// is replaced to /404 before the page renders or calls the catalogue API.
const M = '/api/v1/catalogue-management';
const routes = [...productManagementRoutes, { path: '/404', element: <p>Không tìm thấy trang</p> }];

function backend(role) {
  return mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    [`GET ${M}/products`]: json(200, { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    [`GET ${M}/products/tab-counts`]: json(200, {}),
    [`GET ${M}/categories`]: json(200, categoryPage),
    'GET /api/v1/product-selling-locations': json(200, []),
  });
}

beforeEach(() => clearRoleCache());

describe('T219 catalogue management route access', () => {
  for (const role of ['ADMIN', 'MEMBER']) {
    it(`replaces ${role} to /404 without rendering the page or calling the catalogue API`, async () => {
      signInAs(role);
      const calls = backend(role);
      const { router } = renderRoutes(routes, { path: '/catalogue-management/products' });
      expect(await screen.findByText('Không tìm thấy trang')).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/404');
      expect(calls.some(call => call.path.startsWith(M))).toBe(false);
    });
  }

  it('replaces a suspended Staff to /404', async () => {
    signInAs('STAFF', 'SUSPENDED');
    backend('STAFF');
    const { router } = renderRoutes(routes, { path: '/catalogue-management/products/42' });
    await waitFor(() => expect(router.state.location.pathname).toBe('/404'));
  });

  for (const role of ['STAFF', 'MANAGER']) {
    it(`lets ${role} open the product list`, async () => {
      signInAs(role);
      const calls = backend(role);
      const { router } = renderRoutes(routes, { path: '/catalogue-management/products' });
      await waitFor(() => expect(calls.some(call => call.path === `${M}/products`)).toBe(true));
      expect(router.state.location.pathname).toBe('/catalogue-management/products');
    });
  }
});
