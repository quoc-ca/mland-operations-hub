import React from 'react';
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import WorkshopReviewSummary from '../ui/WorkshopReviewSummary';

describe('F11 booking workshop review summary', () => {
  it('loads reviews only for the selected workshop session and keeps CTA disabled before attendance', async () => {
    const calls = mockFetch({
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 2, averageRating: 4.5 },
        items: [{ id: 'review-1', rating: 5, bodyText: 'Workshop tuyệt vời', maskedDisplayName: 'K***h' }],
      }),
    });

    renderRoutes([
      { path: '/booking-review', element: <WorkshopReviewSummary targetReference="SESSION-101" /> },
    ], { path: '/booking-review', session: fakeSession('signed-out') });

    expect(await screen.findByText('Workshop tuyệt vời')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Available after attendance/i })).toBeDisabled();
    expect(calls).toHaveLength(1);
    expect(calls[0].query).toMatchObject({
      targetType: 'WORKSHOP_SESSION',
      targetReference: 'SESSION-101',
    });
  });

  it('does not call the review API when no concrete session reference exists', async () => {
    const calls = mockFetch({});
    renderRoutes([
      { path: '/booking-review', element: <WorkshopReviewSummary /> },
    ], { path: '/booking-review', session: fakeSession('signed-out') });

    expect(screen.queryByRole('heading', { name: /Khách hàng nói gì/i })).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
