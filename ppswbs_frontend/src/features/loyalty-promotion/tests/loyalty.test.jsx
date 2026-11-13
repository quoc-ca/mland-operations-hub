import React, { useState } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { loyaltyApi } from '../infrastructure/loyaltyApi';
import { BenefitSelector } from '../ui/checkout/BenefitSelector';
import { loyaltyPromotionRoutes } from '../routes/loyaltyPromotionRoutes';
import { serverFieldErrors } from '../domain/promotionRules';
import { account, policy, program, publicPromotion, transaction, voucherUsage } from './fixtures';

const P = '/api/v1/promotion-programs';
const C = '/api/v1/catalogue-management/products';
const catalogue = {
  items: [
    { id: '42', name: 'Nhẫn bạc', variants: [{ id: '101', size: 12, sku: 'SP000042-S12' }, { id: '102', size: 14, sku: 'SP000042-S14' }] },
    { id: '43', name: 'Nhẫn vàng', variants: [{ id: '103', size: 16, sku: 'SP000043-S16' }] },
  ],
  page: 0, pageSize: 20, totalElements: '2',
};
const open = (path, options) => renderRoutes(loyaltyPromotionRoutes, { path, ...options });

describe('006 REST operations', () => {
  const request = vi.fn(async () => ({}));
  const api = loyaltyApi(request);

  it('sends expectedVersion as a string and saves a voucher without a body', async () => {
    await api.pauseProgram('k1', '5', '2');
    expect(request).toHaveBeenLastCalledWith(`${P}/5/pauses`, { method: 'POST', idempotencyKey: 'k1', body: { expectedVersion: '2' } });
    await api.saveVoucher('k2', '5');
    expect(request).toHaveBeenLastCalledWith('/api/v1/members/me/saved-vouchers/5', { method: 'PUT', idempotencyKey: 'k2' });
    await api.listPublicPromotions({ scope: 'PRODUCT' });
    expect(request).toHaveBeenLastCalledWith('/api/v1/promotions', { query: { scope: 'PRODUCT', cursor: undefined, limit: undefined }, auth: false });
  });
});

