import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { fakeSession, json, mockFetch } from '../test/render';
import { WorkshopListPage } from './WorkshopListPage';

describe('workshop list page', () => {
  it('shows the workshop reviews at the bottom without any click', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-packages': json(200, [{ packageId: 145, name: 'Silver 925', publicPrice: 900000, minParticipants: 1, maxParticipants: 8 }]),
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 4, averageRating: 4.5 }, totalElements: 4,
        items: [{ id: 'r1', rating: 5, bodyText: 'Rất đáng học', maskedDisplayName: 'H*** Quốc', packageName: 'Silver 925', status: 'PUBLIC', createdAt: '2026-11-05T10:00:00Z' }],
      }),
    });
    render(
      <I18nProvider initialLanguage="vi">
        <AuthProvider source={fakeSession('signed-out')}>
          <MemoryRouter><WorkshopListPage /></MemoryRouter>
        </AuthProvider>
      </I18nProvider>,
    );

    expect(await screen.findByText('Rất đáng học')).toBeInTheDocument();
    expect(calls.find(call => call.path === '/api/v1/reviews').query).toMatchObject({ targetType: 'WORKSHOP_SESSION', limit: '3' });
    expect(screen.getByText('Silver 925', { selector: '.pkg-review-package' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem tất cả 4 đánh giá' })).toHaveAttribute('href', '/reviews');
  });
});
