import { describe, expect, it } from 'vitest';
import { orderTab, statusLabel, tabAfterPlacing } from '../ui/orders/MemberOrdersPage';

const order = overrides => ({ paymentState: 'PAID', paymentMethod: 'VNPAY', fulfilmentMethod: 'GHTK', fulfilmentState: 'SHIPMENT_PENDING', ...overrides });

describe('FE-08 Member order stages (Session 2026-11-18)', () => {
  it('moves a GHTK order from Chờ lấy hàng to Chờ giao hàng to Đã giao', () => {
    expect(orderTab(order())).toBe('AWAITING_COLLECTION');
    expect(statusLabel(order())).toBe('Đang chuẩn bị hàng');
    expect(statusLabel(order({ fulfilmentState: 'READY_FOR_CARRIER' }))).toBe('Chờ GHTK lấy hàng');
    expect(orderTab(order({ fulfilmentState: 'HANDED_TO_CARRIER' }))).toBe('AWAITING_DELIVERY');
    expect(orderTab(order({ fulfilmentState: 'IN_TRANSIT' }))).toBe('AWAITING_DELIVERY');
    expect(orderTab(order({ fulfilmentState: 'DELIVERED' }))).toBe('COMPLETED');
    expect(statusLabel(order({ fulfilmentState: 'DELIVERED' }))).toBe('Giao thành công');
  });

  it('keeps a pickup order in Chờ nhận tại cửa hàng until it is collected', () => {
    const pickup = order({ fulfilmentMethod: 'PICKUP', fulfilmentState: null });
    expect(orderTab(pickup)).toBe('AWAITING_PICKUP');
    expect(statusLabel(pickup)).toBe('Đang chuẩn bị hàng');
    expect(statusLabel({ ...pickup, fulfilmentState: 'READY_FOR_PICKUP' })).toBe('Sẵn sàng nhận hàng');
    expect(statusLabel({ ...pickup, paymentMethod: 'CASH_AT_COUNTER', paymentState: 'ACCEPTED_UNPAID' })).toBe('Trả tiền khi nhận');
    expect(orderTab({ ...pickup, fulfilmentState: 'PICKED_UP' })).toBe('COMPLETED');
    expect(statusLabel({ ...pickup, fulfilmentState: 'PICKED_UP' })).toBe('Đã nhận tại cửa hàng');
  });

  it('puts an unpaid VNPay order, even one opened at VNPay, in Chờ thanh toán', () => {
    expect(orderTab(order({ paymentState: 'ACCEPTED_UNPAID' }))).toBe('AWAITING_PAYMENT');
    expect(orderTab(order({ paymentState: 'PAYMENT_INITIATED' }))).toBe('AWAITING_PAYMENT');
    expect(orderTab(order({ paymentState: 'EXPIRED' }))).toBe('CANCELLED');
  });

  it('opens the tab of what was just bought, or Tất cả when delivery kinds are mixed', () => {
    expect(tabAfterPlacing([order()])).toBe('AWAITING_COLLECTION');
    expect(tabAfterPlacing([order({ fulfilmentMethod: 'PICKUP' })])).toBe('AWAITING_PICKUP');
    expect(tabAfterPlacing([order(), order({ fulfilmentMethod: 'PICKUP' })])).toBe('ALL');
  });
});
