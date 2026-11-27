import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { StaffOrders } from '../ui/manager/StaffRetailOrdersPage';

// U4 / C2 (Thinh, 2026-11-26, T090): counter cash and the counter handover are Staff only
// (RetailCashCollectionService.requireActiveStaff, PickupOperationsService.handOver); Staff and Manager both pack orders.
let role = 'STAFF';
vi.mock('../../../shared/auth/useRole', () => ({ useRole: () => role }));

const order = (id, overrides = {}) => ({
  id, orderCode: `ML-${id}`, locationId: 1, paymentMethod: 'CASH_AT_COUNTER', paymentState: 'PAID', payableAmount: '1000000',
  orderedAt: '2026-11-26T02:00:00Z', items: [], ...overrides,
});
const pickups = [
  { order: order('1', { paymentState: 'ACCEPTED_UNPAID', counterPaymentDeadline: '2026-11-29T02:00:00Z' }), fulfilmentState: 'AWAITING_PICKUP', pickupReadyConfirmed: false },
  { order: order('2', { paymentMethod: 'VNPAY' }), fulfilmentState: 'AWAITING_PICKUP', pickupReadyConfirmed: false },
  { order: order('3', { paymentMethod: 'VNPAY' }), fulfilmentState: 'AWAITING_PICKUP', pickupReadyConfirmed: true },
];
const shipment = { id: 's1', branchOrderId: '4', orderCode: 'ML-4', status: 'PREPARING', pickupLocationId: 1, createdAt: '2026-11-26T02:00:00Z' };

function renderAs(actor) {
  role = actor;
  mockFetch({
    'GET /api/v1/staff/retail-pickups': json(200, pickups),
    'GET /api/v1/staff/retail-shipments': call => json(200, { items: call.query.status === 'PREPARING' ? [shipment] : [] }),
    'GET /api/v1/product-selling-locations': json(200, []),
  });
  renderRoutes([{ path: '/staff/retail-orders', element: <StaffOrders initialTab="AWAITING_CASH" /> }], { path: '/staff/retail-orders' });
}

describe('Counter buttons on the branch retail orders', () => {
  it('Staff confirms cash at the counter', async () => {
    renderAs('STAFF');
    expect(await screen.findByRole('button', { name: /^Xác nhận đã thu/ })).toBeInTheDocument();
    expect(screen.getByText(/bấm "Xác nhận đã thu"/)).toBeInTheDocument();
  });

  it('Manager sees the order awaiting cash but no cash button', async () => {
    renderAs('MANAGER');
    expect(await screen.findByText('ML-1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Xác nhận đã thu/ })).toBeNull();
    expect(screen.queryByText(/bấm "Xác nhận đã thu"/)).toBeNull();
  });

  it('Staff packs a pickup order and hands it over', async () => {
    renderAs('STAFF');
    (await screen.findByRole('tab', { name: /Cần chuẩn bị/ })).click();
    expect(await screen.findAllByRole('button', { name: 'Đã chuẩn bị xong' })).toHaveLength(2);
    (await screen.findByRole('tab', { name: /Chờ khách nhận/ })).click();
    expect(await screen.findByRole('button', { name: 'Giao cho khách' })).toBeInTheDocument();
  });

  it('Manager packs the pickup order and the GHTK parcel but has no handover button', async () => {
    renderAs('MANAGER');
    (await screen.findByRole('tab', { name: /Cần chuẩn bị/ })).click();
    expect(await screen.findAllByRole('button', { name: 'Đã chuẩn bị xong' })).toHaveLength(2);
    expect(screen.getByText('ML-2')).toBeInTheDocument();
    (await screen.findByRole('tab', { name: /Chờ khách nhận/ })).click();
    expect(await screen.findByText('ML-3')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Giao cho khách' })).toBeNull();
  });
});
