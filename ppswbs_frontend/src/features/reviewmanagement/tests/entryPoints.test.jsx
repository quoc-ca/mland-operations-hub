import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { reviewManagementRoutes } from '../routes';

const PROFILE = 'GET /api/v1/accounts/me/profile';
const LIST = 'GET /api/v1/reviews';
const SOURCES = 'GET /api/v1/reviews/eligible-sources';
const empty = json(200, { aggregate: { count: 0, averageRating: 0 }, items: [] });
const open = (path, options) => renderRoutes(reviewManagementRoutes, { path, ...options });

describe('who is offered which way into reviewing', () => {
  beforeEach(() => clearRoleCache());

  it('offers a signed-in Member their own sources and no email/phone lookup', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [LIST]: empty });
    open('/reviews');

    expect(await screen.findByRole('link', { name: 'Nguồn đủ điều kiện' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Tìm giao dịch đủ điều kiện' })).toBeNull();
  });

  it('offers a Guest the email/phone lookup and no Member list', async () => {
    mockFetch({ [LIST]: empty });
    open('/reviews', { session: fakeSession('signed-out') });

    expect(await screen.findByRole('link', { name: 'Tìm giao dịch đủ điều kiện' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Nguồn đủ điều kiện' })).toBeNull();
  });

  it('offers neither to a Staff, who moderates instead', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'STAFF' }), [LIST]: empty });
    open('/reviews');

    await screen.findByText('Đánh giá từ khách hàng');
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('link', { name: 'Tìm giao dịch đủ điều kiện' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Nguồn đủ điều kiện' })).toBeNull();
  });

  it('sends a signed-in Member who opens the Guest lookup to their own list', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [SOURCES]: json(200, { items: [] }) });
    open('/reviews/guest/lookup');

    await waitFor(() => expect(screen.queryByText('Tìm giao dịch đủ điều kiện')).toBeNull());
    expect(await screen.findByText(/Chưa có|trải nghiệm|đủ điều kiện/i)).toBeInTheDocument();
  });

  it('keeps the Guest lookup for a Guest', async () => {
    mockFetch({});
    open('/reviews/guest/lookup', { session: fakeSession('signed-out') });

    expect(await screen.findByText('Tìm giao dịch đủ điều kiện')).toBeInTheDocument();
  });
});
