import React from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { json, mockFetch } from '../../test/render';
import { AuthProvider } from './AuthProvider';
import { clearRoleCache, useRole } from './useRole';

const PROFILE = 'GET /api/v1/accounts/me/profile';

/** A session whose sign-in changes can be driven by the test. */
function controllableSession(first) {
  let emit = () => {};
  const source = {
    watch: listener => { emit = listener; listener(first); return () => {}; },
    getIdToken: vi.fn(async () => 'test-token'),
    signOut: vi.fn(async () => {}),
  };
  return { source, signIn: next => act(() => emit(next)) };
}

const wrapper = source => ({ children }) => <AuthProvider source={source}>{children}</AuthProvider>;

describe('useRole', () => {
  beforeEach(() => clearRoleCache());

  it('reads the signed-in account role once and keeps it', async () => {
    const calls = mockFetch({ [PROFILE]: json(200, { role: 'MANAGER' }) });
    const { source } = controllableSession({ status: 'signed-in', userId: 'u1' });
    const { result, rerender } = renderHook(() => useRole(), { wrapper: wrapper(source) });

    await waitFor(() => expect(result.current).toBe('MANAGER'));
    rerender();
    expect(calls.filter(call => call.path === '/api/v1/accounts/me/profile')).toHaveLength(1);
  });

  it('drops the role of the previous account when another account signs in in the same tab', async () => {
    const roles = ['MEMBER', 'MANAGER'];
    mockFetch({ [PROFILE]: () => json(200, { role: roles.shift() }) });
    const { source, signIn } = controllableSession({ status: 'signed-in', userId: 'member-1' });
    const { result } = renderHook(() => useRole(), { wrapper: wrapper(source) });
    await waitFor(() => expect(result.current).toBe('MEMBER'));

    // The Member never signed out through the app; a Manager signs in straight away.
    await signIn({ status: 'signed-in', userId: 'manager-1' });

    await waitFor(() => expect(result.current).toBe('MANAGER'));
  });

  it('has no role when nobody is signed in', async () => {
    mockFetch({});
    const { source } = controllableSession({ status: 'signed-out', userId: null });
    const { result } = renderHook(() => useRole(), { wrapper: wrapper(source) });

    expect(result.current).toBeNull();
  });
});