describe('T030 programmes', () => {
  it('shows an error on one list item on its list field, with the item', () => {
    expect(serverFieldErrors({ fieldErrors: [
      { field: 'targets.variantIds[16]', message: 'This target does not exist.' },
      { field: 'targets.variantIds[17]', message: 'This target does not exist.' },
      { field: 'code', message: 'Already issued.' },
    ] })).toEqual({
      'targets.variantIds': '16: This target does not exist. 17: This target does not exist.',
      code: 'Already issued.',
    });
  });

  it('lists with filters from the URL and loads the next cursor page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${P}`]: call => json(200, call.query.cursor
        ? { items: [program({ id: '6', name: 'Trang 2' })], nextCursor: null }
        : { items: [program()], nextCursor: 'c2' }),
    });
    open('/promotion-management/programs?status=ENABLED');
    expect(await screen.findByRole('link', { name: 'Giảm 10%' })).toHaveAttribute('href', '/promotion-management/programs/5');
    // The sidebar reads the profile for the role, so find the list call instead of taking the first call.
    expect(calls.find(call => call.path === P).query).toEqual({ status: 'ENABLED' });
    await user.click(screen.getByRole('button', { name: 'Xem thêm' }));
    expect(await screen.findByRole('link', { name: 'Trang 2' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Giảm 10%' })).toBeInTheDocument();
  });

  it('an automatic discount is product-only and has no code or limits', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/promotion-management/programs/new');
    await user.selectOptions(await screen.findByLabelText('Loại'), 'AUTO_DISCOUNT');
    expect(screen.getByLabelText('Phạm vi')).toBeDisabled();
    expect(screen.queryByLabelText('Mã voucher')).toBeNull();
    expect(screen.queryByLabelText('Tổng số lượt')).toBeNull();
  });

  it('fills code and window, keeps typed values on a field error, redraws a taken code; success opens the programme', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const calls = mockFetch({
      [`POST ${P}`]: () => {
        attempt += 1;
        if (attempt === 1) return json(400, { code: 'INVALID_INPUT', message: 'Bad.', fieldErrors: [{ field: 'name', message: 'Name rejected.' }] });
        if (attempt === 2) return json(409, { code: 'CODE_EXISTS', message: 'Exists.', fieldErrors: [{ field: 'code', message: 'Already issued.' }] });
        return json(201, program({ id: '9' }));
      },
      [`GET ${P}/9`]: json(200, program({ id: '9' })),
      [`GET ${P}/9/usage`]: json(200, voucherUsage),
      [`GET ${C}`]: json(200, catalogue),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
      [`GET ${C}/43`]: json(200, catalogue.items[1]),
    });
    open('/promotion-management/programs/new');
    const code = await screen.findByLabelText('Mã voucher');
    expect(code).toHaveAttribute('readonly');
    expect(code.value).toMatch(/^ML[A-HJKMNP-Z2-9]{8}$/);
    expect(screen.getByLabelText('Bắt đầu').value).not.toBe('');
    expect(screen.getAllByText(/^Tức là: /)).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Sinh mã khác' }));
    const shown = screen.getByLabelText('Mã voucher').value;

    await user.type(screen.getByLabelText('Tên chương trình'), 'Giảm 10%');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.type(screen.getByLabelText('Tìm sản phẩm'), 'nhẫn');
    await user.click(screen.getByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('checkbox', { name: /#42\)/ }));
    await user.click(screen.getByRole('checkbox', { name: /#43\)/ }));
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));

    expect(await screen.findByText('Name rejected.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tên chương trình')).toHaveValue('Giảm 10%');
    const first = calls.find(call => call.method === 'POST');
    expect(first.body).toMatchObject({
      kind: 'VOUCHER', scope: 'PRODUCT', name: 'Giảm 10%', code: shown, allowPoints: true, totalLimit: '100', perMemberLimit: '1',
      discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' }, minimumEligibleAmount: '0', targets: { productIds: ['42', '43'] },
    });
    expect(first.body.startAt).toMatch(/Z$/);

    // The code was taken meanwhile: a new one is drawn and the request sent again, with a new key, without asking.
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Đã tạo chương trình.')).toBeInTheDocument();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts).toHaveLength(3);
    expect(posts[1].body.code).toBe(shown);
    expect(posts[2].body.code).not.toBe(shown);
    expect(posts[2].body.code).toMatch(/^ML[A-HJKMNP-Z2-9]{8}$/);
    expect(posts[2].headers['Idempotency-Key']).not.toBe(posts[1].headers['Idempotency-Key']);
  }, 20000); // three submissions with many typed fields: give a loaded machine time
});

describe('field errors clear once the value is fixed (review 2026-11-10)', () => {
  it('a hint and a server field error disappear when the field changes, before sending again', async () => {
    const user = userEvent.setup();
    mockFetch({
      [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'Bad.', fieldErrors: [{ field: 'name', message: 'Name rejected.' }] }),
      [`GET ${C}`]: json(200, catalogue),
    });
    open('/promotion-management/programs/new');
    await screen.findByLabelText('Mã voucher');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(screen.getAllByText('Bắt buộc nhập.').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Là số nguyên dương, không dấu chấm/phẩy, không bắt đầu bằng 0.').length).toBeGreaterThan(0);
    const before = screen.getAllByText('Bắt buộc nhập.').length;
    await user.type(screen.getByLabelText('Tên chương trình'), 'Giảm 10%');
    expect(screen.queryAllByText('Bắt buộc nhập.').length).toBe(before - 1);
    const positive = screen.getAllByText('Là số nguyên dương, không dấu chấm/phẩy, không bắt đầu bằng 0.').length;
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    expect(screen.getAllByText('Là số nguyên dương, không dấu chấm/phẩy, không bắt đầu bằng 0.').length).toBe(positive - 1);
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    expect(screen.queryByText(/Phần trăm từ lớn hơn 0 đến 100/)).toBeNull();
    // Choosing branches does not answer "pick a product or size": that error stays until a product is picked.
    await user.click(screen.getByLabelText('Chỉ một số chi nhánh'));
    expect(screen.getByText('Chọn ít nhất một sản phẩm hoặc một size.')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Tất cả chi nhánh'));

    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.type(screen.getByLabelText('Tìm sản phẩm'), 'nhẫn');
    await user.click(screen.getByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('checkbox', { name: /#42\)/ }));
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Name rejected.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Tên chương trình'), '!');
    expect(screen.queryByText('Name rejected.')).toBeNull();
  }, 20000);

  it('a point policy hint disappears when the value is corrected', async () => {
    const user = userEvent.setup();
    mockFetch({ 'GET /api/v1/loyalty-policies/current': json(200, policy) });
    open('/promotion-management/point-policy');
    await user.click(await screen.findByRole('button', { name: 'Sửa' }));
    const earn = screen.getByLabelText('Số tiền để tích 1 điểm (₫)');
    await user.clear(earn);
    await user.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(screen.getByText('Là số nguyên dương, không dấu chấm/phẩy, không bắt đầu bằng 0.')).toBeInTheDocument();
    await user.type(earn, '20000');
    expect(screen.queryByText('Là số nguyên dương, không dấu chấm/phẩy, không bắt đầu bằng 0.')).toBeNull();
  });
});

describe('product target picker', () => {
  it('searches 002 products, sends only the chosen IDs and a whole product covers its sizes', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${C}`]: json(200, catalogue), [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'x' }) });
    open('/promotion-management/programs/new');
    await user.type(await screen.findByLabelText('Tìm sản phẩm'), 'SP000042');
    await user.keyboard('{Enter}');
    const size14 = await screen.findByRole('checkbox', { name: /Size 14/ });
    expect(calls.find(call => call.path === C).query).toEqual({ q: 'SP000042', page: '0' });
    await user.click(size14);
    expect(screen.getByText('Nhẫn bạc · size 14 · SP000042-S14')).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: /Size 16/ }));
    await user.click(screen.getByRole('checkbox', { name: /#42\)/ }));
    expect(screen.getByRole('checkbox', { name: /Size 12/ })).toBeDisabled();
    expect(screen.queryByText('Nhẫn bạc · size 14 · SP000042-S14')).toBeNull();
    expect(screen.getByText('Đã chọn (2)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Bỏ chọn Nhẫn vàng · size 16 · SP000043-S16' }));
    expect(screen.getByText('Đã chọn (1)')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Tên chương trình'), 'Giảm');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({ productIds: ['42'] });
  }, 20000); // many typed fields: give a loaded machine time

  it('sends a whole product mixed with a size of another product as sizes, so the backend accepts it', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${C}`]: json(200, catalogue), [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'x' }) });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('checkbox', { name: /#42\)/ }));
    await user.click(screen.getByRole('checkbox', { name: /Size 16/ }));
    expect(screen.getByText(/Đang chọn cả sản phẩm lẫn từng size/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Tên chương trình'), 'Giảm');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({ variantIds: ['101', '102', '103'] });
  }, 20000);

  it('labels products and sizes that are not on sale and keeps deleted products unchoosable', async () => {
    const user = userEvent.setup();
    mockFetch({ [`GET ${C}`]: json(200, { items: [
      { id: '50', name: 'Nhẫn mới', published: false, firstPublishedAt: null, variants: [{ id: '501', size: 10, sku: 'SP000050-S10', saleEnabled: true }] },
      { id: '51', name: 'Nhẫn cũ', published: false, firstPublishedAt: '2026-10-01T00:00:00Z', variants: [{ id: '511', size: 11, sku: 'SP000051-S11', saleEnabled: false }] },
      { id: '52', name: 'Nhẫn bỏ', published: false, deletedAt: '2026-10-02T00:00:00Z', variants: [{ id: '521', size: 12, sku: 'SP000052-S12', saleEnabled: true }] },
      { id: '53', name: 'Nhẫn bán', published: true, firstPublishedAt: '2026-10-01T00:00:00Z', variants: [{ id: '531', size: 13, sku: 'SP000053-S13', saleEnabled: true }] },
    ], page: 0, pageSize: 20, totalElements: '4' }) });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    const results = await screen.findByRole('list', { name: 'Kết quả tìm sản phẩm' });
    const row = name => within(results).getByText(new RegExp(name)).closest('li');
    expect(within(row('Nhẫn mới')).getByText('Chưa công khai')).toBeInTheDocument();
    expect(within(row('Nhẫn cũ')).getByText('Đang ẩn')).toBeInTheDocument();
    expect(within(row('Nhẫn cũ')).getByText('Ngừng bán')).toBeInTheDocument();
    expect(within(row('Nhẫn bỏ')).getByText('Đã xóa')).toBeInTheDocument();
    expect(within(row('Nhẫn bỏ')).getAllByRole('checkbox').every(box => box.disabled)).toBe(true);
    expect(within(row('Nhẫn bán')).queryByText(/Chưa công khai|Đang ẩn|Đã xóa|Ngừng bán/)).toBeNull();
    expect(within(row('Nhẫn cũ')).getAllByRole('checkbox').some(box => box.disabled)).toBe(false);
  });

  it('asks for a target before sending anything', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({});
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Chọn ít nhất một sản phẩm hoặc một size.')).toBeInTheDocument();
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0);
  });

  it('the detail names its products and sizes and keeps an ID it cannot read', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program({ targets: { productIds: ['43', '99'], variantIds: ['102'] } })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/43`]: json(200, catalogue.items[1]),
      [`GET ${C}/99`]: json(404, { code: 'PRODUCT_NOT_FOUND', message: 'x' }),
      [`GET ${C}`]: json(200, catalogue),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Nhẫn vàng (#43); 99')).toBeInTheDocument();
    expect(await screen.findByText('Nhẫn bạc · size 14 · SP000042-S14')).toBeInTheDocument();
  });
});

describe('product voucher branches (Session 2026-11-09, T086)', () => {
  // The form reads branch management (as 002's product create does); names on details come from the public list.
  const W = '/api/v1/workshop-locations';
  const B = '/api/v1/product-selling-locations';
  const managed = [{ id: 7, code: 'HN-HOANKIEM', name: 'Hoàn Kiếm', address: 'Hà Nội', active: true }, { id: 8, code: 'HN-TAYHO', name: 'Tây Hồ', address: 'Hà Nội', active: true }];
  const branches = { items: [{ id: '7', name: 'Hoàn Kiếm', address: 'Hà Nội' }, { id: '8', name: 'Tây Hồ', address: 'Hà Nội' }] };
  const atBranch7 = {
    items: [{
      id: '42', name: 'Nhẫn bạc', branches: [{ locationId: '7', name: 'Hoàn Kiếm', active: true, saleEnabled: true }],
      variants: [
        { id: '101', size: 12, sku: 'SP000042-S12', branchStocks: [{ locationId: '7', remainingUnsold: '3', saleEnabled: true, version: 0 }] },
        { id: '102', size: 14, sku: 'SP000042-S14', branchStocks: [{ locationId: '8', remainingUnsold: '2', saleEnabled: true, version: 0 }] },
      ],
    }],
    page: 0, pageSize: 20, totalElements: '1',
  };
  const fillTerms = async user => {
    await user.type(screen.getByLabelText('Tên chương trình'), 'Hoàn Kiếm');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
  };

  it('limits the voucher to a chosen branch, suggests only products sold there and warns for a size not sold there', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${W}`]: json(200, managed), [`GET ${C}`]: json(200, atBranch7), [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'x' }) });
    open('/promotion-management/programs/new');
    expect(await screen.findByRole('radio', { name: 'Tất cả chi nhánh' })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: 'Chỉ một số chi nhánh' }));
    await user.click(await screen.findByRole('checkbox', { name: /Hoàn Kiếm/ }));
    await user.click(screen.getByRole('button', { name: 'Tìm' }));
    const size14 = await screen.findByRole('checkbox', { name: /Size 14/ });
    expect(calls.filter(call => call.path === C).at(-1).query).toEqual({ page: '0', locationId: '7' });
    expect(within(size14.closest('label')).getByText('Chưa bán ở chi nhánh đã chọn')).toBeInTheDocument();
    expect(within(screen.getByRole('checkbox', { name: /Size 12/ }).closest('label')).queryByText('Chưa bán ở chi nhánh đã chọn')).toBeNull();
    await user.click(size14);
    expect(screen.getByText(/Một số size đã chọn chưa bán ở chi nhánh nào đã chọn/)).toBeInTheDocument();

    await fillTerms(user);
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({ variantIds: ['102'], branchIds: ['7'] });
  }, 20000);

  it('"Only some branches" with none chosen is refused before sending; "All branches" sends no branch', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ [`GET ${W}`]: json(200, managed), [`GET ${C}`]: json(200, catalogue), [`POST ${P}`]: json(400, { code: 'INVALID_INPUT', message: 'x' }) });
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Tìm' }));
    await user.click(await screen.findByRole('checkbox', { name: /#42\)/ }));
    await fillTerms(user);
    await user.click(screen.getByRole('radio', { name: 'Chỉ một số chi nhánh' }));
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Chọn ít nhất một chi nhánh, hoặc chọn "Tất cả chi nhánh".')).toBeInTheDocument();
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0);

    await user.click(screen.getByRole('radio', { name: 'Tất cả chi nhánh' }));
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    await waitFor(() => expect(calls.some(call => call.method === 'POST')).toBe(true));
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({ productIds: ['42'] });
  }, 20000);

  it('an automatic discount has no branch choice', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/promotion-management/programs/new');
    await user.selectOptions(await screen.findByLabelText('Loại'), 'AUTO_DISCOUNT');
    expect(screen.getByText('Giảm tự động áp dụng ở mọi chi nhánh.')).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Chỉ một số chi nhánh' })).toBeNull();
  });

  it('the detail shows "All branches", or the branch names, and warns for a branch no longer active', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program({ targets: { productIds: ['42'], branchIds: ['7', '9'] },
        targetCheck: 'CHECKED', targetWarnings: [{ dimension: 'BRANCH', targetKey: '9', state: 'BRANCH_INACTIVE' }] })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
      [`GET ${B}`]: json(200, branches),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Hoàn Kiếm; Chi nhánh #9')).toBeInTheDocument();
    expect(await screen.findByText('Chi nhánh #9 — chi nhánh đã ngừng hoạt động')).toBeInTheDocument();
  });

  it('a product voucher without branches reads "All branches"', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program()),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Tất cả chi nhánh')).toBeInTheDocument();
  });
});

