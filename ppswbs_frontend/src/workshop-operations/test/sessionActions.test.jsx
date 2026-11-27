import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const session = state => json(200, { id: 'session-1', state, locationId: 4, capacity: 10 });

describe('Session facts actions', () => {
  it('has no plain ATTENDANCE FINALIZED state button; only Finalize attendance does the finalization', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions/session-1': session('IN_PROGRESS') });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('button', { name: 'Finalize attendance' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Attendance finalized' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start session' })).toBeDisabled();
  });

  it('keeps Finalize attendance disabled until the session is IN PROGRESS', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions/session-1': session('PUBLISHED') });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('button', { name: 'Finalize attendance' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Start session' })).toBeEnabled();
  });
});
