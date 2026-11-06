import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { clearRoleCache } from '../shared/auth/useRole';
import { fakeSession, json, mockFetch } from '../test/render';
import { WorkshopLookupPage } from './WorkshopLookupPage';
import { reviewSourceFor } from './ReviewCta';

const LIST = 'GET /api/v1/members/me/workshop-bookings';
const PROFILE = 'GET /api/v1/accounts/me/profile';
const SOURCES = 'GET /api/v1/reviews/eligible-sources';
const SESSION = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

const booking = (code, extra = {}) => ({
  bookingCode: code, bookingStatus: 'CONFIRMED', paymentStatus: 'DEPOSIT_PAID', packageName: 'Silver', participantCount: 1,
  sessions: [{ sessionReference: SESSION, ordinal: 1, scheduledStart: '2026-12-01T13:00:00Z' }], tickets: [], ...extra,
});
const source = { sourceType: 'WORKSHOP_ATTENDANCE', sourceReference: 'INTERNAL-TICKET-REF', targetType: 'WORKSHOP_SESSION', targetReference: SESSION.toUpperCase(), eligible: true };

function Landing() {
  const location = useLocation();
  return <p>NEW REVIEW for {location.state?.source?.targetReference}</p>;
}

function renderPage() {
  return render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={fakeSession()}>
        <MemoryRouter initialEntries={['/workshop-booking/lookup']}>
          <Routes>
            <Route path="/workshop-booking/lookup" element={<WorkshopLookupPage />} />
            <Route path="/reviews/new" element={<Landing />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
}

describe('review button in "Đặt chỗ của tôi" (Feature-011 eligibility only)', () => {
  beforeEach(() => clearRoleCache());

  it('matches the backend source to a session of the booking, ignoring case', () => {
    expect(reviewSourceFor(booking('BK-1'), [source])).toBe(source);
    expect(reviewSourceFor(booking('BK-1'), [{ ...source, targetReference: 'other' }])).toBeUndefined();
    expect(reviewSourceFor(booking('BK-1'), [{ ...source, targetType: 'RETAIL_PRODUCT' }])).toBeUndefined();
    expect(reviewSourceFor(booking('BK-1'), [{ ...source, eligible: false }])).toBeUndefined();
    expect(reviewSourceFor(booking('BK-1', { sessions: [] }), [source])).toBeUndefined();
    expect(reviewSourceFor(booking('BK-1'), null)).toBeUndefined();
  });

  it('offers "Viết đánh giá" for an eligible session and opens the review form with that source', async () => {
    const user = userEvent.setup();
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [booking('BK-ATTENDED', { attendanceStatus: 'ATTENDED' })]),
      [SOURCES]: json(200, { items: [source] }),
    });
    renderPage();

    const list = within(await screen.findByLabelText('Đặt chỗ của tôi'));
    await user.click(await list.findByRole('button', { name: 'Viết đánh giá cho BK-ATTENDED' }));

    expect(await screen.findByText(/NEW REVIEW for/)).toHaveTextContent(SESSION.toUpperCase());
  });

  it('never shows the internal source reference', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [booking('BK-ATTENDED', { attendanceStatus: 'ATTENDED' })]),
      [SOURCES]: json(200, { items: [source] }),
    });
    renderPage();

    await screen.findByRole('button', { name: 'Viết đánh giá cho BK-ATTENDED' });
    expect(screen.queryByText(/INTERNAL-TICKET-REF/)).toBeNull();
  });

  it('greys the action out for a confirmed booking that was not attended yet', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [booking('BK-LATER')]),
      [SOURCES]: json(200, { items: [] }),
    });
    renderPage();

    expect(await screen.findByRole('button', { name: 'Đánh giá BK-LATER sau khi tham dự' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: /Viết đánh giá/ })).toBeNull();
  });

  it('shows no review action for a no-show', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [booking('BK-NOSHOW', { attendanceStatus: 'NO_SHOW_PENDING' })]),
      [SOURCES]: json(200, { items: [] }),
    });
    renderPage();

    await screen.findByText('BK-NOSHOW');
    expect(screen.queryByRole('button', { name: /đánh giá/i })).toBeNull();
  });

  it('shows no review action, and no error, when the eligibility call fails', async () => {
    mockFetch({
      [PROFILE]: json(200, { role: 'MEMBER' }),
      [LIST]: json(200, [booking('BK-ATTENDED', { attendanceStatus: 'ATTENDED' })]),
      [SOURCES]: json(403, { code: 'REVIEW_NOT_AUTHORIZED', message: 'no' }),
    });
    renderPage();

    await screen.findByText('BK-ATTENDED');
    expect(screen.queryByRole('button', { name: /Viết đánh giá/ })).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