describe('T047 edit / pause / resume', () => {
  it('sends only the changed fields; on 409 loads the latest and keeps the edit', async () => {
    const user = userEvent.setup();
    let reads = 0;
    const calls = mockFetch({
      [`GET ${P}/5`]: () => json(200, program({ version: reads++ === 0 ? '2' : '4' })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`PATCH ${P}/5`]: json(409, { code: 'STALE_VERSION', message: 'Stale.' }),
    });
    open('/promotion-management/programs/5');
    await user.click(await screen.findByRole('button', { name: 'Sửa' }));
    const form = screen.getByRole('heading', { name: 'Sửa chương trình' }).closest('form');
    const name = within(form).getByLabelText('Tên chương trình');
    await user.clear(name);
    await user.type(name, 'Giảm 12%');
    await user.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(await within(form).findByText(/Dữ liệu đã được thay đổi/)).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PATCH').body).toEqual({ expectedVersion: '2', name: 'Giảm 12%' });
    await user.click(within(form).getByRole('button', { name: 'Tải bản mới nhất' }));
    await waitFor(() => expect(reads).toBe(2));
    expect(within(form).getByLabelText('Tên chương trình')).toHaveValue('Giảm 12%');
  });

  it('an earlier success notice goes away when a new edit starts, so it never sits beside a new error (review 2026-11-10)', async () => {
    const user = userEvent.setup();
    let patches = 0;
    mockFetch({
      [`GET ${P}/5`]: json(200, program({ version: '2' })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`PATCH ${P}/5`]: () => (++patches === 1 ? json(200, program({ version: '3', name: 'Giảm 12%' }))
        : json(409, { code: 'STALE_VERSION', message: 'Stale.' })),
    });
    open('/promotion-management/programs/5');
    await user.click(await screen.findByRole('button', { name: 'Sửa' }));
    let form = screen.getByRole('heading', { name: 'Sửa chương trình' }).closest('form');
    await user.clear(within(form).getByLabelText('Tên chương trình'));
    await user.type(within(form).getByLabelText('Tên chương trình'), 'Giảm 12%');
    await user.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Đã lưu thay đổi.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Sửa' }));
    expect(screen.queryByText('Đã lưu thay đổi.')).toBeNull();
    form = screen.getByRole('heading', { name: 'Sửa chương trình' }).closest('form');
    await user.type(within(form).getByLabelText('Tên chương trình'), '!');
    await user.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(await within(form).findByText(/Dữ liệu đã được thay đổi/)).toBeInTheDocument();
    expect(screen.queryByText('Đã lưu thay đổi.')).toBeNull();
  });

  it('pausing needs confirmation; Cancel sends nothing; usage is shown', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${P}/5`]: json(200, program()),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`POST ${P}/5/pauses`]: json(200, program({ status: 'PAUSED', version: '3' })),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('7 / 100')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tạm dừng' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Hủy' }));
    expect(calls.some(call => call.method === 'POST')).toBe(false);
    await user.click(screen.getByRole('button', { name: 'Tạm dừng' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Tạm dừng' }));
    expect(await screen.findByText('Đã tạm dừng chương trình.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mở lại' })).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body).toEqual({ expectedVersion: '2' });
  });
});

describe('T034 point policy', () => {
  it('shows "saved" only from the confirmed response and the latest after 409', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const calls = mockFetch({
      'GET /api/v1/loyalty-policies/current': json(200, policy),
      'PUT /api/v1/loyalty-policies/current': () => (++attempt === 1
        ? json(409, { code: 'STALE_VERSION', message: 'Stale.' })
        : json(200, { ...policy, version: '4', earnUnitAmount: '20000' })),
    });
    open('/promotion-management/point-policy');
    await user.click(await screen.findByRole('button', { name: 'Sửa' }));
    const earn = screen.getByLabelText('Số tiền để tích 1 điểm (₫)');
    await user.clear(earn);
    await user.type(earn, '20000');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText(/Dữ liệu đã được thay đổi/)).toBeInTheDocument();
    expect(screen.queryByText('Đã lưu chính sách điểm mới.')).toBeNull();
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ expectedVersion: '3', earnUnitAmount: '20000', redeemPointValue: '100', minRedeemPoints: '10', maxRedeemPercent: '50' });
    await user.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Đã lưu chính sách điểm mới.')).toBeInTheDocument();
    expect(screen.getByText('Mỗi 20.000 ₫ được 1 điểm')).toBeInTheDocument();
  });
});

describe('T040 My Loyalty', () => {
  it('shows balance, held, available, rules and history with paging', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/members/me/loyalty-accounts/current': json(200, account),
      'GET /api/v1/members/me/loyalty-transactions': call => json(200, call.query.cursor
        ? { items: [transaction('t2', 'EARN', '30', '0')], nextCursor: null }
        : { items: [transaction('t1', 'HOLD', '0', '200')], nextCursor: 'n' }),
    });
    open('/me/loyalty');
    expect(await screen.findByText('1.300')).toBeInTheDocument();
    expect(screen.getByText('1.500')).toBeInTheDocument();
    expect(screen.getByText('1 điểm = 100 ₫')).toBeInTheDocument();
    expect(await screen.findByText('Giữ điểm')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Xem thêm' }));
    expect(await screen.findByText('Tích điểm')).toBeInTheDocument();
    expect(screen.getByText('+30')).toBeInTheDocument();
  });

  it('a failed read shows the error, not numbers; an empty history has its own text', async () => {
    mockFetch({
      'GET /api/v1/members/me/loyalty-accounts/current': json(503, { code: 'DEPENDENCY_UNAVAILABLE', message: 'Down.' }),
      'GET /api/v1/members/me/loyalty-transactions': json(200, { items: [], nextCursor: null }),
    });
    open('/me/loyalty');
    expect(await screen.findByText(/tạm thời không phản hồi/)).toBeInTheDocument();
    expect(screen.queryByText('Có thể dùng')).toBeNull();
    expect(await screen.findByText('Bạn chưa có giao dịch điểm nào.')).toBeInTheDocument();
  });

  it('asks a Guest to sign in and sends nothing', async () => {
    const calls = mockFetch({});
    open('/me/loyalty', { session: fakeSession('signed-out') });
    expect(await screen.findByText(/cần đăng nhập bằng tài khoản Member/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe('T070 public offers and voucher wallet', () => {
  it('a Guest sees the cards and is asked to sign in before saving', async () => {
    mockFetch({ 'GET /api/v1/promotions': json(200, { items: [publicPromotion()], nextCursor: null }) });
    open('/promotions', { session: fakeSession('signed-out') });
    expect(await screen.findByText('MLAND10')).toBeInTheDocument();
    expect(screen.getByText('Giảm 10% (tối đa 50.000 ₫)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng nhập để lưu' })).toHaveAttribute('href', '/login?returnTo=%2Fpromotions');
  });

  it('names product targets from the public catalogue, without signing in; a target not found keeps its ID', async () => {
    const calls = mockFetch({
      'GET /api/v1/promotions': json(200, { items: [publicPromotion({ targets: { productIds: ['6', '99'], variantIds: ['23'] } })], nextCursor: null }),
      'GET /api/v1/products/6': json(200, { id: '6', name: 'Nhẫn kiểm thử', variants: [] }),
      'GET /api/v1/products/99': json(404, { code: 'PRODUCT_NOT_FOUND', message: 'Not found.' }),
      'GET /api/v1/products': json(200, { items: [{ id: '6', name: 'Nhẫn kiểm thử', variants: [{ id: '23', size: 14, sku: 'SP000006-S14' }] }], page: 0, pageSize: 20, totalElements: '1' }),
    });
    open('/promotions', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Nhẫn kiểm thử; 99')).toBeInTheDocument();
    expect(await screen.findByText('Nhẫn kiểm thử · size 14')).toBeInTheDocument();
    expect(calls.filter(call => call.path.startsWith('/api/v1/products')).every(call => !call.headers.Authorization)).toBe(true);
  });

  it('names workshop packages and branches without signing in; one that cannot be read shows "Gói #id" / "Chi nhánh #id"', async () => {
    const calls = mockFetch({
      'GET /api/v1/promotions': json(200, { items: [publicPromotion({ scope: 'WORKSHOP', targets: { packageIds: ['2', '7'], branchIds: ['1', '3'] } })], nextCursor: null }),
      'GET /api/v1/workshop-packages/2': json(200, { packageId: 2, name: 'Workshop nhẫn bạc', state: 'PUBLISHED' }),
      'GET /api/v1/workshop-packages/7': json(404, { code: 'PACKAGE_NOT_FOUND', message: 'Not found.' }),
      'GET /api/v1/product-selling-locations': json(200, { items: [{ id: '1', name: 'Mland Hà Nội – Hoàn Kiếm', address: '12 Hàng Bạc' }] }),
    });
    open('/promotions', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Workshop nhẫn bạc; Gói #7')).toBeInTheDocument();
    expect(await screen.findByText('Mland Hà Nội – Hoàn Kiếm; Chi nhánh #3')).toBeInTheDocument();
    expect(screen.getByText('Gói workshop')).toBeInTheDocument();
    expect(screen.getByText('Chi nhánh')).toBeInTheDocument();
    expect(screen.queryByText('Mã gói workshop')).not.toBeInTheDocument();
    const reads = calls.filter(call => call.path.startsWith('/api/v1/workshop-packages') || call.path === '/api/v1/product-selling-locations');
    expect(reads).toHaveLength(3);
    expect(reads.every(call => !call.headers.Authorization)).toBe(true);
  });

  it('a Member saves a voucher with a key', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/promotions': json(200, { items: [publicPromotion()], nextCursor: null }),
      'PUT /api/v1/members/me/saved-vouchers/5': json(200, { promotion: publicPromotion(), savedAt: '2026-11-01T00:00:00Z', availability: 'ACTIVE' }),
    });
    open('/promotions');
    await user.click(await screen.findByRole('button', { name: 'Lưu vào Kho Voucher' }));
    expect(await screen.findByText('Đã lưu.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PUT').headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(calls.filter(call => call.path === '/api/v1/promotions').every(call => !call.headers.Authorization)).toBe(true);
  });

  it('the wallet labels availability and has an empty state', async () => {
    mockFetch({ 'GET /api/v1/members/me/saved-vouchers': json(200, { items: [{ promotion: publicPromotion(), savedAt: '2026-11-01T00:00:00Z', availability: 'PAUSED' }], nextCursor: null }) });
    open('/me/saved-vouchers');
    expect(await screen.findByText('Đang tạm dừng')).toBeInTheDocument();
  });

  it('the wallet says when no use is left, in total or for this Member (US3.5, review 2026-11-10)', async () => {
    mockFetch({ 'GET /api/v1/members/me/saved-vouchers': json(200, { items: [
      { promotion: publicPromotion({ id: '5' }), savedAt: '2026-11-01T00:00:00Z', availability: 'NO_USES_LEFT' },
      { promotion: publicPromotion({ id: '6', code: 'MLAND20' }), savedAt: '2026-11-02T00:00:00Z', availability: 'MEMBER_LIMIT_REACHED' },
    ], nextCursor: null }) });
    open('/me/saved-vouchers');
    expect(await screen.findByText('Đã hết lượt dùng')).toBeInTheDocument();
    expect(screen.getByText('Bạn đã dùng hết lượt của mình')).toBeInTheDocument();
  });

  it('a hidden offer reads as not found', async () => {
    mockFetch({ 'GET /api/v1/promotions/9': json(404, { code: 'PROMOTION_NOT_FOUND', message: 'Not found.' }) });
    open('/promotions/9');
    expect(await screen.findByText('Ưu đãi không tồn tại hoặc không còn công khai')).toBeInTheDocument();
  });
});

describe('T061 offer picker in the workshop context', () => {
  it('shows gross, voucher, points, net, deposit and remaining from the FE-03 quote and has no automatic discount', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    // Shape of FE-03 WorkshopBookingQuoteResponse (develop 8d69792).
    const quote = { grossAmount: '1000000', programDiscount: '100000', pointsUsed: '900', pointDiscount: '90000', postPromotionAmount: '810000', depositAmount: '405000', remainingAmount: '405000', reasons: [] };
    function Host() {
      const [ack, setAck] = useState(false);
      return <BenefitSelector context="workshop" value={{ choice: 'VOUCHER', voucherCode: 'WK10', requestedPoints: '900' }} onChange={() => {}}
        quote={quote} quoteMatches acknowledged={ack} onAcknowledge={setAck} onRecalculate={() => {}} onConfirm={onConfirm} />;
    }
    render(<I18nProvider initialLanguage="vi"><Host /></I18nProvider>);

    expect(screen.queryByLabelText('Giảm giá tự động (nếu có)')).toBeNull();
    expect(screen.getByText('1.000.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('Giảm từ 900 điểm')).toBeInTheDocument();
    expect(screen.getByText('810.000 ₫')).toBeInTheDocument();
    expect(screen.getAllByText('405.000 ₫')).toHaveLength(2);
    await user.click(screen.getByLabelText('Tôi đã xem tổng tiền và ưu đãi ở trên'));
    await user.click(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('a quote with reasons cannot be confirmed', () => {
    const quote = { grossAmount: '1000000', programDiscount: '0', pointsUsed: '5', pointDiscount: '0', postPromotionAmount: '1000000', depositAmount: '500000', remainingAmount: '500000', reasons: ['Points must be between 10 and 1000.'] };
    render(<I18nProvider initialLanguage="vi"><BenefitSelector context="workshop" value={{ choice: 'NONE', voucherCode: '', requestedPoints: '5' }} onChange={() => {}}
      quote={quote} quoteMatches acknowledged onAcknowledge={() => {}} onRecalculate={() => {}} onConfirm={() => {}} /></I18nProvider>);
    expect(screen.getByText('Points must be between 10 and 1000.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();
  });

  it('T081: reasons with codes are shown in the reader language, unknown codes fall back to the text', () => {
    const quote = { grossAmount: '1000000', programDiscount: '0', pointsUsed: '0', pointDiscount: '0', postPromotionAmount: '1000000', depositAmount: '500000', remainingAmount: '500000',
      reasons: ['This promotion has ended.', 'Points must be between 10 and 1000.', 'Some new reason.'],
      reasonDetails: [{ code: 'PROMOTION_ENDED', params: {} }, { code: 'POINTS_OUT_OF_RANGE', params: { min: '10', max: '1000' } }, { code: 'NEW_CODE', params: {} }] };
    const props = { context: 'workshop', value: { choice: 'VOUCHER', voucherCode: 'WK', requestedPoints: '5' }, onChange: () => {}, quote, quoteMatches: true,
      acknowledged: false, onAcknowledge: () => {}, onRecalculate: () => {}, onConfirm: () => {} };
    const { unmount } = render(<I18nProvider initialLanguage="vi"><BenefitSelector {...props} /></I18nProvider>);
    expect(screen.getByText('Chương trình ưu đãi đã kết thúc.')).toBeInTheDocument();
    expect(screen.getByText('Số điểm phải từ 10 đến 1.000.')).toBeInTheDocument();
    expect(screen.getByText('Some new reason.')).toBeInTheDocument();
    expect(screen.queryByText('This promotion has ended.')).not.toBeInTheDocument();
    unmount();
    render(<I18nProvider initialLanguage="en"><BenefitSelector {...props} /></I18nProvider>);
    expect(screen.getByText('This promotion has ended.')).toBeInTheDocument();
    expect(screen.getByText('Points must be between 10 and 1,000.')).toBeInTheDocument();
  });
});

describe('T055 offer picker for checkout', () => {
  function Host({ quote, onRecalculate, onConfirm }) {
    const [value, setValue] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
    const [calculatedFor, setCalculatedFor] = useState(null);
    const [ack, setAck] = useState(false);
    return (
      <BenefitSelector value={value} onChange={next => { setValue(next); setAck(false); }} quote={calculatedFor ? quote : null}
        quoteMatches={JSON.stringify(calculatedFor) === JSON.stringify(value)} acknowledged={ack} onAcknowledge={setAck}
        savedVouchers={[{ code: 'MLAND10', name: 'Giảm 10%', availability: 'ACTIVE' }, { code: 'OLD', name: 'Cũ', availability: 'EXPIRED' }]}
        onRecalculate={() => { onRecalculate(value); setCalculatedFor(value); }} onConfirm={onConfirm} />
    );
  }
  const quote = { merchandiseSubtotal: '300000', discountAmount: '30000', pointsDiscountAmount: '1000', shippingTotal: '0', shippingExcluded: true, payableAmount: '269000', maxPoints: '1350', eligible: true, reasons: ['Points must be between 10 and 1350.'] };

  it('changing a choice sends nothing; Confirm needs a matching quote and the acknowledgement', async () => {
    const user = userEvent.setup();
    const onRecalculate = vi.fn();
    const onConfirm = vi.fn();
    render(<I18nProvider initialLanguage="vi"><Host quote={quote} onRecalculate={onRecalculate} onConfirm={onConfirm} /></I18nProvider>);

    await user.click(screen.getByLabelText('Dùng voucher'));
    expect(screen.getAllByRole('option').map(option => option.textContent)).toEqual(['Chọn voucher đã lưu', 'Giảm 10% (MLAND10)']);
    await user.selectOptions(screen.getByLabelText('Chọn từ Kho Voucher'), 'MLAND10');
    expect(screen.getByLabelText('Mã voucher')).toHaveValue('MLAND10');
    expect(onRecalculate).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Tính lại' }));
    expect(onRecalculate).toHaveBeenCalledWith({ choice: 'VOUCHER', voucherCode: 'MLAND10', requestedPoints: '0' });
    expect(screen.getByText('Tối đa dùng được: 1.350 điểm')).toBeInTheDocument();
    expect(screen.getByText('269.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('Points must be between 10 and 1350.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();
    await user.click(screen.getByLabelText('Tôi đã xem tổng tiền và ưu đãi ở trên'));
    await user.click(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    const points = screen.getByLabelText('Số điểm muốn dùng');
    await user.clear(points);
    await user.type(points, '100');
    expect(screen.getByText('Lựa chọn đã thay đổi. Bấm Tính lại để xem tổng tiền mới.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();
  });
});

describe('T078 session end and escaping (identity mocked: 001 has no Firebase sign-in yet)', () => {
  it('a 401 after one refresh ends the Manager session and hides the data', async () => {
    const session = fakeSession();
    mockFetch({ [`GET ${P}`]: json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'Session expired.' }) });
    open('/promotion-management/programs', { session });
    expect(await screen.findByText('Khu quản lý ưu đãi cần đăng nhập bằng tài khoản Manager.')).toBeInTheDocument();
    expect(session.getIdToken).toHaveBeenCalledWith(true);
    expect(session.signOut).toHaveBeenCalled();
  });

  it('a 401 on My Loyalty ends the Member session', async () => {
    const session = fakeSession();
    mockFetch({
      'GET /api/v1/members/me/loyalty-accounts/current': json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }),
      'GET /api/v1/members/me/loyalty-transactions': json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }),
    });
    open('/me/loyalty', { session });
    expect(await screen.findByText(/cần đăng nhập bằng tài khoản Member/)).toBeInTheDocument();
    expect(session.signOut).toHaveBeenCalled();
    expect(screen.queryByText('1.300')).toBeNull();
  });

  it('names and codes from the server are shown as text, never as markup', async () => {
    const evil = '<img src=x onerror="window.__xss=1">';
    mockFetch({
      [`GET ${P}`]: json(200, { items: [program({ name: evil })], nextCursor: null }),
      'GET /api/v1/promotions': json(200, { items: [publicPromotion({ name: `<script>window.__xss=1</script>`, code: '<b>X</b>' })], nextCursor: null }),
    });
    const manager = open('/promotion-management/programs');
    expect(await screen.findByRole('link', { name: evil })).toBeInTheDocument();
    expect(manager.container.querySelector('img[src="x"]')).toBeNull();
    manager.unmount();
    const guest = open('/promotions', { session: fakeSession('signed-out') });
    expect(await screen.findByText('<b>X</b>')).toBeInTheDocument();
    expect(guest.container.querySelector('script, b')).toBeNull();
    expect(window.__xss).toBeUndefined();
  });
});

describe('voucher code and window helpers', () => {
  it('draws ML + 8 characters without look-alikes, and the window starts on a quarter hour for 30 days', async () => {
    const { generateVoucherCode, defaultWindow } = await import('../domain/promotionRules');
    expect(generateVoucherCode(values => values.fill(0))).toBe('MLAAAAAAAA');
    expect(generateVoucherCode()).toMatch(/^ML[A-HJKMNP-Z2-9]{8}$/);
    expect(defaultWindow(new Date(2026, 10, 1, 21, 7, 30))).toEqual({ startAt: '2026-11-01T21:15', endAt: '2026-12-01T21:15' });
    expect(defaultWindow(new Date(2026, 10, 1, 21, 15, 0))).toEqual({ startAt: '2026-11-01T21:30', endAt: '2026-12-01T21:30' });
  });
});

describe('24-hour time on the programme form', () => {
  it('offers hours 00–23 without AM/PM and an earlier end time is refused before sending', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({});
    open('/promotion-management/programs/new');
    const startHour = await screen.findByLabelText('Bắt đầu — giờ (00–23)');
    const hours = within(startHour).getAllByRole('option').map(option => option.textContent);
    expect(hours[0]).toBe('00 h');
    expect(hours.at(-1)).toBe('23 h');
    expect(hours).toHaveLength(24);

    const date = screen.getByLabelText('Bắt đầu').value;
    await user.selectOptions(startHour, '22');
    await user.selectOptions(screen.getByLabelText('Bắt đầu — phút'), '00');
    const endDate = screen.getByLabelText('Kết thúc');
    await user.clear(endDate);
    await user.type(endDate, date);
    await user.selectOptions(screen.getByLabelText('Kết thúc — giờ (00–23)'), '12');
    expect(screen.getAllByText(/^Tức là: .*, 22:00$/)).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Thời gian kết thúc phải sau thời gian bắt đầu.')).toBeInTheDocument();
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0);
  });
});

describe('scope warnings on the programme detail (clarification 2026-10-26)', () => {
  it('lists products of the scope that can no longer be bought and keeps the programme as it is', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program({
        targets: { productIds: ['43', '99'] }, targetCheck: 'CHECKED',
        targetWarnings: [{ dimension: 'PRODUCT', targetKey: '43', state: 'NOT_PUBLIC' }, { dimension: 'PRODUCT', targetKey: '99', state: 'DELETED' }],
      })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/43`]: json(200, catalogue.items[1]),
      [`GET ${C}/99`]: json(404, { code: 'PRODUCT_NOT_FOUND', message: 'x' }),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Phạm vi có sản phẩm không còn mua được')).toBeInTheDocument();
    expect(await screen.findByText('Nhẫn vàng (#43) — đang ẩn hoặc chưa công khai')).toBeInTheDocument();
    expect(screen.getByText('Sản phẩm #99 — đã xóa')).toBeInTheDocument();
  });

  it('shows nothing when every product is fine and says so when the check is unavailable', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program({ targetCheck: 'CHECKED', targetWarnings: [] })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
    });
    const first = open('/promotion-management/programs/5');
    await screen.findByText('Tình hình sử dụng');
    expect(screen.queryByText('Phạm vi có sản phẩm không còn mua được')).toBeNull();
    first.unmount();

    mockFetch({
      [`GET ${P}/5`]: json(200, program({ targetCheck: 'UNAVAILABLE' })),
      [`GET ${P}/5/usage`]: json(200, voucherUsage),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Chưa kiểm tra được tình trạng sản phẩm trong phạm vi lúc này.')).toBeInTheDocument();
  });
});

