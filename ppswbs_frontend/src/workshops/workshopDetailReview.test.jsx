import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { fakeSession, json, mockFetch } from '../test/render';
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

describe('sessions are paged on the package page', () => {
  it('shows six sessions at a time, moves between pages and can jump to any page', async () => {
    const user = userEvent.setup();
    renderPage(14);

    expect(await screen.findAllByText('Chọn lịch này để đặt')).toHaveLength(6);
    expect(screen.getByLabelText('Chọn trang')).toHaveValue('0');
    expect(screen.queryByText(/trên 14/)).toBeNull();
    expect(screen.getByRole('button', { name: '‹ Trước' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Sau ›' }));
    expect(screen.getByLabelText('Chọn trang')).toHaveValue('1');
    expect(screen.getAllByText('Chọn lịch này để đặt')).toHaveLength(6);

    await user.selectOptions(screen.getByLabelText('Chọn trang'), '3');
    expect(screen.getAllByText('Chọn lịch này để đặt')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Sau ›' })).toBeDisabled();
  });

  it('has no paging when everything fits on one page', async () => {
    renderPage(4);

    expect(await screen.findAllByText('Chọn lịch này để đặt')).toHaveLength(4);
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

describe('a session card carries its session to the booking page', () => {
  it('links to the booking page with the session, branch and dates', async () => {
    renderPage(3);
    const links = await screen.findAllByRole('link', { name: 'Chọn lịch này để đặt' });
    expect(links[0].getAttribute('href')).toMatch(/^\/workshops\/145\/book\?locationId=1&session=s-1&from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/);
  });
});
