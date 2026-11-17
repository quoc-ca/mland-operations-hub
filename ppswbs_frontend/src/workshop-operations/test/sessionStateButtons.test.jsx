import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';
import { transitionProblemText } from '../pages/OperationsSessionPage';

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

describe('a refused state change says why, and does not send the user home', () => {
  beforeEach(() => clearRoleCache());

  it('explains a Staff member who is not assigned to the session', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
      'POST /api/v1/workshop-sessions/session-1/state-transitions': json(403, { code: 'FORBIDDEN', message: 'Access denied: Staff member is not assigned to this workshop session', correlationId: 'c-9' }),
    });
    const { router } = renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
    await userEvent.setup().click(await screen.findByRole('button', { name: 'IN PROGRESS' }));
    expect(await screen.findByText(/Bạn chưa được gán vào ca này/)).toBeInTheDocument();
    expect(screen.getByText('Code: FORBIDDEN')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/operations/sessions/session-1');
  });

  it('words each refusal for the person who has to act on it', () => {
    expect(transitionProblemText({ status: 403, code: 'LOCATION_SCOPE_DENIED', message: 'Location ID 4 is outside actor\'s active location scope' }, 'IN_PROGRESS')).toMatch(/chi nhánh ngoài phạm vi/);
    expect(transitionProblemText({ status: 403, code: 'FORBIDDEN', message: 'Only a Manager can pause session sales' }, 'SALES_PAUSED')).toMatch(/Chỉ Manager/);
    expect(transitionProblemText({ status: 409, code: 'INVALID_STATE', message: 'Session is already in state IN_PROGRESS' }, 'IN_PROGRESS')).toMatch(/đã ở trạng thái này/);
    expect(transitionProblemText({ status: 409, code: 'INVALID_STATE', message: 'Cannot transition to IN_PROGRESS from state CANCELLED' }, 'IN_PROGRESS')).toMatch(/Không chuyển được ca sang IN PROGRESS/);
    expect(transitionProblemText({ status: 500 }, 'IN_PROGRESS')).toBe('');
  });
});
