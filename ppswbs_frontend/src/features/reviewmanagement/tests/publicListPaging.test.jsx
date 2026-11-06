import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { reviewManagementRoutes } from '../routes';

const review = (id, text, rating = 5) => ({
  id, targetType: 'WORKSHOP_SESSION', targetReference: 'S-1', rating, bodyText: text, status: 'PUBLIC',
  maskedDisplayName: 'Hội viên ***', createdAt: '2026-11-05T10:00:00Z', version: 0,
});
const open = () => renderRoutes(reviewManagementRoutes, { path: '/reviews', session: fakeSession('signed-out') });

describe('public review list: show more follows the backend cursor', () => {
  beforeEach(() => clearRoleCache());

  it('loads the list with no target, then appends the next page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/reviews': call => (call.query.cursor
        ? json(200, { aggregate: { count: 3, averageRating: 4.5 }, items: [review('r3', 'Ba')], totalElements: 3, nextCursor: null })
        : json(200, { aggregate: { count: 3, averageRating: 4.5 }, items: [review('r1', 'Một'), review('r2', 'Hai')], totalElements: 3, nextCursor: 'CUR-1' })),
    });
    open();

    expect(await screen.findByText('Một')).toBeInTheDocument();
    expect(screen.getByText('2 / 3')).toBeInTheDocument();
    expect(calls[0].query.targetType).toBeUndefined();
    expect(calls[0].query.targetReference).toBeUndefined();

    await user.click(screen.getByRole('button', { name: 'Xem thêm đánh giá' }));

    expect(await screen.findByText('Ba')).toBeInTheDocument();
    expect(screen.getByText('Một')).toBeInTheDocument(); // the first page stays
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
    expect(calls.find(call => call.query.cursor).query.cursor).toBe('CUR-1');
    expect(screen.queryByRole('button', { name: 'Xem thêm đánh giá' })).toBeNull();
  });

  it('has no "show more" when everything fits on one page', async () => {
    mockFetch({ 'GET /api/v1/reviews': json(200, { aggregate: { count: 1, averageRating: 5 }, items: [review('r1', 'Một')], totalElements: 1, nextCursor: null }) });
    open();

    await screen.findByText('Một');
    expect(screen.queryByRole('button', { name: 'Xem thêm đánh giá' })).toBeNull();
  });

  it('starts over from the first page when the rating filter changes', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/reviews': call => (call.query.rating === '4'
        ? json(200, { aggregate: { count: 2, averageRating: 4.5 }, items: [review('r9', 'Bốn sao', 4)], totalElements: 1, nextCursor: null })
        : json(200, { aggregate: { count: 2, averageRating: 4.5 }, items: [review('r1', 'Năm sao')], totalElements: 2, nextCursor: 'CUR-1' })),
    });
    open();
    await screen.findByText('Năm sao');

    await user.selectOptions(screen.getByLabelText(/Số sao/), '4');

    expect(await screen.findByText('Bốn sao')).toBeInTheDocument();
    expect(screen.queryByText('Năm sao')).toBeNull();
    expect(calls.some(call => call.query.rating === '4' && !call.query.cursor)).toBe(true);
  });
});
