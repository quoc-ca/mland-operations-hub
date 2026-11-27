import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';
import { localDay, sessionsOfTab, sortSessions } from '../sessionBoard';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const at = (daysBack, hour) => ({ workshopDate: localDay(daysBack), scheduledStartAt: `${localDay(daysBack)}T0${hour}:00:00Z`, scheduledEndAt: `${localDay(daysBack)}T0${hour + 1}:00:00Z` });
const SESSIONS = [
  { id: 'ended-old', state: 'ATTENDANCE_FINALIZED', ...at(3, 1) },
  { id: 'later', state: 'SALES_PAUSED', ...at(-2, 1) },
  { id: 'cancelled', state: 'CANCELLED', ...at(-1, 1) },
  { id: 'soon', state: 'PUBLISHED', ...at(-1, 1) },
  { id: 'running', state: 'IN_PROGRESS', ...at(1, 5) },
];
const ids = list => list.map(item => item.id);

describe('session board order and tabs', () => {
  beforeEach(() => clearRoleCache());

  it('puts running, then upcoming, then ended, then cancelled', () => {
    expect(ids(sortSessions(SESSIONS))).toEqual(['running', 'soon', 'later', 'ended-old', 'cancelled']);
  });

  it('shows past sessions only on the ended tab, but never hides one still running', () => {
    expect(ids(sessionsOfTab(SESSIONS, 'all', true))).toEqual(['running', 'soon', 'later', 'cancelled']);
    expect(ids(sessionsOfTab(SESSIONS, 'done', true))).toEqual(['ended-old']);
    expect(ids(sessionsOfTab(SESSIONS, 'all', false))).toHaveLength(5);
  });

  it('counts each tab, hides the empty draft tab and moves the From date back on the ended tab', async () => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-sessions': json(200, SESSIONS),
      'GET /api/v1/workshop-locations': json(200, []),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions' });

    const tabs = await screen.findByRole('tablist');
    expect(within(tabs).getAllByRole('tab').map(tab => tab.textContent))
      .toEqual(['All (4)', 'In progress (1)', 'Upcoming (2)', 'Ended (1)', 'Cancelled (1)']);
    expect(screen.getByLabelText('From')).toHaveValue(localDay());

    await userEvent.setup().click(within(tabs).getByRole('tab', { name: 'Ended (1)' }));
    expect(screen.getByLabelText('From')).toHaveValue(localDay(7));
    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(calls.filter(call => call.path === '/api/v1/workshop-sessions')).toHaveLength(1);
  });
});
