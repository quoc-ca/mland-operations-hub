import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { BenefitSelector } from '../ui/checkout/BenefitSelector';
import { loyaltyPromotionRoutes } from '../routes/loyaltyPromotionRoutes';
import { usePublicProductPrograms } from '../application/usePublicProductPrograms';
import { checkCreate, createBody } from '../domain/promotionRules';
import { program, publicPromotion, signInAs, voucherUsage } from './fixtures';

// Phase 25/26 (T129, T131, T134, T138; FR-046, FR-048): vouchers that must be saved, on screen.

const P = '/api/v1/promotion-programs';
const MEMBER_PROFILE = { 'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER' }) };
const claim = overrides => publicPromotion({ id: '8', code: 'LUU8', name: 'Phải lưu 8', distribution: 'CLAIM_REQUIRED', ...overrides });
const open = (path, options) => renderRoutes(loyaltyPromotionRoutes, { path, ...options });

beforeEach(() => {
  clearRoleCache();
  signInAs('MANAGER');
});

describe('Kho Voucher with vouchers that must be saved', () => {
  it('reads every distribution and labels a voucher to save and one fully claimed', async () => {
    const calls = mockFetch({
      'GET /api/v1/promotions': json(200, { items: [claim(), claim({ id: '9', code: 'LUU9', availability: 'NO_USES_LEFT' }), publicPromotion({ distribution: 'PUBLIC' })], nextCursor: null }),
    });
    open('/promotions', { session: fakeSession('signed-out') });
    expect(await screen.findByText('LUU8')).toBeInTheDocument();
    expect(calls.find(call => call.path === '/api/v1/promotions').query.distribution).toBe('CLAIM_REQUIRED');
    expect(screen.getAllByText('Lưu mã để dùng')).toHaveLength(2);
    expect(screen.getAllByText('Đã hết lượt lưu').length).toBeGreaterThan(0);
  });

  it('a refused claim says the voucher is fully claimed instead of a generic error (T131)', async () => {
    const user = userEvent.setup();
    mockFetch({
      ...MEMBER_PROFILE,
      'GET /api/v1/promotions': json(200, { items: [claim()], nextCursor: null }),
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [], nextCursor: null }),
      'PUT /api/v1/members/me/saved-vouchers/8': json(422, { code: 'PROMOTION_INELIGIBLE', message: 'The promotion cannot be applied to this purchase.', fieldErrors: [{ field: 'reason', message: 'VOUCHER_NO_USES_LEFT' }] }),
    });
    open('/promotions');
    await user.click(await screen.findByRole('button', { name: 'Lưu voucher' }));
    expect(await screen.findByText('Đã hết lượt lưu')).toBeInTheDocument();
    expect(screen.queryByText('Ưu đãi không áp dụng được cho đơn này.')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Lưu voucher' })).toBeNull();
  });

  it('a public voucher page offers no save button: it needs no saving (Session 2026-11-23)', async () => {
    mockFetch({
      ...MEMBER_PROFILE,
      'GET /api/v1/promotions/5': json(200, publicPromotion({ distribution: 'PUBLIC' })),
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [], nextCursor: null }),
      'GET /api/v1/products': json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      'GET /api/v1/products/42': json(404, { code: 'PRODUCT_NOT_FOUND', message: 'Not found.' }),
    });
    open('/promotions/5');
    expect(await screen.findByText('Mã công khai: không cần lưu, chọn thẳng khi thanh toán.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Lưu voucher' })).toBeNull();
  });
});

describe('"Voucher của tôi" scope filter (Session 2026-11-23)', () => {
  it('filters saved vouchers by products or workshops, like Kho Voucher', async () => {
    const user = userEvent.setup();
    mockFetch({
      ...MEMBER_PROFILE,
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [
        { promotion: publicPromotion({ id: '5', code: 'SPCODE', name: 'Mã sản phẩm' }), savedAt: '2026-11-01T00:00:00Z', availability: 'ACTIVE' },
        { promotion: publicPromotion({ id: '6', code: 'WSCODE', name: 'Mã workshop', scope: 'WORKSHOP', targets: { packageIds: ['2'] } }), savedAt: '2026-11-02T00:00:00Z', availability: 'ACTIVE' },
      ], nextCursor: null }),
      'GET /api/v1/products': json(200, { items: [], page: 0, pageSize: 20, totalElements: '0' }),
      'GET /api/v1/products/42': json(404, { code: 'PRODUCT_NOT_FOUND', message: 'Not found.' }),
      'GET /api/v1/workshop-packages/2': json(404, { code: 'PACKAGE_NOT_FOUND', message: 'Not found.' }),
    });
    open('/me/saved-vouchers');
    expect(await screen.findByText('SPCODE')).toBeInTheDocument();
    expect(screen.getByText('WSCODE')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Workshop' }));
    expect(screen.queryByText('SPCODE')).toBeNull();
    expect(screen.getByText('WSCODE')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Sản phẩm' }));
    expect(screen.getByText('SPCODE')).toBeInTheDocument();
    expect(screen.queryByText('WSCODE')).toBeNull();
  });
});

