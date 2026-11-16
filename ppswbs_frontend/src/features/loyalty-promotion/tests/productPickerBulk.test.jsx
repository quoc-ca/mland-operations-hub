import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { loyaltyPromotionRoutes } from '../routes/loyaltyPromotionRoutes';

const P = '/api/v1/promotion-programs';
const C = '/api/v1/catalogue-management/products';
const open = path => renderRoutes(loyaltyPromotionRoutes, { path });
const searches = calls => calls.filter(call => call.method === 'GET' && call.path === C);

// Two pages of 002 results: page 0 has a deleted product, page 1 one more product.
const pages = {
  0: { items: [
    { id: '42', name: 'Nhẫn bạc', published: false, firstPublishedAt: null, variants: [{ id: '101', size: 12, sku: 'SP000042-S12' }, { id: '102', size: 14, sku: 'SP000042-S14' }] },
    { id: '43', name: 'Nhẫn vàng', published: true, variants: [{ id: '103', size: 16, sku: 'SP000043-S16' }] },
    { id: '52', name: 'Nhẫn bỏ', published: false, deletedAt: '2026-10-02T00:00:00Z', variants: [{ id: '521', size: 12, sku: 'SP000052-S12' }] },
  ], page: 0, pageSize: 3, totalElements: '4' },
  1: { items: [
    { id: '44', name: 'Nhẫn đôi', published: true, variants: [{ id: '104', size: 10, sku: 'SP000044-S10' }] },
  ], page: 1, pageSize: 3, totalElements: '4' },
};
const catalogue = call => json(200, pages[call.query.page || 0]);

describe('product picker status filter and choose all (Session 2026-11-16)', () => {
  it('sends the publication filter to 002 and starts again from the first page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${C}`]: catalogue });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(searches(calls).at(-1).query).toEqual({ page: '1' }));

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'false');
    await waitFor(() => expect(searches(calls).at(-1).query).toEqual({ page: '0', published: 'false' }));
    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'true');
    await waitFor(() => expect(searches(calls).at(-1).query).toEqual({ page: '0', published: 'true' }));
    await user.selectOptions(screen.getByLabelText('Trạng thái'), '');
    await waitFor(() => expect(searches(calls).at(-1).query).toEqual({ page: '0' }));
  });

  it('chooses and clears whole products of the shown page only, skipping deleted ones', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${C}`]: catalogue, [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'x' }) });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    const clearAll = await screen.findByRole('button', { name: 'Bỏ chọn tất cả trên trang này' });
    expect(clearAll).toBeDisabled();
    // A single size chosen first is replaced by its whole product.
    await user.click(screen.getByRole('checkbox', { name: /Size 14/ }));

    await user.click(screen.getByRole('button', { name: 'Chọn tất cả trên trang này' }));
    expect(screen.getByText('Đã chọn (2)')).toBeInTheDocument();
    expect(screen.queryByText('Nhẫn bạc · size 14 · SP000042-S14')).toBeNull();
    expect(screen.getByRole('checkbox', { name: /#42\)/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /#52\)/ })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Chọn tất cả trên trang này' })).toBeDisabled();

    // Another page: its own choose all adds to the selection; its clear all leaves page 0 alone.
    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await screen.findByRole('checkbox', { name: /#44\)/ });
    expect(screen.getByRole('button', { name: 'Bỏ chọn tất cả trên trang này' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Chọn tất cả trên trang này' }));
    expect(screen.getByText('Đã chọn (3)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Bỏ chọn tất cả trên trang này' }));
    expect(screen.getByText('Đã chọn (2)')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Tên chương trình'), 'Giảm');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({ productIds: ['42', '43'] });
  }, 20000);

  it('clear all also drops single sizes of the shown products', async () => {
    const user = userEvent.setup();
    mockFetch({ [`GET ${C}`]: catalogue });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('checkbox', { name: /Size 16/ }));
    expect(screen.getByText('Đã chọn (1)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Bỏ chọn tất cả trên trang này' }));
    expect(screen.getByText('Đã chọn (0)')).toBeInTheDocument();
  });
});
