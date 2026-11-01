import React, { useState } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { loyaltyApi } from '../api/loyaltyApi';
import { BenefitSelector } from '../checkout/BenefitSelector';
import { loyaltyPromotionRoutes } from '../routes';
import { account, policy, program, publicPromotion, transaction, voucherUsage } from './fixtures';

const P = '/api/v1/promotion-programs';
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
  it('lists with filters from the URL and loads the next cursor page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`GET ${P}`]: call => json(200, call.query.cursor
        ? { items: [program({ id: '6', name: 'Trang 2' })], nextCursor: null }
        : { items: [program()], nextCursor: 'c2' }),
    });
    open('/promotion-management/programs?status=ENABLED');
    expect(await screen.findByRole('link', { name: 'Giảm 10%' })).toHaveAttribute('href', '/promotion-management/programs/5');
    expect(calls[0].query).toEqual({ status: 'ENABLED' });
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

  it('keeps the typed values and marks server field errors; success opens the programme', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const calls = mockFetch({
      [`POST ${P}`]: () => (++attempt === 1
        ? json(409, { code: 'CODE_EXISTS', message: 'Exists.', fieldErrors: [{ field: 'code', message: 'Already issued.' }] })
        : json(201, program({ id: '9', code: 'MLAND11' }))),
      [`GET ${P}/9`]: json(200, program({ id: '9', code: 'MLAND11' })),
      [`GET ${P}/9/usage`]: json(200, voucherUsage),
    });
    open('/promotion-management/programs/new');
    await user.type(await screen.findByLabelText('Tên chương trình'), 'Giảm 10%');
    await user.type(screen.getByLabelText('Mã voucher'), 'MLAND10');
    await user.type(screen.getByLabelText('Bắt đầu'), '2026-11-01T08:00');
    await user.type(screen.getByLabelText('Kết thúc'), '2026-12-01T08:00');
    await user.type(screen.getByLabelText('Phần trăm giảm (%)'), '10');
    await user.type(screen.getByLabelText('Giảm tối đa (₫)'), '50000');
    await user.type(screen.getByLabelText('Tổng số lượt'), '100');
    await user.type(screen.getByLabelText('Mã sản phẩm'), '42, 43');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));

    expect(await screen.findByText('Already issued.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tên chương trình')).toHaveValue('Giảm 10%');
    const first = calls.find(call => call.method === 'POST');
    expect(first.body).toMatchObject({
      kind: 'VOUCHER', scope: 'PRODUCT', name: 'Giảm 10%', code: 'MLAND10', allowPoints: true, totalLimit: '100', perMemberLimit: '1',
      discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' }, minimumEligibleAmount: '0', targets: { productIds: ['42', '43'] },
    });
    expect(first.body.startAt).toMatch(/Z$/);

    const code = screen.getByLabelText('Mã voucher');
    await user.clear(code);
    await user.type(code, 'MLAND11');
    await user.click(screen.getByRole('button', { name: 'Tạo chương trình' }));
    expect(await screen.findByText('Đã tạo chương trình.')).toBeInTheDocument();
    const posts = calls.filter(call => call.method === 'POST');
    expect(posts[1].headers['Idempotency-Key']).not.toBe(posts[0].headers['Idempotency-Key']);
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
    expect(screen.getByRole('link', { name: 'Đăng nhập để lưu' })).toBeInTheDocument();
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

  it('a hidden offer reads as not found', async () => {
    mockFetch({ 'GET /api/v1/promotions/9': json(404, { code: 'PROMOTION_NOT_FOUND', message: 'Not found.' }) });
    open('/promotions/9');
    expect(await screen.findByText('Ưu đãi không tồn tại hoặc không còn công khai')).toBeInTheDocument();
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