describe('Manager form and detail', () => {
  const form = {
    kind: 'VOUCHER', scope: 'PRODUCT', name: 'Phải lưu', code: 'MLABCDEFGH', startAt: '2026-11-01T00:00', endAt: '2026-12-01T00:00',
    discountKind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000', amount: '', minimumEligibleAmount: '0', allowPoints: true,
    totalLimit: '10', perMemberLimit: '1', distribution: 'CLAIM_REQUIRED', productIds: '42', variantIds: '', branchMode: 'ALL', branchIds: [],
  };

  it('sends the distribution and refuses more than one use per Member for a voucher that must be saved', () => {
    expect(createBody(form).distribution).toBe('CLAIM_REQUIRED');
    expect(checkCreate(form).perMemberLimit).toBeUndefined();
    expect(checkCreate({ ...form, perMemberLimit: '2' }).perMemberLimit).toBe('validation.claimOnePerMember');
    expect(createBody({ ...form, distribution: undefined }).distribution).toBe('PUBLIC');
  });

  it('the detail shows the distribution and the saved uses not used yet', async () => {
    mockFetch({
      [`GET ${P}/5`]: json(200, program({ distribution: 'CLAIM_REQUIRED' })),
      [`GET ${P}/5/usage`]: json(200, { ...voucherUsage, claimedCount: '4' }),
    });
    open('/promotion-management/programs/5');
    expect(await screen.findByText('Phải lưu — lưu là giữ suất')).toBeInTheDocument();
    expect(screen.getByText('Đã lưu, chưa dùng')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
  });
});

describe('offer picker on the stacking model (T138, FR-015)', () => {
  it('offers no voucher or a voucher on top, shows both discounts and translates VOUCHER_NOT_SAVED', () => {
    const quote = { merchandiseSubtotal: '1000000', discountAmount: '172000', autoDiscountAmount: '100000', voucherDiscountAmount: '72000', pointsDiscountAmount: '0', shippingTotal: '0', shippingExcluded: true, payableAmount: '828000', maxPoints: '0', eligible: true, reasons: ['x'], reasonDetails: [{ code: 'VOUCHER_NOT_SAVED', params: {} }] };
    render(<I18nProvider initialLanguage="vi"><BenefitSelector value={{ choice: 'AUTO', voucherCode: '', requestedPoints: '0' }} onChange={() => {}} quote={quote} quoteMatches acknowledged={false} onAcknowledge={() => {}} onRecalculate={() => {}} onConfirm={() => {}} /></I18nProvider>);
    expect(screen.getAllByRole('radio')).toHaveLength(2);
    expect(screen.getByLabelText('Không dùng voucher (giảm tự động vẫn được áp)')).toBeChecked();
    expect(screen.getByText('−100.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('−72.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('Hãy lưu voucher này ở Kho Voucher trước khi dùng.')).toBeInTheDocument();
  });
});

describe('wallet read for displayed prices (FR-048)', () => {
  function Probe() {
    const programs = usePublicProductPrograms();
    return <p data-testid="programs">{programs.map(item => `${item.id}:${item.saved ? 'saved' : 'public'}`).join(',')}</p>;
  }
  const routes = [{ path: '/probe', element: <Probe /> }];

  it('a Member gets saved vouchers marked; a failed wallet read leaves the Guest view', async () => {
    mockFetch({
      ...MEMBER_PROFILE,
      'GET /api/v1/promotions': json(200, { items: [publicPromotion({ id: '5', distribution: 'PUBLIC' })], nextCursor: null }),
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [{ promotion: claim(), savedAt: '2026-11-22T00:00:00Z', availability: 'ACTIVE' }], nextCursor: null }),
    });
    renderRoutes(routes, { path: '/probe' });
    expect(await screen.findByText('5:public,8:saved')).toBeInTheDocument();
  });

  it('keeps the public programmes when the wallet answers with an error', async () => {
    clearRoleCache();
    mockFetch({
      ...MEMBER_PROFILE,
      'GET /api/v1/promotions': json(200, { items: [publicPromotion({ id: '5', distribution: 'PUBLIC' })], nextCursor: null }),
      'GET /api/v1/members/me/saved-vouchers': json(500, { code: 'INTERNAL_ERROR', message: 'Boom.' }),
    });
    renderRoutes(routes, { path: '/probe' });
    expect(await screen.findByText('5:public')).toBeInTheDocument();
  });
});
