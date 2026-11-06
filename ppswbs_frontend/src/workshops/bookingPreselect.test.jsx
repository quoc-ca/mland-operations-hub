import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { fakeSession, json, mockFetch } from '../test/render';
import { WorkshopBookingPage } from './WorkshopBookingPage';

const day = n => `2026-12-${String(n).padStart(2, '0')}T13:00:00Z`;
const sessions = Array.from({ length: 10 }, (_, i) => ({
  sessionReference: `s-${i + 1}`, scheduledStart: day(i + 1), scheduledEnd: day(i + 1).replace('T13', 'T16'),
  remainingCapacity: 8, customerSelectable: true,
}));

afterEach(() => window.history.pushState({}, '', '/'));

describe('booking page opened from a session card', () => {
  it('has that session already ticked, even when it is on a later page of the list', async () => {
    window.history.pushState({}, '', '/workshops/145/book?locationId=1&session=s-10&from=2026-12-01&to=2026-12-31');
    const calls = mockFetch({
      'GET /api/v1/workshop-packages/145': json(200, { packageId: 145, name: 'Silver 925', publicPrice: 900000, requiredSessionCount: 1, minParticipants: 1, maxParticipants: 8 }),
      'GET /api/v1/workshop-packages/145/locations': json(200, [{ id: 1, name: 'Thủ Đức' }, { id: 2, name: 'Quận 1' }]),
      'GET /api/v1/workshop-packages/145/sessions': json(200, sessions),
    });
    render(
      <I18nProvider initialLanguage="vi">
        <AuthProvider source={fakeSession('signed-out')}>
          <MemoryRouter><WorkshopBookingPage packageId="145" /></MemoryRouter>
        </AuthProvider>
      </I18nProvider>,
    );

    await waitFor(() => expect(screen.getByText('✓ Đã chọn')).toBeInTheDocument());
    expect(calls.find(call => call.path === '/api/v1/workshop-packages/145/sessions').query).toMatchObject({ locationId: '1', from: '2026-12-01', to: '2026-12-31' });
    expect(screen.getByLabelText('Chọn trang')).toHaveValue('1');
  });
});
