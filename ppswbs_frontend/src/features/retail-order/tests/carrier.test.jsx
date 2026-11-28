import { describe, expect, it } from 'vitest';
import { carrierPeriod, carrierStats, carrierTab } from '../ui/carrier/CarrierPages';
import { messages } from '../messages';

const NOW = Date.parse('2026-11-20T03:00:00Z'); // 20/11 10:00 Vietnam time

describe('Simulated GHTK portal (Session 2026-11-20)', () => {
  it('names each shipment status the way GHTK does', () => {
    expect(messages.vi['carrier.status.PREPARING']).toBe('Đã tiếp nhận');
    expect(messages.vi['carrier.status.READY_FOR_CARRIER']).toBe('Đang lấy hàng');
    expect(messages.en['carrier.status.PREPARING']).toBe('Received');
    expect(carrierTab('HANDED_TO_CARRIER')).toBe('DELIVERING');
    expect(carrierTab('IN_TRANSIT')).toBe('DELIVERING');
    expect(carrierTab('DELIVERED')).toBe('DELIVERED');
  });

  it('cuts periods in Vietnam time, up to now', () => {
    const month = carrierPeriod('MONTH', '2026-11-20', NOW);
    expect(new Date(month.start).toISOString()).toBe('2026-10-31T17:00:00.000Z');
    expect(month.unit).toBe('DAY');
    expect(month.buckets).toHaveLength(20);
    expect(month.buckets[0].label).toBe('01/11');
    const day = carrierPeriod('DAY', '2026-11-19', NOW);
    expect(day.buckets).toHaveLength(24);
    expect(day.buckets[9].label).toBe('09h');
    const week = carrierPeriod('WEEK', '2026-11-20', NOW);
    expect(week.buckets[0].label).toBe('16/11');
  });

  it('counts received, picked up and delivered shipments, delivery time and fees', () => {
    const period = carrierPeriod('MONTH', '2026-11-20', NOW);
    const items = [
      { pickupLocationId: 1, status: 'DELIVERED', createdAt: '2026-11-02T02:00:00Z', pickedUpAt: '2026-11-02T04:00:00Z', deliveredAt: '2026-11-03T04:00:00Z', shippingCharge: '30000' },
      { pickupLocationId: 2, status: 'HANDED_TO_CARRIER', createdAt: '2026-11-05T02:00:00Z', pickedUpAt: '2026-11-05T05:00:00Z', deliveredAt: null, shippingCharge: '25000' },
      { pickupLocationId: 1, status: 'READY_FOR_CARRIER', createdAt: '2026-10-20T02:00:00Z', pickedUpAt: null, deliveredAt: null, shippingCharge: '20000' },
    ];
    const stats = carrierStats(items, period);
    expect(stats.received).toBe(2);
    expect(stats.pickedUp).toBe(2);
    expect(stats.delivered).toBe(1);
    expect(stats.delivering).toBe(1);
    expect(stats.waitingPickup).toBe(1);
    expect(stats.averageHours).toBe(24);
    expect(stats.fees).toBe('30000');
    expect(stats.trend.received[1]).toBe(1);
    expect(stats.trend.delivered[2]).toBe(1);
    expect(stats.byBranch.find(row => row.locationId === 1)).toMatchObject({ received: 1, delivered: 1, fee: '30000' });
  });

  it('counts a delivered shipment with a non-numeric or missing fee as free instead of crashing', () => {
    const period = carrierPeriod('MONTH', '2026-11-20', NOW);
    const items = [
      { pickupLocationId: 1, status: 'DELIVERED', createdAt: '2026-11-02T02:00:00Z', pickedUpAt: '2026-11-02T04:00:00Z', deliveredAt: '2026-11-03T04:00:00Z', shippingCharge: '30000' },
      { pickupLocationId: null, status: 'DELIVERED', createdAt: '2026-11-04T02:00:00Z', pickedUpAt: '2026-11-04T04:00:00Z', deliveredAt: '2026-11-05T04:00:00Z', shippingCharge: 'Miễn phí' },
      { pickupLocationId: null, status: 'DELIVERED', createdAt: '2026-11-06T02:00:00Z', pickedUpAt: '2026-11-06T04:00:00Z', deliveredAt: '2026-11-07T04:00:00Z', shippingCharge: null },
    ];
    const stats = carrierStats(items, period);
    expect(stats.delivered).toBe(3);
    expect(stats.fees).toBe('30000');
    expect(stats.byBranch.find(row => row.locationId === null)).toMatchObject({ delivered: 2, fee: '0' });
  });
});

describe('GHTK home page notice (Session 2026-11-23)', () => {
  it('counts today\'s new shipments in Vietnam time and the ones waiting or on the way', async () => {
    const { carrierTodayCounts } = await import('../../accountmanagement/components/CarrierPortalPage');
    const now = Date.parse('2026-11-23T03:00:00Z'); // 10:00 in Vietnam
    const counts = carrierTodayCounts([
      { status: 'PREPARING', createdAt: '2026-11-22T18:30:00Z' }, // 01:30 on the 23rd in Vietnam
      { status: 'READY_FOR_CARRIER', createdAt: '2026-11-23T02:00:00Z' },
      { status: 'IN_TRANSIT', createdAt: '2026-11-22T16:00:00Z' }, // 23:00 on the 22nd in Vietnam
      { status: 'DELIVERED', createdAt: '2026-11-20T02:00:00Z' },
    ], now);
    expect(counts).toEqual({ today: 2, waitingPickup: 1, delivering: 1 });
  });
});
