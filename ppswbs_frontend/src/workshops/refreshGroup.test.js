import { afterEach, describe, expect, it, vi } from 'vitest';
import { json, mockFetch } from '../test/render';
import { refreshBookingGroup } from './refreshGroup';

describe('refreshBookingGroup', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('reads the group of a Member with their own session', async () => {
    const calls = mockFetch({ 'GET /api/v1/workshop-booking-groups/GRP-1': json(200, { groupCode: 'GRP-1', groupStatus: 'EXPIRED' }) });
    const group = await refreshBookingGroup({ groupCode: 'GRP-1', mode: 'MEMBER', memberAuthorization: async () => 'Bearer t', contact: {} });
    expect(group.groupStatus).toBe('EXPIRED');
    expect(calls[0].headers?.Authorization || calls[0].headers?.authorization).toBe('Bearer t');
  });

  it('reads the group of a Guest with the e-mail as the one proof', async () => {
    const calls = mockFetch({ 'POST /api/v1/workshop-booking-group-lookups': json(200, { groupCode: 'GRP-1', groupStatus: 'EXPIRED' }) });
    const group = await refreshBookingGroup({ groupCode: 'GRP-1', mode: 'GUEST', memberAuthorization: async () => null, contact: { contactEmail: 'a@b.vn', contactPhone: '0901234567', contactPhoneCountry: 'VN' } });
    expect(group?.groupStatus).toBe('EXPIRED');
    expect(JSON.stringify(calls[0].body)).toContain('a@b.vn');
    expect(JSON.stringify(calls[0].body)).not.toContain('0901234567');
  });

  it('returns null instead of throwing when the group cannot be read', async () => {
    mockFetch({ 'GET /api/v1/workshop-booking-groups/GRP-1': json(503, { code: 'DEPENDENCY_UNAVAILABLE' }) });
    expect(await refreshBookingGroup({ groupCode: 'GRP-1', mode: 'MEMBER', memberAuthorization: async () => 'Bearer t', contact: {} })).toBeNull();
    expect(await refreshBookingGroup({ groupCode: '', mode: 'MEMBER', memberAuthorization: async () => 'x', contact: {} })).toBeNull();
  });
});

import { groupResultHeading } from './groupDisplay';

describe('groupResultHeading', () => {
  it('words the heading for the status and the real number of tickets', () => {
    expect(groupResultHeading({ groupStatus: 'PENDING_PAYMENT', tickets: [{}] }).title).toBe('Đã giữ chỗ cho 1 vé');
    expect(groupResultHeading({ groupStatus: 'PENDING_PAYMENT', tickets: [{}, {}, {}] }).title).toBe('Đã giữ chỗ cho 3 vé');
    expect(groupResultHeading({ groupStatus: 'EXPIRED', tickets: [{}] }).title).toBe('Giữ chỗ đã hết hạn');
    expect(groupResultHeading({ groupStatus: 'CANCELLED' }).title).toBe('Đặt chỗ đã được hủy');
    expect(groupResultHeading({ groupStatus: 'CONFIRMED' }).title).toBe('Đặt chỗ đã được xác nhận');
  });
});
