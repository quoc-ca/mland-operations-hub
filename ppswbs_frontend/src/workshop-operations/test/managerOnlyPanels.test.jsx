import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

const session = json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: [33] });

function open(role) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-sessions/session-1': session,
  });
  renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
}

describe('Manager-only panels on the session page', () => {
  beforeEach(() => clearRoleCache());

  it('hides staff assignment, booking recovery, session cancellation and folio correction from a Staff', async () => {
    open('STAFF');

    // What a Staff does at the session stays: check-in, corrections and ticket delivery.
    expect(await screen.findByText('Check-in desk')).toBeInTheDocument();
    expect(screen.getByText('Check-in corrections')).toBeInTheDocument();
    expect(screen.getByText('Ticket delivery')).toBeInTheDocument();
    // Give the profile time to arrive, then confirm nothing of the Manager's appeared.
    await new Promise(resolve => setTimeout(resolve, 50));
    for (const hidden of ['Staff assignment', 'Manager booking recovery', 'Cancellation handoff', 'Manager folio correction']) {
      expect(screen.queryByText(hidden)).not.toBeInTheDocument();
    }
  });

  it('shows them to a Manager', async () => {
    open('MANAGER');

    expect(await screen.findByText('Staff assignment')).toBeInTheDocument();
    for (const shown of ['Manager booking recovery', 'Cancellation handoff', 'Manager folio correction']) {
      expect(screen.getByText(shown)).toBeInTheDocument();
    }
  });
});
