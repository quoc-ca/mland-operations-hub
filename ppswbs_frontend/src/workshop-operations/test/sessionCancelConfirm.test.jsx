import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

/** BUG-E2E-0052: cancelling a session asks first; nothing is sent until the Manager confirms. */
describe('session cancellation asks for confirmation', () => {
  beforeEach(() => clearRoleCache());

  it('sends the request only after the dialog is confirmed', async () => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
      'POST /api/v1/workshop-sessions/session-1/cancellation-requests': json(202, {}),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    const request = await screen.findByRole('button', { name: 'Request cancellation' });
    const sent = () => calls.filter(call => call.path.endsWith('/cancellation-requests'));
    await user.type(screen.getByPlaceholderText(/what happened/i), 'broken oven');

    await user.click(request);
    expect(sent()).toHaveLength(0);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm cancellation' }));

    await waitFor(() => expect(sent()).toHaveLength(1));
  });
});
