import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';
import { clearRoleCache } from '../../shared/auth/useRole';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

describe('Late payment reviews page', () => {
  beforeEach(() => clearRoleCache());

  it('loads the Manager queue on open and lists the waiting group', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'M' }),
      'GET /api/v1/workshop-sessions': json(200, []),
      'GET /api/v1/late-payment-reviews': json(200, [
        { reviewId: 'r1', groupCode: 'GRP-LATE-1', amount: 500000, failedConditions: ['SESSION_STARTED'], confirmAllowed: false },
      ]),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/late-payments' });

    expect(await screen.findByText('GRP-LATE-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm late payment' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Open 100% refund' })).toBeEnabled();
  });

  it('shows the empty state', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'M' }),
      'GET /api/v1/workshop-sessions': json(200, []),
      'GET /api/v1/late-payment-reviews': json(200, []),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/late-payments' });
    expect(await screen.findByText('No late payment is waiting for a Manager.')).toBeInTheDocument();
  });
});
