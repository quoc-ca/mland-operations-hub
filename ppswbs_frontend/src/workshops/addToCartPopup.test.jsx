import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 };
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
const SESSIONS = [{ sessionReference: SESSION_ONE, locationId: 2, locationName: 'MLAND Hoàn Kiếm', scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 4 }];

describe('Add to cart confirmation', () => {
  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('shows a centred pop-up that closes by itself after about one second', async () => {
    mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Hoàn Kiếm' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
    });
    const user = userEvent.setup();
    renderRoutes(workshopRoutes, { path: '/workshops/5?locationId=2', session: fakeSession('signed-out') });

    const panel = await screen.findByLabelText('Chọn ca và số lượng');
    const sessionButton = await waitFor(() => {
      const found = within(panel).getAllByRole('button').find(button => /\d{2}:\d{2}/.test(button.textContent));
      expect(found).toBeTruthy();
      return found;
    });
    await user.click(sessionButton);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));

    const popup = await screen.findByText(/Đã thêm vào giỏ hàng thành công/);
    expect(popup.closest('.toast-center')).not.toBeNull();
    await waitFor(() => expect(screen.queryByText(/Đã thêm vào giỏ hàng thành công/)).toBeNull(), { timeout: 2500 });
  });
});
