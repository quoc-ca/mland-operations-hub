import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// The camera and the decoder are replaced: a test "shows" a QR by calling the decode callback.
const camera = vi.hoisted(() => ({ show: null, stop: null, fail: false }));
vi.mock('@zxing/browser', () => ({
  BrowserQRCodeReader: class {
    async decodeFromConstraints(constraints, video, callback) {
      if (camera.fail) throw new Error('NotAllowedError');
      camera.show = token => callback({ getText: () => token });
      camera.stop = vi.fn();
      return { stop: camera.stop };
    }
  },
}));

const PATH = '/operations/sessions/session-1';
const CHECK_IN = 'POST /api/v1/workshop-sessions/session-1/check-ins';
const ROSTER = 'GET /api/v1/workshop-sessions/session-1/attendance-roster';
const sessionIn = (state, extra = {}) => ({ 'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state, locationId: 4, capacity: 10, assignedStaffAccountIds: [], ...extra }) });
const row = (attendanceState, checkedInAt = null) => ({ ticketCode: 'TK-1', bookingReference: 'GRP-A-L0', ticketSequence: 1, attendanceState, checkedInAt });

beforeEach(() => { clearRoleCache(); camera.show = null; camera.stop = null; camera.fail = false; });

async function openScanner(user) {
  await user.click(await screen.findByRole('button', { name: 'Scan with camera' }));
  await waitFor(() => expect(camera.show).toBeTruthy());
  return screen.getByRole('dialog', { name: 'Scan tickets with the camera' });
}

describe('Camera scan on the session page (003.2 FR-057)', () => {
  it('checks in once for a code held in view, keeps the page and turns the camera off on close', async () => {
    const calls = mockFetch({
      ...sessionIn('IN_PROGRESS'),
      [ROSTER]: json(200, [row('NOT_CHECKED_IN')]),
      [CHECK_IN]: json(200, { checkInResult: 'CHECKED_IN', ticketCode: 'TK-1' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    const dialog = await openScanner(user);

    await act(async () => { camera.show('QR-abc'); camera.show('QR-abc'); });
    expect(await within(dialog).findByText('CHECKED IN')).toBeInTheDocument();
    expect(within(dialog).getByText('GRP-A-L0-T1')).toBeInTheDocument();
    await act(async () => { camera.show('QR-abc'); });

    const posts = calls.filter(call => call.method === 'POST' && call.path.endsWith('/check-ins'));
    expect(posts).toHaveLength(1);
    expect(posts[0].body).toEqual({ qrToken: 'QR-abc' });
    expect(screen.queryByText('QR-abc')).not.toBeInTheDocument();
    // The reload after the check-in kept the page and the open camera.
    await waitFor(() => expect(calls.filter(call => call.path.endsWith('/attendance-roster')).length).toBeGreaterThanOrEqual(2));
    expect(screen.getByRole('dialog', { name: 'Scan tickets with the camera' })).toBe(dialog);
    expect(camera.stop).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: 'Close camera' }));
    expect(camera.stop).toHaveBeenCalled();
  });

  it('says why a scanned ticket was refused', async () => {
    mockFetch({
      ...sessionIn('IN_PROGRESS'),
      [ROSTER]: json(200, []),
      [CHECK_IN]: json(409, { code: 'TICKET_QR_REVOKED', detail: 'revoked' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    const dialog = await openScanner(user);

    await act(async () => { camera.show('QR-old'); });
    expect(await within(dialog).findByText('NOT CHECKED IN')).toBeInTheDocument();
    expect(within(dialog).getByText(/revoked because the ticket has a new recipient/)).toBeInTheDocument();
  });

  it('says the camera is unavailable and has no typed QR input', async () => {
    camera.fail = true;
    mockFetch({ ...sessionIn('IN_PROGRESS'), [ROSTER]: json(200, []) });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    await user.click(await screen.findByRole('button', { name: 'Scan with camera' }));

    expect(await screen.findByText(/The camera could not be opened/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Scan token')).not.toBeInTheDocument();
  });
});

describe('Roster row check-in (003.2 FR-058)', () => {
  const openRow = async user => {
    await user.click(await screen.findByRole('button', { name: 'GRP-A-L0-T1' }));
    return screen.getByRole('dialog', { name: 'Ticket GRP-A-L0-T1' });
  };

  it('checks a ticket in by its code only after the confirmation', async () => {
    const calls = mockFetch({
      ...sessionIn('IN_PROGRESS'),
      [ROSTER]: json(200, [row('NOT_CHECKED_IN')]),
      [CHECK_IN]: json(200, { checkInResult: 'CHECKED_IN' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    const dialog = await openRow(user);
    expect(calls.some(call => call.path.endsWith('/check-ins'))).toBe(false);

    await user.click(within(dialog).getByRole('button', { name: 'Confirm check-in' }));

    expect(await screen.findByText('Check-in recorded.')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/check-ins')).body).toEqual({ ticketCode: 'GRP-A-L0-T1' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('gives the reason and no check-in before check-in opens', async () => {
    mockFetch({ ...sessionIn('PUBLISHED', { checkInOpensAt: '2099-01-01T01:00:00Z' }), [ROSTER]: json(200, [row('NOT_CHECKED_IN')]) });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    const dialog = await openRow(user);

    expect(within(dialog).getByRole('alert')).toHaveTextContent('Check-in opens at');
    expect(within(dialog).queryByRole('button', { name: 'Confirm check-in' })).not.toBeInTheDocument();
  });

  it('shows Staff the check-in time only', async () => {
    mockFetch({
      ...sessionIn('IN_PROGRESS'),
      [ROSTER]: json(200, [row('CHECKED_IN', '2026-11-06T02:00:00Z')]),
      'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    const dialog = await openRow(user);

    expect(within(dialog).getByText(/^Checked in at .*2026/)).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Reverse check-in' })).not.toBeInTheDocument();
  });
});

describe('Roster undo by a Manager (003.2 FR-058)', () => {
  it('reverses a check-in only with a reason note', async () => {
    const calls = mockFetch({
      ...sessionIn('IN_PROGRESS'),
      [ROSTER]: json(200, [row('CHECKED_IN', '2026-11-06T02:00:00Z')]),
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'POST /api/v1/workshop-sessions/session-1/check-in-reversals': json(200, { attendanceState: 'NOT_CHECKED_IN' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: PATH });
    await screen.findByText('Staff assignment');
    await user.click(screen.getByRole('button', { name: 'GRP-A-L0-T1' }));
    const dialog = screen.getByRole('dialog', { name: 'Ticket GRP-A-L0-T1' });
    const reverse = within(dialog).getByRole('button', { name: 'Reverse check-in' });
    expect(reverse).toBeDisabled();

    await user.type(within(dialog).getByRole('textbox'), 'Wrong ticket scanned');
    await user.click(reverse);

    expect(await screen.findByText('Check-in reversal recorded.')).toBeInTheDocument();
    expect(calls.find(call => call.path.endsWith('/check-in-reversals')).body)
      .toEqual({ ticketCode: 'GRP-A-L0-T1', reasonCode: 'RECORDING_ERROR', reasonNote: 'Wrong ticket scanned' });
  });
});
