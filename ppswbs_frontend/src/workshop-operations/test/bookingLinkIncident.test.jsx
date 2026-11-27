import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const INCIDENT = 'POST /api/v1/workshop-booking-groups/GRP-A/magic-link-incidents';

beforeEach(() => clearRoleCache());

describe('Booking recovery: revoke or rotate the magic link', () => {
  it.each([
    ['REVOKE', 'Revoke magic link', 'Confirm revocation', 'was revoked'],
    ['ROTATE', 'Rotate magic link', 'Confirm rotation', 'was rotated'],
  ])('asks before %s and reports the outcome in words', async (action, button, confirm, done) => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
      [INCIDENT]: json(200, { groupCode: 'GRP-A', action: `MAGIC_LINK_${action}`, outcome: `${action}D`, correlationId: 'c-1' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await user.type(await screen.findByPlaceholderText('GRP-… (Booking code to handle)'), 'GRP-A');
    const sent = () => calls.filter(call => call.path.endsWith('/magic-link-incidents'));

    await user.click(screen.getByRole('button', { name: button }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    expect(sent()).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: button }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: confirm }));

    expect(await screen.findByText(new RegExp(`GRP-A ${done}`))).toBeInTheDocument();
    expect(sent()).toHaveLength(1);
    expect(sent()[0].body.action).toBe(action);
    expect(screen.queryByText(/correlationId|MAGIC_LINK_/)).not.toBeInTheDocument();
  });
});
