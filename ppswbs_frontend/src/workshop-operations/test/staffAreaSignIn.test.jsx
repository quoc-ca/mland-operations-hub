import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { fakeSession, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';
import { settlementRoutes } from '../../workshop-settlement/routes';

describe('Staff/Manager pages without a session', () => {
  it('offers a sign-in link that returns to the session page', async () => {
    const calls = mockFetch({});
    renderRoutes(operationsRoutes, { path: '/operations/sessions/abc-1', session: fakeSession('signed-out') });

    const link = await screen.findByRole('link', { name: 'Đăng nhập' });
    expect(link).toHaveAttribute('href', '/login?returnTo=%2Foperations%2Fsessions%2Fabc-1');
    expect(screen.getByText(/Staff hoặc Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('does the same for the settlement workspace', async () => {
    mockFetch({});
    renderRoutes(settlementRoutes, { path: '/settlement', session: fakeSession('signed-out') });

    expect(await screen.findByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login?returnTo=%2Fsettlement');
  });
});
