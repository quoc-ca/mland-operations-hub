import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { categoryPage } from './fixtures';

const M = '/api/v1/catalogue-management';
const open = path => renderRoutes(productManagementRoutes, { path });
const listCalls = calls => calls.filter(call => call.method === 'GET' && call.path === `${M}/categories`);
const empty = { items: [], page: 0, pageSize: 20, totalElements: '0' };
beforeEach(() => clearRoleCache());

describe('FR-062 category status filter', () => {
  it('lists every category by default and sends no active filter', async () => {
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/categories');
    expect(await screen.findByLabelText('Trạng thái')).toHaveValue('');
    expect(listCalls(calls)[0].query).toEqual({});
  });

  it('sends active=false for "Ngừng hoạt động" and goes back to the first page', async () => {
    const calls = mockFetch({ [`GET ${M}/categories`]: call => json(200, call.query.active === 'false' ? empty : categoryPage) });
    open('/catalogue-management/categories?page=2');
    const filter = await screen.findByLabelText('Trạng thái');
    await waitFor(() => expect(listCalls(calls)[0].query).toEqual({ page: '2' }));

    await userEvent.selectOptions(filter, 'inactive');

    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ active: 'false' }));
    // Nothing matches: a filtered empty message, and the filter stays to change it back.
    expect(await screen.findByText('Không có danh mục nào khớp bộ lọc.')).toBeInTheDocument();
    expect(screen.queryByText('Chưa có danh mục.')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Trạng thái')).toHaveValue('inactive');
  });

  it('reads the filter from the URL and sends active=true', async () => {
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/categories?status=active');
    expect(await screen.findByLabelText('Trạng thái')).toHaveValue('active');
    expect(listCalls(calls)[0].query).toEqual({ active: 'true' });
  });

  it('searches by name on Tìm, keeps the status filter and goes back to the first page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${M}/categories`]: call => json(200, call.query.q === 'không có' ? empty : categoryPage) });
    open('/catalogue-management/categories?status=active&page=1');
    const search = await screen.findByLabelText('Tìm danh mục');
    await user.type(search, '  Nhẫn ');
    expect(listCalls(calls)).toHaveLength(1); // nothing sent while typing
    await user.click(screen.getByRole('button', { name: 'Tìm' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ active: 'true', q: 'Nhẫn' }));

    await user.clear(screen.getByLabelText('Tìm danh mục'));
    await user.type(screen.getByLabelText('Tìm danh mục'), 'không có{Enter}');
    expect(await screen.findByText('Không có danh mục nào khớp bộ lọc.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tìm danh mục')).toHaveValue('không có');

    await user.clear(screen.getByLabelText('Tìm danh mục'));
    await user.click(screen.getByRole('button', { name: 'Tìm' }));
    await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ active: 'true' }));
  });

  it('reads the name search from the URL', async () => {
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/categories?q=v%C3%A0ng');
    expect(await screen.findByLabelText('Tìm danh mục')).toHaveValue('vàng');
    expect(listCalls(calls)[0].query).toEqual({ q: 'vàng' });
  });

  it('ignores an unknown status in the URL', async () => {
    const calls = mockFetch({ [`GET ${M}/categories`]: json(200, categoryPage) });
    open('/catalogue-management/categories?status=constructor');
    expect(await screen.findByLabelText('Trạng thái')).toHaveValue('');
    expect(listCalls(calls)[0].query).toEqual({});
  });
});
