import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const PATH = '/api/v1/workshop-refund-account-key-replacements';

// 003.3 FR-040 / A10: the Manager screen (moved from Admin 2026-11-23) that returns encrypted refund accounts to pending account.
describe('RefundAccountKeyReplacementPage', () => {
  useFreshRole();

  it('lets a Manager run the replacement with a reason and shows the counts', async () => {
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      [`POST ${PATH}`]: json(200, { requestsReset: 3, linksSent: 3, failed: 1 }),
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { path: '/settlement/refund-account-key', language: 'en' });

    await user.click(await screen.findByRole('button', { name: 'Replace key' }));
    await user.click(screen.getAllByRole('button', { name: 'Replace key' }).pop());
    // A reason is required before anything is sent.
    expect(calls.filter(call => call.path === PATH)).toHaveLength(0);

    await user.type(screen.getByRole('textbox'), 'Key variable replaced');
    await user.click(screen.getAllByRole('button', { name: 'Replace key' }).pop());

    expect(await screen.findByText(/Key replaced/)).toBeInTheDocument();
    expect(screen.getByText('Requests returned to pending account').nextSibling).toHaveTextContent('3');
    expect(screen.getByText('Failed requests').nextSibling).toHaveTextContent('1');
    const posts = calls.filter(call => call.path === PATH);
    expect(posts).toHaveLength(1);
    expect(posts[0].headers['Idempotency-Key']).toBeTruthy();
    const body = typeof posts[0].body === 'string' ? JSON.parse(posts[0].body) : posts[0].body;
    expect(body).toEqual({ reason: 'Key variable replaced' });
  });

  it('shows an Admin that only a Manager can open the screen and sends nothing', async () => {
    const calls = mockFetch({ [PROFILE]: asRole('ADMIN') });
    renderRoutes(settlementRoutes, { path: '/settlement/refund-account-key', language: 'en' });
    expect(await screen.findByText('Only a Manager can open this screen.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Replace key' })).toBeNull();
    expect(calls.filter(call => call.path === PATH)).toHaveLength(0);
  });
});
