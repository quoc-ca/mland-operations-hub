import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderHook, act } from '@testing-library/react';
import { useVerifiedEmailGate } from '../hooks/useVerifiedEmailGate';
import ProfilePage from '../components/ProfilePage';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';
import { MemoryRouter } from 'react-router';

describe('WF-08 / T051 / T052 Verified Email Gate & Profile Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('verifies Profile entry when user email is verified in Firebase (same-browser / other-device)', async () => {
    const mockUser = {
      uid: 'user-123',
      email: 'test@example.com',
      emailVerified: true,
      reload: vi.fn().mockResolvedValue(undefined),
      getIdToken: vi.fn().mockResolvedValue('fresh-token'),
    };

    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);
    mockUser.reload.mockImplementation(async () => {
      mockUser.emailVerified = true;
    });
    const syncSpy = vi.spyOn(accountManagementApiClient, 'syncOwnEmail').mockResolvedValue({
      id: '1',
      email: 'test@example.com',
      emailVerified: true,
      role: 'MEMBER',
      status: 'ACTIVE',
      locations: [],
      version: 1,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-03',
    });

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '1', initialVerified: false })
    );

    let checkResult: boolean = false;
    await act(async () => {
      checkResult = await result.current.checkGate();
    });

    expect(checkResult).toBe(true);
    expect(mockUser.reload).toHaveBeenCalled();
    expect(syncSpy).toHaveBeenCalledTimes(1);
    expect(result.current.isVerified).toBe(true);
    expect(result.current.gateState).toBe('verified');
  });

  it('keeps gate unverified and does not call syncOwnEmail when user remains unverified', async () => {
    const mockUser = {
      uid: 'user-123',
      email: 'unverified@example.com',
      emailVerified: false,
      reload: vi.fn().mockResolvedValue(undefined),
    };

    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);
    const syncSpy = vi.spyOn(accountManagementApiClient, 'syncOwnEmail');

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '1', initialVerified: false })
    );

    let checkResult: boolean = true;
    await act(async () => {
      checkResult = await result.current.checkGate();
    });

    expect(checkResult).toBe(false);
    expect(mockUser.reload).toHaveBeenCalled();
    expect(syncSpy).not.toHaveBeenCalled();
    expect(result.current.isVerified).toBe(false);
    expect(result.current.gateState).toBe('unverified');
  });

  it('discards session and triggers onSessionExpired on 401 / missing session', async () => {
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(null);
    vi.spyOn(firebaseAuthClient, 'reloadUser').mockResolvedValue(undefined);
    const expiredSpy = vi.fn();

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '1', onSessionExpired: expiredSpy })
    );

    await act(async () => {
      await result.current.checkGate();
    });

    expect(expiredSpy).toHaveBeenCalledTimes(1);
    expect(result.current.gateState).toBe('error');
    expect(result.current.error).toBeDefined();
  });

  it('handles Firebase reload failure safely without crashing', async () => {
    const reloadError = new Error('Network error on reload');
    vi.spyOn(firebaseAuthClient, 'reloadUser').mockRejectedValue(reloadError);

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '1' })
    );

    await act(async () => {
      const res = await result.current.checkGate();
      expect(res).toBe(false);
    });

    expect(result.current.gateState).toBe('error');
    expect(result.current.error).toBe(reloadError);
  });

  it('handles PUT /accounts/me/email 503 error without background retry, preserving safe input', async () => {
    const mockUser = {
      uid: 'user-123',
      emailVerified: true,
      reload: vi.fn().mockResolvedValue(undefined),
    };
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);

    const apiError503 = new ApiError({
      status: 503,
      code: 'SERVICE_UNAVAILABLE',
      message: 'Email sync service is temporarily unavailable',
    });
    const syncSpy = vi.spyOn(accountManagementApiClient, 'syncOwnEmail').mockRejectedValue(apiError503);

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '1' })
    );

    await act(async () => {
      const ok = await result.current.checkGate();
      expect(ok).toBe(false);
    });

    expect(result.current.gateState).toBe('error');
    expect(result.current.error).toBe(apiError503);
    // Verified: No background auto-retry; single call
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('enforces 90-second Account-ID-keyed localStorage cooldown on verification resend', async () => {
    const mockUser = { uid: 'user-999', email: 'test@example.com' };
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);
    const sendSpy = vi.spyOn(firebaseAuthClient, 'sendVerificationEmail').mockResolvedValue(undefined);

    const accountId = '42';
    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId })
    );

    expect(result.current.canResend).toBe(true);
    expect(result.current.resendCooldownSeconds).toBe(0);

    let resendSuccess = false;
    await act(async () => {
      resendSuccess = await result.current.resendVerification();
    });

    expect(resendSuccess).toBe(true);
    expect(sendSpy).toHaveBeenCalledTimes(1);
    expect(result.current.canResend).toBe(false);
    expect(result.current.resendCooldownSeconds).toBeGreaterThan(0);

    // Verify localStorage key format and content: ONLY expiration timestamp stored; NO tokens, emails or UID
    const stored = localStorage.getItem(`account_resend_cooldown_${accountId}`);
    expect(stored).toBeDefined();
    expect(Number(stored)).toBeGreaterThan(Date.now());
    expect(stored).not.toContain('test@example.com');
    expect(stored).not.toContain('user-999');

    // Attempting resend during cooldown is rejected immediately
    await act(async () => {
      const secondAttempt = await result.current.resendVerification();
      expect(secondAttempt).toBe(false);
    });
    expect(sendSpy).toHaveBeenCalledTimes(1);
  });

  it('restores cooldown from localStorage across simulated browser restart', () => {
    const accountId = '42';
    const futureExpiry = Date.now() + 45000; // 45 seconds remaining
    localStorage.setItem(`account_resend_cooldown_${accountId}`, String(futureExpiry));

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId })
    );

    expect(result.current.canResend).toBe(false);
    expect(result.current.resendCooldownSeconds).toBeGreaterThanOrEqual(44);
    expect(result.current.resendCooldownSeconds).toBeLessThanOrEqual(46);
  });

  it('ProfilePage renders accessible reload icon with tooltip/aria-label and disables save when unverified', async () => {
    const mockProfile = {
      id: '10',
      email: 'member@example.com',
      emailVerified: false,
      role: 'MEMBER' as const,
      status: 'ACTIVE' as const,
      locations: [],
      version: 1,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-03',
      fullName: 'Nguyễn Văn A',
      phone: '0901234567',
    };

    vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(mockProfile);
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue({
      uid: 'user-10',
      email: 'member@example.com',
      emailVerified: false,
      reload: vi.fn().mockResolvedValue(undefined),
    } as any);

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    // Wait for profile loading
    await waitFor(() => {
      expect(screen.getByText('Hồ sơ cá nhân & Bảo mật')).toBeInTheDocument();
    });

    // Check accessible reload button
    const reloadButton = screen.getByRole('button', {
      name: 'Kiểm tra lại trạng thái xác thực email',
    });
    expect(reloadButton).toBeInTheDocument();
    expect(reloadButton).toHaveAttribute('title', 'Tải lại trạng thái xác thực từ hệ thống');

    // Check that Save button is disabled because email is unverified
    const saveButton = screen.getByRole('button', { name: 'Lưu thay đổi' });
    expect(saveButton).toBeDisabled();

    // Check unverified reminder banner
    expect(screen.getByText('Email của bạn chưa được xác thực!')).toBeInTheDocument();
  });

  it('calls verifyBeforeUpdateEmail and retains current email visible while verification is pending', async () => {
    const mockProfile = {
      id: '10',
      email: 'current@example.com',
      emailVerified: true,
      role: 'MEMBER' as const,
      status: 'ACTIVE' as const,
      locations: [],
      version: 1,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-03',
      fullName: 'Trần Văn B',
      phone: null,
    };

    vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(mockProfile);
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue({
      uid: 'user-10',
      email: 'current@example.com',
      emailVerified: true,
      reload: vi.fn().mockResolvedValue(undefined),
      getIdToken: vi.fn().mockResolvedValue('token-10'),
    } as any);
    vi.spyOn(accountManagementApiClient, 'syncOwnEmail').mockResolvedValue(mockProfile);

    const updateEmailSpy = vi
      .spyOn(firebaseAuthClient, 'verifyBeforeUpdateEmail')
      .mockResolvedValue(undefined);

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Hồ sơ cá nhân & Bảo mật')).toBeInTheDocument();
    });

    const emailInput = screen.getByPlaceholderText('nhap.email.moi@example.com');
    fireEvent.change(emailInput, { target: { value: 'proposed.new@example.com' } });

    const submitBtn = screen.getByRole('button', { name: 'Yêu cầu đổi email' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateEmailSpy).toHaveBeenCalledWith('proposed.new@example.com');
    });

    // Current login email in the input remains unchanged
    const currentEmailField = screen.getByLabelText('Địa chỉ đăng nhập');
    expect(currentEmailField).toHaveValue('current@example.com');

    // Success notice shown explaining that current email remains active
    expect(
      screen.getByText(/Email hiện tại của bạn sẽ được giữ nguyên cho đến khi xác minh hoàn tất/i)
    ).toBeInTheDocument();
  });

  it('deduplicates concurrent checkGate calls so only a single syncOwnEmail request is in flight', async () => {
    const mockUser = {
      uid: 'user-concurrent',
      email: 'concurrent@example.com',
      emailVerified: true,
      reload: vi.fn().mockImplementation(async () => {
        await new Promise(r => setTimeout(r, 20));
      }),
      getIdToken: vi.fn().mockResolvedValue('fresh-token'),
    };
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);

    let syncCallCount = 0;
    const syncSpy = vi.spyOn(accountManagementApiClient, 'syncOwnEmail').mockImplementation(async () => {
      syncCallCount++;
      await new Promise(r => setTimeout(r, 30));
      return {
        id: '2',
        email: 'concurrent@example.com',
        emailVerified: true,
        role: 'MEMBER',
        status: 'ACTIVE',
        locations: [],
        version: 1,
        createdAt: '2026-11-01',
        updatedAt: '2026-11-03',
      };
    });

    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '2', initialVerified: false })
    );

    // Trigger two checkGate calls concurrently (e.g. StrictMode duplicate mount or re-render)
    let res1 = false;
    let res2 = false;
    await act(async () => {
      const results = await Promise.all([
        result.current.checkGate(),
        result.current.checkGate(),
      ]);
      res1 = results[0];
      res2 = results[1];
    });

    expect(res1).toBe(true);
    expect(res2).toBe(true);
    // Verified: Exactly 1 sync call occurred, eliminating optimistic lock collisions
    expect(syncSpy).toHaveBeenCalledTimes(1);
    expect(syncCallCount).toBe(1);
  });

  it('handles token refresh 400 / invalid token by clearing session and triggering onSessionExpired', async () => {
    const signOutSpy = vi.spyOn(firebaseAuthClient, 'signOut').mockResolvedValue(undefined);
    const mockUser = {
      uid: 'user-expired',
      email: 'expired@example.com',
      emailVerified: true,
      reload: vi.fn().mockRejectedValue({
        code: 'auth/user-token-expired',
        message: '400 (Bad Request): INVALID_REFRESH_TOKEN',
      }),
    };
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser as any);

    const sessionExpiredSpy = vi.fn();
    const { result } = renderHook(() =>
      useVerifiedEmailGate({ accountId: '3', onSessionExpired: sessionExpiredSpy })
    );

    let checkRes = true;
    await act(async () => {
      checkRes = await result.current.checkGate();
    });

    expect(checkRes).toBe(false);
    expect(result.current.gateState).toBe('error');
    expect(sessionExpiredSpy).toHaveBeenCalledTimes(1);
    expect(signOutSpy).toHaveBeenCalled();
  });
});
