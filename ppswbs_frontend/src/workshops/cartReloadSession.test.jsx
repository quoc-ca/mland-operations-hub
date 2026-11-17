import React from 'react';
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { act, screen } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { addCartLine, clearCart } from './cartStorage';

describe('Cart page follows the stored cart', () => {
  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('shows lines that appear in storage after the page has rendered (session restored on reload)', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER', fullName: 'M' }) });
    renderRoutes(workshopRoutes, { path: '/workshop-booking/cart', session: fakeSession('signed-in') });
    expect(await screen.findByText(/Giỏ workshop của bạn đang trống/)).toBeInTheDocument();

    act(() => {
      addCartLine({ packageId: 5, packageName: 'Gói A', pricePerParticipantVnd: 500000, sessionReferences: ['11111111-1111-4111-8111-111111111111'], participantCount: 1 });
    });
    expect(await screen.findByText('Gói A')).toBeInTheDocument();
    expect(screen.queryByText(/Giỏ workshop của bạn đang trống/)).toBeNull();
  });
});
