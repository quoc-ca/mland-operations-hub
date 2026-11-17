import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, waitFor, within, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

/** Going Back from checkout to the package page keeps the branch, session, ticket count and dates already chosen. */

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 6 };
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
const SESSIONS = [{ sessionReference: SESSION_ONE, locationId: 2, locationName: 'MLAND Hoàn Kiếm', scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 6 }];

function open() {
  mockFetch({
    'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
    'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Hoàn Kiếm' }]),
    'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
  });
  return renderRoutes(workshopRoutes, { path: '/workshops/5?locationId=2', session: fakeSession('signed-out') });
}

describe('Back from checkout keeps the package selection', () => {
  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('writes the pick into the page URL and restores it after "Đặt ngay" then Back', async () => {
    const user = userEvent.setup();
    const { router } = open();

    const panel = await screen.findByRole('region', { name: 'Chọn ca và số lượng' }).catch(() => screen.findByLabelText('Chọn ca và số lượng'));
    const sessionButton = await waitFor(() => {
      const found = within(panel).getAllByRole('button').find(button => /\d{2}:\d{2}/.test(button.textContent));
      expect(found).toBeTruthy();
      return found;
    });
    await user.click(sessionButton);
    await user.click(screen.getByRole('button', { name: 'Tăng số vé' }));
    await user.click(screen.getByRole('button', { name: 'Tăng số vé' }));

    await waitFor(() => {
      const params = new URLSearchParams(router.state.location.search);
      expect(params.get('participants')).toBe('3');
      expect(params.getAll('session')).toEqual([SESSION_ONE]);
      expect(params.get('locationId')).toBe('2');
    });
    const historyLength = window.history.length;

    await user.click(screen.getByRole('button', { name: /Đặt ngay/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/workshops/5/book'));
    expect(new URLSearchParams(router.state.location.search).get('participants')).toBe('3');

    await act(async () => { await router.navigate(-1); });
    await waitFor(() => expect(router.state.location.pathname).toBe('/workshops/5'));
    expect(new URLSearchParams(router.state.location.search).get('participants')).toBe('3');
    expect(screen.getByRole('spinbutton')).toHaveValue(3);
    expect(window.history.length).toBe(historyLength);
  });
});
