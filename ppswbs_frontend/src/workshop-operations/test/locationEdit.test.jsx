import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

const LOCATION = { id: 4, code: 'Q1', name: 'MLAND Quận 1', address: '1 Lê Lợi', active: true };
const problem = (status, code, detail = '') => new Response(JSON.stringify({ type: 'about:blank', title: code, status, code, detail }), { status, headers: { 'Content-Type': 'application/problem+json' } });

function openLocations(role, extra = {}) {
  const calls = mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-locations': json(200, [LOCATION]),
    'GET /api/v1/workshop-locations/4/slots': json(200, []),
    'GET /api/v1/workshop-sessions': json(200, []),
    ...extra,
  });
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/admin?tab=locations' });
  return calls;
}

const openEditor = async user => {
  await user.click(await screen.findByRole('button', { name: 'Edit' }));
  return screen.findByRole('form', { name: 'Edit location Q1' });
};

describe('003.2 T136 Manager location edit', () => {
  beforeEach(() => clearRoleCache());

  it('sends only the changed fields with an Idempotency-Key and reloads the list', async () => {
    const user = userEvent.setup();
    const calls = openLocations('MANAGER', {
      'PATCH /api/v1/workshop-locations/4': json(200, { ...LOCATION, name: 'MLAND Q1 mới' }),
    });
    const form = await openEditor(user);
    const name = within(form).getByLabelText('Name');
    await user.clear(name);
    await user.type(name, '  MLAND Q1 mới ');
    await user.click(within(form).getByRole('button', { name: 'Save location' }));

    expect(await screen.findByText('Location updated.')).toBeInTheDocument();
    const patch = calls.find(call => call.method === 'PATCH');
    expect(patch.path).toBe('/api/v1/workshop-locations/4');
    expect(patch.body).toEqual({ name: 'MLAND Q1 mới' });
    expect(patch.headers['Idempotency-Key'].length).toBeGreaterThanOrEqual(16);
    expect(calls.filter(call => call.method === 'GET' && call.path === '/api/v1/workshop-locations').length).toBeGreaterThan(1);
  });

  it('sends active=false from the toggle', async () => {
    const user = userEvent.setup();
    const calls = openLocations('MANAGER', {
      'PATCH /api/v1/workshop-locations/4': json(200, { ...LOCATION, active: false }),
    });
    const form = await openEditor(user);
    await user.click(within(form).getByLabelText('Active'));
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    await screen.findByText('Location updated.');
    expect(calls.find(call => call.method === 'PATCH').body).toEqual({ active: false });
  });

  it('refuses an unchanged form or a blank name on the screen and sends nothing', async () => {
    const user = userEvent.setup();
    const calls = openLocations('MANAGER');
    const form = await openEditor(user);
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    expect(await screen.findByText('Change the name, address or status before saving.')).toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Name'));
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    expect(await screen.findByText('Location name is required.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'PATCH')).toBe(false);
  });

  it('shows the 409 INVALID_STATE deactivation refusal and keeps the form open with the edit intact', async () => {
    const user = userEvent.setup();
    openLocations('MANAGER', {
      'PATCH /api/v1/workshop-locations/4': problem(409, 'INVALID_STATE', 'Location cannot be deactivated while a session that has not ended still uses it'),
    });
    const form = await openEditor(user);
    await user.click(within(form).getByLabelText('Active'));
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    expect(await screen.findByText(/while a session that has not ended/)).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Edit location Q1' })).toBeInTheDocument();
    expect(within(form).getByLabelText('Active')).not.toBeChecked();
  });

  it('shows the 404 and 403 messages', async () => {
    const user = userEvent.setup();
    let status = 404;
    openLocations('MANAGER', {
      'PATCH /api/v1/workshop-locations/4': () => (status === 404 ? problem(404, 'RESOURCE_NOT_FOUND', 'Workshop location not found with ID 4') : problem(403, 'FORBIDDEN', 'Access requires MANAGER role')),
    });
    const form = await openEditor(user);
    const name = within(form).getByLabelText('Name');
    await user.clear(name);
    await user.type(name, 'Renamed');
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    expect(await screen.findByText('This location was not found. It may have been removed; reload the list.')).toBeInTheDocument();
    status = 403;
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    expect(await screen.findByText('Only an active Manager can edit a location.')).toBeInTheDocument();
  });

  it('offers a retry after a lost answer and repeats the same Idempotency-Key', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const calls = openLocations('MANAGER', {
      'PATCH /api/v1/workshop-locations/4': () => (++attempt === 1 ? json(503, { code: 'DEPENDENCY_UNAVAILABLE', message: 'down' }) : json(200, { ...LOCATION, name: 'Retried' })),
    });
    const form = await openEditor(user);
    const name = within(form).getByLabelText('Name');
    await user.clear(name);
    await user.type(name, 'Retried');
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    await user.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Location updated.')).toBeInTheDocument();
    const patches = calls.filter(call => call.method === 'PATCH');
    expect(patches).toHaveLength(2);
    expect(patches[1].headers['Idempotency-Key']).toBe(patches[0].headers['Idempotency-Key']);
  });

  it.each(['STAFF', 'ADMIN'])('hides the edit action from %s', async role => {
    openLocations(role);
    await screen.findByText('MLAND Quận 1');
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument());
    expect(screen.queryByRole('columnheader', { name: 'Edit' })).not.toBeInTheDocument();
  });
});

