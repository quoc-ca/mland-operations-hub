import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { reviewManagementRoutes } from '../routes';

const source = {
  sourceType: 'WORKSHOP_ATTENDANCE', sourceReference: 'TICK-101', targetType: 'WORKSHOP_SESSION',
  targetReference: 'SESS-101', label: 'Workshop làm nhẫn cơ bản', eligible: true,
};
const created = {
  id: 'rev-1', targetType: 'WORKSHOP_SESSION', targetReference: 'SESS-101', rating: 5, bodyText: 'ok', status: 'PUBLIC',
  maskedDisplayName: 'H*** Quốc', createdAt: '2026-11-06T00:00:00Z', version: 0,
};

function openCreate() {
  const calls = mockFetch({ 'POST /api/v1/reviews': json(201, created), 'GET /api/v1/reviews/rev-1': json(200, created) });
  renderRoutes(reviewManagementRoutes, { path: '/reviews/new', session: fakeSession('signed-in'), state: { source } });
  return calls;
}

describe('anonymous option when writing a review', () => {
  it('sends anonymous: true when the reviewer ticks it', async () => {
    const user = userEvent.setup();
    const calls = openCreate();
    await screen.findByText('Share your experience');

    expect(screen.getByText('Tên của bạn sẽ không hiện công khai. Lựa chọn này không đổi được sau khi gửi.')).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Đánh giá ẩn danh' }));
    await user.click(screen.getByRole('button', { name: 'Đăng đánh giá' }));

    expect(calls.find(call => call.method === 'POST').body.anonymous).toBe(true);
  });

  it('sends no anonymous flag by default, so the masked account name is shown', async () => {
    const user = userEvent.setup();
    const calls = openCreate();
    await screen.findByText('Share your experience');

    expect(screen.getByRole('checkbox', { name: 'Đánh giá ẩn danh' })).not.toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Đăng đánh giá' }));

    expect(calls.find(call => call.method === 'POST').body.anonymous).toBeUndefined();
  });
});
