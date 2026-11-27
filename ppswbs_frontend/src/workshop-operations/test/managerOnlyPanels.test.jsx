import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const session = json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: [33] });

function open(role) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-sessions/session-1': session,
  });
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
}

describe('Manager-only panels on the session page', () => {
  beforeEach(() => clearRoleCache());

  it('hides staff assignment, booking recovery, and session cancellation from a Staff', async () => {
    open('STAFF');

    // What a Staff does at the session stays: check-in, corrections and ticket delivery.
    expect(await screen.findByText('Check-in desk')).toBeInTheDocument();
    expect(screen.getByText('Ticket delivery')).toBeInTheDocument();
    // Give the profile time to arrive, then confirm nothing of the Manager's appeared.
    await new Promise(resolve => setTimeout(resolve, 50));
    for (const hidden of ['Staff assignment', 'Manager booking recovery', 'Cancel session']) {
      expect(screen.queryByText(hidden)).not.toBeInTheDocument();
    }
  });

  it('shows them to a Manager', async () => {
    open('MANAGER');

    expect(await screen.findByText('Staff assignment')).toBeInTheDocument();
    for (const shown of ['Manager booking recovery', 'Cancel session']) {
      expect(screen.getByText(shown)).toBeInTheDocument();
    }
    // 003.2 FR-070, FR-071: the late-payment queue and the bill correction have their own screens.
    expect(screen.queryByText('Late payment reviews')).not.toBeInTheDocument();
    expect(screen.queryByText('Manager bill correction')).not.toBeInTheDocument();
  });
});