describe('003.2 FR-012c inactive locations', () => {
  beforeEach(() => clearRoleCache());

  it('lists an inactive location for the Manager to reactivate but does not offer it for slots', async () => {
    const user = userEvent.setup();
    const inactive = { id: 5, code: 'Q2', name: 'MLAND Quận 2', address: '2 Nguyễn Huệ', active: false };
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-locations': json(200, [LOCATION, inactive]),
      'GET /api/v1/workshop-locations/4/slots': json(200, []),
      'GET /api/v1/workshop-sessions': json(200, []),
      'PATCH /api/v1/workshop-locations/5': json(200, { ...inactive, active: true }),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/admin?tab=locations' });
    expect(await screen.findByText('MLAND Quận 2')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    const row = screen.getByText('MLAND Quận 2').closest('tr');
    await user.click(within(row).getByRole('button', { name: 'Edit' }));
    const form = await screen.findByRole('form', { name: 'Edit location Q2' });
    await user.click(within(form).getByLabelText('Active'));
    await user.click(within(form).getByRole('button', { name: 'Save location' }));
    await screen.findByText('Location updated.');
    expect(calls.find(call => call.method === 'PATCH').body).toEqual({ active: true });

    await user.click(screen.getByRole('button', { name: 'Slots' }));
    const select = await screen.findByLabelText('Location');
    expect(within(select).queryByRole('option', { name: 'MLAND Quận 2' })).not.toBeInTheDocument();
    expect(within(select).getByRole('option', { name: 'MLAND Quận 1' })).toBeInTheDocument();
  });
});

describe('003.2 FR-001e/FR-001f slot and session forms are Manager-only', () => {
  beforeEach(() => clearRoleCache());

  it.each(['STAFF', 'ADMIN'])('hides the slot and session creation forms from %s', async role => {
    const user = userEvent.setup();
    openLocations(role);
    await screen.findByText('MLAND Quận 1');
    await user.click(screen.getByRole('button', { name: 'Slots' }));
    await screen.findByLabelText('Location');
    expect(screen.queryByRole('button', { name: 'Create slot' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sessions' }));
    await screen.findByText('No sessions match the current scope.');
    expect(screen.queryByRole('button', { name: 'New session' })).not.toBeInTheDocument();
  });

  it('shows both forms to a Manager', async () => {
    const user = userEvent.setup();
    openLocations('MANAGER');
    await screen.findByText('MLAND Quận 1');
    await user.click(screen.getByRole('button', { name: 'Slots' }));
    expect(await screen.findByRole('button', { name: 'Create slot' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sessions' }));
    expect(await screen.findByRole('button', { name: 'New session' })).toBeInTheDocument();
  });
});
