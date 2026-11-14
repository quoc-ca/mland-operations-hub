import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { reviewManagementRoutes } from '../routes';

const review = (id, packageName) => ({
  id, targetType: 'WORKSHOP_SESSION', targetReference: 'S-1', packageId: 145, packageName, rating: 5, bodyText: `nội dung ${id}`,
  status: 'PUBLIC', maskedDisplayName: 'H*** Quốc', createdAt: '2026-11-05T10:00:00Z', version: 0,
});
const open = path => renderRoutes(reviewManagementRoutes, { path, session: fakeSession('signed-out') });

describe('reviews say which package they are about', () => {
  it('shows the package name on each workshop review', async () => {
    mockFetch({ 'GET /api/v1/reviews': json(200, { aggregate: { count: 1, averageRating: 5 }, items: [review('r1', 'Signature Workshop - Silver 925')], totalElements: 1 }) });
    open('/reviews');

    expect(await screen.findByText('nội dung r1')).toBeInTheDocument();
    expect(screen.getByText('Signature Workshop - Silver 925')).toBeInTheDocument();
    expect(screen.getByText(/Gói:/)).toBeInTheDocument();
  });

  it('lists only one package when opened with ?packageId, and says so', async () => {
    const calls = mockFetch({ 'GET /api/v1/reviews': json(200, { aggregate: { count: 1, averageRating: 5 }, items: [review('r1', 'Signature Workshop - Silver 925')], totalElements: 1 }) });
    open('/reviews?packageId=145');

    expect(await screen.findByText('Đánh giá của gói: Signature Workshop - Silver 925')).toBeInTheDocument();
    expect(calls[0].query.packageId).toBe('145');
    expect(screen.getByRole('link', { name: 'Xem đánh giá của tất cả các gói' })).toHaveAttribute('href', '/reviews');
  });

  it('ignores a package id that is not a number', async () => {
    const calls = mockFetch({ 'GET /api/v1/reviews': json(200, { aggregate: { count: 0, averageRating: 0 }, items: [], totalElements: 0 }) });
    open('/reviews?packageId=abc');

    expect(await screen.findByText('Đánh giá từ khách hàng')).toBeInTheDocument();
    expect(calls[0].query.packageId).toBeUndefined();
  });
});
