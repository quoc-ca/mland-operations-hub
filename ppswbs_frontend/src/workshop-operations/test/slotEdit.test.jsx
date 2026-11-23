import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

const LOCATION = { id: 4, code: 'Q1', name: 'MLAND Quận 1', address: '1 Lê Lợi', active: true };
const SLOT = { id: 9, locationId: 4, label: 'Ca s?ng', startsAtLocal: '09:00', endsAtLocal: '12:00', active: true };
const problem = (status, code, detail = '') => new Response(JSON.stringify({ type: 'about:blank', title: code, status, code, detail }), { status, headers: { 'Content-Type': 'application/problem+json' } });

function openSlots(role, extra = {}) {
  const calls = mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-locations': json(200, [LOCATION]),
    'GET /api/v1/workshop-locations/4/slots': json(200, [SLOT]),
    'GET /api/v1/workshop-sessions': json(200, []),
    ...extra,
  });
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/admin?tab=slots' });
  return calls;
}

const openEditor = async user => {
  await user.click(await screen.findByRole('button', { name: 'Edit' }));
  return screen.findByRole('form', { name: 'Edit slot Ca s?ng' });
};

describe('003.2 FR-001e Manager slot edit', () => {
  beforeEach(() => clearRoleCache());

  it('sends the corrected label with times and active unchanged, then reloads the slots', async () => {
    const user = userEvent.setup();
    const calls = openSlots('MANAGER', { 'PATCH /api/v1/workshop-slots/9': json(200, { ...SLOT, label: 'Ca sáng' }) });
    const form = await openEditor(user);
    const label = within(form).getByLabelText('Label');
    await user.clear(label);
    await user.type(label, ' Ca sáng ');
    await user.click(within(form).getByRole('button', { name: 'Save slot' }));

    expect(await screen.findByText('Slot updated.')).toBeInTheDocument();
    const patch = calls.find(call => call.method === 'PATCH');
    expect(patch.path).toBe('/api/v1/workshop-slots/9');
    expect(patch.body).toEqual({ label: 'Ca sáng', startsAtLocal: '09:00', endsAtLocal: '12:00', active: true });
    expect(patch.headers['Idempotency-Key'].length).toBeGreaterThanOrEqual(16);
    expect(calls.filter(call => call.method === 'GET' && call.path === '/api/v1/workshop-locations/4/slots').length).toBeGreaterThan(1);
  });

  it('refuses an end before the start, a blank label or no change, and sends nothing', async () => {
    const user = userEvent.setup();
    const calls = openSlots('MANAGER');
    const form = await openEditor(user);
    await user.click(within(form).getByRole('button', { name: 'Save slot' }));
    expect(await screen.findByText('Change the label or times before saving.')).toBeInTheDocument();
    const end = within(form).getByLabelText('Ends at');
    await user.clear(end);
    await user.type(end, '08:00');
    await user.click(within(form).getByRole('button', { name: 'Save slot' }));
    expect(await screen.findByText('Start time must be before end time on the same day.')).toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Label'));
    await user.click(within(form).getByRole('button', { name: 'Save slot' }));
    expect(await screen.findByText('Slot label is required.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'PATCH')).toBe(false);
  });

  it('shows the backend refusal and keeps the form open with the edit intact', async () => {
    const user = userEvent.setup();
    openSlots('MANAGER', { 'PATCH /api/v1/workshop-slots/9': problem(409, 'SLOT_CONFLICT', 'Updated slot interval overlaps with configured slot') });
    const form = await openEditor(user);
    const end = within(form).getByLabelText('Ends at');
    await user.clear(end);
    await user.type(end, '13:30');
    await user.click(within(form).getByRole('button', { name: 'Save slot' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByRole('form', { name: 'Edit slot Ca s?ng' })).toBeInTheDocument();
    expect(within(form).getByLabelText('Ends at')).toHaveValue('13:30');
  });

  it.each(['STAFF', 'ADMIN'])('hides the slot edit action from %s', async role => {
    openSlots(role);
    await screen.findByText('Ca s?ng');
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });
});
