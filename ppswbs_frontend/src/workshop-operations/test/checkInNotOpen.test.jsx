import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';
import { checkInNotOpenText } from '../pages/OperationsSessionPage';
import { formatDateTime } from '../../shared/i18n/I18nProvider';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// 003.2 T126 (CHK014): 409 CHECK_IN_NOT_OPEN carries checkInOpensAt; the desk says when check-in opens.
const CHECK_IN = 'POST /api/v1/workshop-sessions/session-1/check-ins';
const OPENS_AT = '2026-12-01T01:30:00Z';

beforeEach(() => clearRoleCache());

describe('CHECK_IN_NOT_OPEN on the check-in desk', () => {
  it('shows "check-in opens at ..." with no early-check-in override', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10 }),
      [CHECK_IN]: json(409, { code: 'CHECK_IN_NOT_OPEN', detail: 'not yet', checkInOpensAt: OPENS_AT, correlationId: 'c-1' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await user.type((await screen.findAllByPlaceholderText('GRP-…-L0-T1'))[0], 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));

    const alert = await screen.findByText(/Check-in opens at/);
    expect(alert).toHaveTextContent(formatDateTime(OPENS_AT, 'en'));
    expect(alert).not.toHaveTextContent('EARLY_CHECKIN');
    // Explained in the desk, not duplicated in the generic notice.
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    // The early-check-in override was removed (owner decision 2026-11-17).
    expect(screen.queryByRole('button', { name: /EARLY_CHECKIN/ })).not.toBeInTheDocument();
  });

  it('keeps INVALID_STATE in the generic notice (a session not open for check-in)', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10 }),
      [CHECK_IN]: json(409, { code: 'INVALID_STATE', detail: 'Session is in state CANCELLED' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await user.type((await screen.findAllByPlaceholderText('GRP-…-L0-T1'))[0], 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));

    await screen.findAllByRole('alert');
    expect(screen.queryByText(/Check-in opens at/)).not.toBeInTheDocument();
  });
});

describe('checkInNotOpenText', () => {
  it('formats the opening time and falls back when the instant is missing or invalid', () => {
    expect(checkInNotOpenText({ status: 409, code: 'CHECK_IN_NOT_OPEN', checkInOpensAt: OPENS_AT }, 'en'))
      .toContain(formatDateTime(OPENS_AT, 'en'));
    expect(checkInNotOpenText({ status: 409, code: 'CHECK_IN_NOT_OPEN' }, 'en')).toContain('a later time');
    expect(checkInNotOpenText({ status: 409, code: 'CHECK_IN_NOT_OPEN', checkInOpensAt: 'junk' }, 'en')).toContain('a later time');
    expect(checkInNotOpenText({ status: 409, code: 'INVALID_STATE' })).toBeNull();
    expect(checkInNotOpenText(null)).toBeNull();
  });
});
