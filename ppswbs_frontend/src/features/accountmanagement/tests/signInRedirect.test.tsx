import { beforeEach, describe, expect, it, vi } from 'vitest';
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import AuthPage from '../components/AuthPage';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';

// The sign-in page the router really uses (the account-management one): where does a signed-in user land?
function open(entry: string, role: string) {
  vi.spyOn(firebaseAuthClient, 'signIn').mockResolvedValue({} as never);
  vi.spyOn(accountManagementApiClient, 'bootstrapOwnAccount').mockResolvedValue({ role } as never);
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/login" element={<AuthPage mode="signin" />} />
        <Route path="/staff/home" element={<p>Staff home</p>} />
        <Route path="/manager/home" element={<p>Manager home</p>} />
        <Route path="/carrier" element={<p>Carrier portal</p>} />
        <Route path="/operations/sessions/abc" element={<p>Session page</p>} />
        <Route path="/" element={<p>General home</p>} />
      </Routes>
    </MemoryRouter>
  );
  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'someone@mland.test' } });
  fireEvent.change(document.getElementById('auth-password') as HTMLInputElement, { target: { value: 'secret123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
}

describe('After signing in (the real sign-in page)', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('a Staff lands on the Staff home', async () => {
    open('/login', 'STAFF');
    expect(await screen.findByText('Staff home')).toBeInTheDocument();
  });

  it('a Manager lands on the Manager home', async () => {
    open('/login', 'MANAGER');
    expect(await screen.findByText('Manager home')).toBeInTheDocument();
  });

  it('a Member keeps the general home', async () => {
    open('/login', 'MEMBER');
    expect(await screen.findByText('General home')).toBeInTheDocument();
  });

  it('a Carrier lands directly on the Carrier portal', async () => {
    open('/login', 'CARRIER');
    expect(await screen.findByText('Carrier portal')).toBeInTheDocument();
  });

  it('a Carrier ignores returnTo and lands directly on the Carrier portal (WF-11 / FR-003)', async () => {
    open('/login?returnTo=%2Foperations%2Fsessions%2Fabc', 'CARRIER');
    expect(await screen.findByText('Carrier portal')).toBeInTheDocument();
  });

  it('goes back to the page the user came from when that is a path on this site', async () => {
    open('/login?returnTo=%2Foperations%2Fsessions%2Fabc', 'STAFF');
    expect(await screen.findByText('Session page')).toBeInTheDocument();
  });

  it('ignores a returnTo that points to another site', async () => {
    open('/login?returnTo=https%3A%2F%2Fevil.example', 'STAFF');
    expect(await screen.findByText('Staff home')).toBeInTheDocument();
  });
});
