import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

/** 003.1 T160 (FR-056, Q7, SC-009(c); 003.2 FR-039): the branch choice follows the server's list of sold branches. */

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 };
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
const SESSIONS = [{ sessionReference: SESSION_ONE, locationId: 2, locationName: 'MLAND Hoàn Kiếm', scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 4 }];

const CONFLICT = json(409, { code: 'PACKAGE_NOT_AVAILABLE_AT_LOCATION', detail: 'Package is not sold at this location' });
const sessionCalls = calls => calls.filter(call => call.path === '/api/v1/workshop-packages/5/sessions');

describe('003.1 FR-056 branch choice only from the list of branches that sell the Package', () => {
  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('offers only the branches the server lists', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Hoàn Kiếm' }, { id: 3, name: 'MLAND Tây Hồ' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    const select = await screen.findByLabelText('Chi nhánh');
    await waitFor(() => expect(select).toHaveValue('2'));
    expect(Array.from(select.querySelectorAll('option')).map(option => option.textContent))
      .toEqual(['Chọn chi nhánh', 'MLAND Hoàn Kiếm', 'MLAND Tây Hồ']);
  });

  it('a 409 on the sessions drops the branch and sessions, reloads the branch list, shows a short message, no retry', async () => {
    let locationReads = 0;
    const calls = mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      // A stale page: the first list still has the branch, the sessions answer 409, the reloaded list no longer has it.
      'GET /api/v1/workshop-packages/5/locations': call => {
        locationReads += 1;
        return json(200, locationReads === 1 ? [{ id: 2, name: 'MLAND Hoàn Kiếm' }] : [{ id: 3, name: 'MLAND Tây Hồ' }]);
      },
      'GET /api/v1/workshop-packages/5/sessions': CONFLICT,
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5?locationId=2', session: fakeSession('signed-out') });

    expect(await screen.findByText(/không còn bán tại chi nhánh bạn đã chọn/)).toBeInTheDocument();
    // Back at the branch choice: no branch picked for the customer, no sessions, no technical error.
    expect(screen.getByLabelText('Chi nhánh')).toHaveValue('');
    expect(screen.queryByText(/Package is not sold/)).toBeNull();
    expect(screen.queryByText(/PACKAGE_NOT_AVAILABLE_AT_LOCATION/)).toBeNull();
    // The list was read again, and the same sessions call was not repeated.
    await waitFor(() => expect(calls.filter(call => call.path === '/api/v1/workshop-packages/5/locations').length).toBeGreaterThanOrEqual(2));
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(sessionCalls(calls)).toHaveLength(1);
  });

  it('a branch in the URL that is not in the reloaded list is dropped with the same message', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 3, name: 'MLAND Tây Hồ' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5?locationId=2', session: fakeSession('signed-out') });

    expect(await screen.findByText(/không còn bán tại chi nhánh bạn đã chọn/)).toBeInTheDocument();
    expect(screen.getByLabelText('Chi nhánh')).toHaveValue('');
    expect(sessionCalls(calls)).toHaveLength(0);
  });

  it('an empty list says the Package is sold nowhere and offers no booking control', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, []),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    expect(await screen.findByText(/chưa được bán tại chi nhánh nào/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt ngay' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Thêm vào giỏ' })).toBeNull();
  });

  it('"Đặt ngay" with a 409 on the sessions shows the message and a way back, no booking control', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Hoàn Kiếm' }]),
      'GET /api/v1/workshop-packages/5/sessions': CONFLICT,
    });
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=2`, session: fakeSession('signed-out') });

    expect(await screen.findByText(/không còn bán tại chi nhánh bạn đã chọn/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chọn chi nhánh khác' })).toHaveAttribute('href', '/workshops/5');
    expect(screen.queryByRole('button', { name: 'Xác nhận và giữ chỗ' })).toBeNull();
  });

  it('"Đặt ngay" with a branch missing from the list is handled the same way', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 3, name: 'MLAND Tây Hồ' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
    });
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=2`, session: fakeSession('signed-out') });

    expect(await screen.findByText(/không còn bán tại chi nhánh bạn đã chọn/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chọn chi nhánh khác' })).toBeInTheDocument();
  });

  it('"Đặt ngay" with an empty list says the Package is sold nowhere', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, []),
      'GET /api/v1/workshop-packages/5/sessions': json(200, []),
    });
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=2`, session: fakeSession('signed-out') });

    expect(await screen.findByText(/chưa được bán tại chi nhánh nào/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Chọn chi nhánh khác' })).toBeNull();
  });
});
