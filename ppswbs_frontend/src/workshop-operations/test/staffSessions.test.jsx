import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

function open(role, sessions) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-sessions': json(200, sessions),
  });
  renderRoutes(operationsRoutes, { path: '/operations/sessions' });
}

describe('Session list by role', () => {
  beforeEach(() => clearRoleCache());

  it('tells a Staff with no assigned session to ask a Manager', async () => {
    open('STAFF', []);

    expect(await screen.findByRole('heading', { name: 'Ca của tôi' })).toBeInTheDocument();
    expect(await screen.findByText(/Bạn chưa được phân công ca nào/)).toBeInTheDocument();
  });

  it('keeps the Manager board and its own empty message', async () => {
    open('MANAGER', []);

    expect(await screen.findByRole('heading', { name: 'Workshop session board' })).toBeInTheDocument();
    expect(await screen.findByText('No sessions match the selected filters.')).toBeInTheDocument();
  });
});
