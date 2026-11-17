import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

const session = state => json(200, { id: 'session-1', state, locationId: 4, capacity: 10 });

describe('Session facts actions', () => {
  it('has no plain ATTENDANCE FINALIZED state button; only Finalize attendance does the finalization', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions/session-1': session('IN_PROGRESS') });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('button', { name: 'Finalize attendance' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'ATTENDANCE FINALIZED' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'IN PROGRESS' })).toBeDisabled();
  });

  it('keeps Finalize attendance disabled until the session is IN PROGRESS', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions/session-1': session('PUBLISHED') });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('button', { name: 'Finalize attendance' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'IN PROGRESS' })).toBeEnabled();
  });
});
