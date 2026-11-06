import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

function open(state) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
    'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state, locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
  });
  renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
}

const button = name => screen.getByRole('button', { name });

describe('session state buttons follow the allowed transitions (spec 003.2)', () => {
  beforeEach(() => clearRoleCache());

  it('greys out the sales states once the session is IN PROGRESS, and says why', async () => {
    open('IN_PROGRESS');
    await screen.findByText('Session facts');

    for (const name of ['PUBLISHED', 'SALES PAUSED', 'IN PROGRESS']) {
      expect(button(name)).toBeDisabled();
    }
    expect(button('PUBLISHED')).toHaveAttribute('title', expect.stringContaining('không quay lại'));
    expect(button('Finalize attendance')).toBeEnabled();
  });

  it('lets a PUBLISHED session be paused or started early, but not "moved" to its own state', async () => {
    open('PUBLISHED');
    await screen.findByText('Session facts');

    expect(button('PUBLISHED')).toBeDisabled();
    expect(button('SALES PAUSED')).toBeEnabled();
    expect(button('IN PROGRESS')).toBeEnabled();
    expect(button('Finalize attendance')).toBeDisabled();
  });

  it('lets a SALES PAUSED session resume sales or start', async () => {
    open('SALES_PAUSED');
    await screen.findByText('Session facts');

    expect(button('PUBLISHED')).toBeEnabled();
    expect(button('SALES PAUSED')).toBeDisabled();
    expect(button('IN PROGRESS')).toBeEnabled();
  });
});