describe('create whose answer was lost (FR-007, US1.8)', () => {
  it('after a reload sends the same key and body again and opens the programme the backend replays', async () => {
    const user = userEvent.setup();
    const body = { kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', name: 'Giảm tự động' };
    sessionStorage.setItem('lp.pendingCreate', JSON.stringify({ key: 'k-lost', body }));
    const calls = mockFetch({
      [`POST ${P}`]: json(201, program({ id: '9', kind: 'AUTO_DISCOUNT', code: null })),
      [`GET ${P}/9`]: json(200, program({ id: '9', kind: 'AUTO_DISCOUNT', code: null })),
      [`GET ${P}/9/usage`]: json(200, { programId: '9', acceptedCount: '0', committedDiscountAmount: '0' }),
      [`GET ${C}/42`]: json(200, catalogue.items[0]),
    });
    open('/promotion-management/programs/new');
    expect(await screen.findByText(/Lần tạo chương trình "Giảm tự động" trước đó chưa nhận được phản hồi/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Gửi lại đúng yêu cầu đó' }));

    expect(await screen.findByText('Tình hình sử dụng')).toBeInTheDocument();
    const create = calls.find(call => call.method === 'POST');
    expect(create.headers['Idempotency-Key']).toBe('k-lost');
    expect(create.body).toEqual(body);
    expect(sessionStorage.getItem('lp.pendingCreate')).toBeNull();
  });

  it('keeps the key of a create that got no answer and dismissing forgets it', async () => {
    const user = userEvent.setup();
    sessionStorage.setItem('lp.pendingCreate', JSON.stringify({ key: 'k-2', body: { name: 'X' } }));
    mockFetch({});
    open('/promotion-management/programs/new');
    await user.click(await screen.findByRole('button', { name: 'Bỏ qua' }));
    expect(sessionStorage.getItem('lp.pendingCreate')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Gửi lại đúng yêu cầu đó' })).toBeNull();
  });
});

describe('workshop target picker', () => {
  const PKG = '/api/v1/workshop-package-management/packages';
  const LOC = '/api/v1/workshop-locations';
  const SES = '/api/v1/workshop-sessions';
  const workshopLists = {
    [`GET ${PKG}`]: json(200, [{ packageId: 7, name: 'Silverclay 2 phiên' }, { packageId: 8, name: 'Ring Basic' }]),
    [`GET ${LOC}`]: json(200, [{ id: 1, name: 'Chi nhánh Hà Nội' }, { id: 2, name: 'Chi nhánh Đà Nẵng' }]),
    [`GET ${SES}`]: json(200, [
      { id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', locationId: 1, slotId: 'Sáng', workshopDate: '2026-12-01' },
      { id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', locationId: 2, slotId: 'Chiều', workshopDate: '2026-12-02' },
    ]),
  };
  const fillBasics = async user => {
    await user.type(await screen.findByLabelText('Tên chương trình'), 'Workshop 10%');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.selectOptions(screen.getByLabelText('Phạm vi'), 'WORKSHOP');
  };

  it('offers names instead of IDs and sends no target when everything is "All"', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...workshopLists, [`POST ${P}`]: json(201, program({ id: '9' })), [`GET ${P}/9`]: json(200, program({ id: '9' })), [`GET ${P}/9/usage`]: json(200, voucherUsage) });
    open('/promotion-management/programs/new');
    await fillBasics(user);
    expect(await screen.findByRole('option', { name: 'Silverclay 2 phiên' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Ngày workshop')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Đã tạo chương trình.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({});
  }, 20000);

  it('sends the chosen package, branch and session, and narrows sessions to the branch', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...workshopLists, [`POST ${P}`]: json(201, program({ id: '9' })), [`GET ${P}/9`]: json(200, program({ id: '9' })), [`GET ${P}/9/usage`]: json(200, voucherUsage) });
    open('/promotion-management/programs/new');
    await fillBasics(user);
    await screen.findByRole('option', { name: 'Ring Basic' });
    await user.selectOptions(screen.getByLabelText('Gói workshop'), '8');
    await user.selectOptions(screen.getByLabelText('Chi nhánh'), '2');
    expect(screen.queryByRole('option', { name: /Hà Nội/ })).not.toBeNull(); // branch option itself
    expect(screen.queryByRole('option', { name: '2026-12-01 · Sáng · Chi nhánh Hà Nội' })).toBeNull();
    await user.selectOptions(screen.getByLabelText('Phiên workshop'), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Đã tạo chương trình.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'POST').body.targets).toEqual({
      packageIds: ['8'], branchIds: ['2'], sessionIds: ['bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'],
    });
  }, 20000);
});
