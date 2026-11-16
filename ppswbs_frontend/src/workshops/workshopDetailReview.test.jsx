import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { WorkshopDetailPage } from './WorkshopDetailPage';

const day = n => `2026-12-${String(n).padStart(2, '0')}T13:00:00Z`;
const sessionsOf = count => Array.from({ length: count }, (_, i) => ({
  sessionReference: `s-${i + 1}`, scheduledStart: day(i + 1), scheduledEnd: day(i + 1).replace('T13', 'T16'),
  remainingCapacity: 8, customerSelectable: true,
}));

function renderPage(sessionCount, extra = {}) {
  const calls = mockFetch({
    'GET /api/v1/workshop-packages/145': json(200, { packageId: 145, name: 'Silver 925', publicPrice: 900000, requiredSessionCount: 1 }),
    'GET /api/v1/workshop-packages/145/locations': json(200, [{ id: 1, name: 'Thủ Đức' }]),
    'GET /api/v1/workshop-packages/145/sessions': json(200, sessionsOf(sessionCount)),
    ...extra,
  });
  render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={fakeSession('signed-out')}>
        <MemoryRouter><WorkshopDetailPage packageId="145" /></MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
  return calls;
}

const sessionButtons = () => screen.getAllByRole('button', { pressed: false });

describe('sessions are paged on the package page', () => {
  it('shows six sessions at a time, moves between pages and can jump to any page', async () => {
    const user = userEvent.setup();
    renderPage(14);

    await screen.findAllByRole('button', { pressed: false });
    expect(sessionButtons()).toHaveLength(6);
    expect(screen.getByLabelText('Chọn trang')).toHaveValue('0');
    expect(screen.queryByText(/trên 14/)).toBeNull();
    expect(screen.getByRole('button', { name: '‹ Trước' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Sau ›' }));
    expect(screen.getByLabelText('Chọn trang')).toHaveValue('1');
    expect(sessionButtons()).toHaveLength(6);

    await user.selectOptions(screen.getByLabelText('Chọn trang'), '3');
    expect(sessionButtons()).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Sau ›' })).toBeDisabled();
  });

  it('has no paging when everything fits on one page', async () => {
    renderPage(4);

    await screen.findAllByRole('button', { pressed: false });
    expect(sessionButtons()).toHaveLength(4);
    expect(screen.queryByRole('navigation', { name: 'Phân trang các ca workshop' })).toBeNull();
  });
});

describe('the package reviews are always shown at the bottom of the same page', () => {
  it('lists the package reviews already, and the button only scrolls to them', async () => {
    const scrolled = vi.fn();
    window.HTMLElement.prototype.scrollIntoView = scrolled;
    const user = userEvent.setup();
    const calls = renderPage(3, {
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 5, averageRating: 4.6 }, totalElements: 5,
        items: [{ id: 'r1', rating: 5, bodyText: 'Rất đáng học', maskedDisplayName: 'H*** Quốc', status: 'PUBLIC', packageName: 'Silver 925', createdAt: '2026-11-05T10:00:00Z' }],
      }),
    });

    expect(await screen.findByText('Rất đáng học')).toBeInTheDocument();
    expect(calls.find(call => call.path === '/api/v1/reviews').query).toMatchObject({ packageId: '145', limit: '3' });
    expect(screen.getByText('4.6')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem tất cả 5 đánh giá của gói' })).toHaveAttribute('href', '/reviews?packageId=145');

    await user.click(screen.getByRole('button', { name: /Xem đánh giá của gói này/ }));
    expect(scrolled).toHaveBeenCalled();
  });

  it('says so when the package has no review yet', async () => {
    renderPage(3, { 'GET /api/v1/reviews': json(200, { aggregate: { count: 0, averageRating: 0 }, items: [], totalElements: 0 }) });

    expect(await screen.findByText('Gói này chưa có đánh giá công khai.')).toBeInTheDocument();
  });
});

describe('"Đặt ngay" carries the chosen session to the checkout', () => {
  it('opens /book with the branch, the session, the ticket count and the dates, and "Thêm vào giỏ" opens nothing', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/workshop-packages/145': json(200, { packageId: 145, name: 'Silver 925', publicPrice: 900000, requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 }),
      'GET /api/v1/workshop-packages/145/locations': json(200, [{ id: 1, name: 'Thủ Đức' }]),
      'GET /api/v1/workshop-packages/145/sessions': json(200, sessionsOf(3)),
      'POST /api/v1/workshop-cart-quotes': json(503, { code: 'DEPENDENCY_UNAVAILABLE' }),
    });
    const { router } = renderRoutes(workshopRoutes, { path: '/workshops/145', session: fakeSession('signed-out') });

    // Nothing chosen yet: both buttons ask for a session and go nowhere.
    await screen.findAllByRole('button', { pressed: false });
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));
    expect(await screen.findByText('Vui lòng chọn đủ 1 ca workshop.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/workshops/145');

    await user.click(sessionButtons()[0]);
    await user.click(screen.getByRole('button', { name: 'Tăng số vé' }));
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/workshops/145/book'));
    expect(router.state.location.search).toMatch(/^\?locationId=1&session=s-1&participants=2&from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/);
  });
});
