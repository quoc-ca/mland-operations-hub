import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { settlementRoutes } from '../routes';
import { PROFILE, asRole, useFreshRole } from './helpers';

useFreshRole();

/** 014 navigation shell R-3: the confirmation view lists exactly the differences the home card counts. */
const PENDING = (reference, branch) => ({ closeReference: reference, locationId: branch, workingDate: '2026-11-14', expectedCashVnd: 1000000, countedCashVnd: 990000, differenceVnd: -10000, differenceReason: 'miscount', status: 'PENDING_MANAGER_CONFIRMATION' });

describe('Cash close confirmation view', () => {
  it('lists the differences awaiting a Manager and confirms one with a reason, then reloads the list', async () => {
    let waiting = [PENDING('CLOSE-1', 3), PENDING('CLOSE-2', 4)];
    const calls = mockFetch({
      [PROFILE]: asRole('MANAGER'),
      'GET /api/v1/workshop-cash-closes': () => json(200, waiting),
      'POST /api/v1/workshop-cash-closes/CLOSE-1/difference-confirmations': () => { waiting = [PENDING('CLOSE-2', 4)]; return json(200, { ...PENDING('CLOSE-1', 3), status: 'CLOSED' }); },
    });
    const user = userEvent.setup();
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close?view=confirmation' });

    const section = await screen.findByRole('region', { name: 'Differences awaiting confirmation' });
    expect(await within(section).findByText('CLOSE-1')).toBeInTheDocument();
    expect(within(section).getByText('CLOSE-2')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'GET' && call.path === '/api/v1/workshop-cash-closes').query).toEqual({ awaitingConfirmation: 'true' });

    await user.click(within(section).getAllByRole('button', { name: 'Confirm difference' })[0]);
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'recounted');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm difference' }));
    expect(await screen.findByText('Difference confirmed; the day is closed.')).toBeInTheDocument();
    const confirm = calls.find(call => call.method === 'POST');
    expect(confirm.body).toEqual({ reason: 'recounted' });
    expect(confirm.headers['Idempotency-Key']).toBeTruthy();
    expect(await within(await screen.findByRole('region', { name: 'Differences awaiting confirmation' })).findByText('CLOSE-2')).toBeInTheDocument();
    expect(screen.queryByText('CLOSE-1')).not.toBeInTheDocument();
  });

  it('says nothing is waiting when the list is empty', async () => {
    mockFetch({ [PROFILE]: asRole('MANAGER'), 'GET /api/v1/workshop-cash-closes': json(200, []) });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close?view=confirmation' });
    expect(await screen.findByText('No cash-close difference is waiting for confirmation.')).toBeInTheDocument();
  });

  it('shows no list and reads none for Staff, and nothing without the view parameter', async () => {
    const staffCalls = mockFetch({ [PROFILE]: asRole('STAFF'), 'GET /api/v1/workshop-cash-closes': json(200, [PENDING('CLOSE-1', 3)]) });
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement/cash-close?view=confirmation' });
    expect(await screen.findByLabelText('Branch ID')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Differences awaiting confirmation' })).not.toBeInTheDocument();
    expect(staffCalls.some(call => call.path === '/api/v1/workshop-cash-closes')).toBe(false);
  });
});
