// Response shapes copied from the backend DTOs (PromotionReadDtos, PublicPromotionDtos, LoyaltyAccountDtos, LoyaltyPolicyDtos).
export function program(overrides = {}) {
  return {
    id: '5', version: '2', revision: '1', kind: 'VOUCHER', scope: 'PRODUCT', code: 'MLAND10', status: 'ENABLED', name: 'Giảm 10%',
    startAt: '2026-11-01T00:00:00Z', endAt: '2026-12-01T00:00:00Z',
    discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' },
    minimumEligibleAmount: '0', allowPoints: true, targets: { productIds: ['42'] }, totalLimit: '100', perMemberLimit: '1',
    createdAt: '2026-10-30T00:00:00Z', ...overrides,
  };
}

export const voucherUsage = { programId: '5', totalLimit: '100', perMemberLimit: '1', heldCount: '3', consumedCount: '7', committedDiscountAmount: '350000' };

export function publicPromotion(overrides = {}) {
  const { id, kind, scope, code, name, startAt, endAt, discount, minimumEligibleAmount, allowPoints, targets, totalLimit, perMemberLimit } = program();
  return { id, kind, scope, code, name, startAt, endAt, discount, minimumEligibleAmount, allowPoints, targets, totalLimit, perMemberLimit, ...overrides };
}

export const policy = { id: '1', version: '3', earnUnitAmount: '10000', redeemPointValue: '100', minRedeemPoints: '10', maxRedeemPercent: '50', roundingMode: 'FLOOR', createdAt: '2026-10-01T00:00:00Z' };

export const account = { balancePoints: '1500', heldPoints: '200', availablePoints: '1300', policy };

export const transaction = (id, kind, balanceChange, heldChange) => ({ id, kind, sourceKind: 'RETAIL', sourceRef: '77', balanceChange, heldChange, occurredAt: '2026-10-31T03:00:00Z', originalDebitId: null });
