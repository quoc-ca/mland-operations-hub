import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import AuthPage from '../components/AuthPage';
import ProfilePage from '../components/ProfilePage';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import type { User } from 'firebase/auth';

const mockNavigate = vi.fn();
vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('T064–T068: Google Sign-In & Credential Re-authentication (FR-002, FR-005, US1, US2, Scenario V-12)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockNavigate.mockClear();
  });

  describe('T064: FirebaseAuthClient Google & Re-auth methods', () => {
    it('hasPasswordProvider returns true when password provider is present', () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue({
        email: 'user@example.com',
        providerData: [{ providerId: 'password' }],
      } as any);

      expect(firebaseAuthClient.hasPasswordProvider()).toBe(true);
    });

    it('hasPasswordProvider returns false for OAuth-only user (e.g. Google)', () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue({
        email: 'googleuser@gmail.com',
        providerData: [{ providerId: 'google.com' }],
      } as any);

      expect(firebaseAuthClient.hasPasswordProvider()).toBe(false);
    });

    it('reauthenticateWithCredential throws if no authenticated user exists', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(null);

      await expect(firebaseAuthClient.reauthenticateWithCredential('secret123')).rejects.toThrow(
        'No authenticated user with email found to re-authenticate'
      );
    });
  });

  describe('T065: AuthPage Google Sign-In UI & Redirect Handling', () => {
    it('renders "Đăng nhập với Google" button prominently on both signin and register modes', () => {
      vi.spyOn(firebaseAuthClient, 'isConfigured').mockReturnValue(true);
      vi.spyOn(firebaseAuthClient, 'getRedirectAuthResult').mockResolvedValue(null);

      const { unmount } = render(
        <MemoryRouter>
          <AuthPage mode="signin" />
        </MemoryRouter>
      );

      const googleBtnSignIn = screen.getByRole('button', { name: /đăng nhập với google/i });
      expect(googleBtnSignIn).toBeInTheDocument();
      unmount();

      render(
        <MemoryRouter>
          <AuthPage mode="register" />
        </MemoryRouter>
      );

      const googleBtnRegister = screen.getByRole('button', { name: /đăng nhập với google/i });
      expect(googleBtnRegister).toBeInTheDocument();
    });

    it('does not render Google button on forgot-password mode', () => {
      vi.spyOn(firebaseAuthClient, 'isConfigured').mockReturnValue(true);
      vi.spyOn(firebaseAuthClient, 'getRedirectAuthResult').mockResolvedValue(null);

      render(
        <MemoryRouter>
          <AuthPage mode="forgot" />
        </MemoryRouter>
      );

      expect(screen.queryByRole('button', { name: /đăng nhập với google/i })).not.toBeInTheDocument();
    });

    it('triggers signInWithGoogleRedirect when Google button is clicked', async () => {
      vi.spyOn(firebaseAuthClient, 'isConfigured').mockReturnValue(true);
      vi.spyOn(firebaseAuthClient, 'getRedirectAuthResult').mockResolvedValue(null);
      const redirectSpy = vi.spyOn(firebaseAuthClient, 'signInWithGoogleRedirect').mockResolvedValue(undefined);

      render(
        <MemoryRouter>
          <AuthPage mode="signin" />
        </MemoryRouter>
      );

      const googleBtn = screen.getByRole('button', { name: /đăng nhập với google/i });
      fireEvent.click(googleBtn);

      await waitFor(() => {
        expect(redirectSpy).toHaveBeenCalledTimes(1);
      });
    });

    it('processes redirect result on mount: bootstraps account and navigates to Home (/)', async () => {
      const mockUser = { uid: 'google-uid-123', email: 'googleuser@gmail.com' } as User;
      vi.spyOn(firebaseAuthClient, 'isConfigured').mockReturnValue(true);
      vi.spyOn(firebaseAuthClient, 'getRedirectAuthResult').mockResolvedValue(mockUser);
      const bootstrapSpy = vi.spyOn(accountManagementApiClient, 'bootstrapOwnAccount').mockResolvedValue({
        id: 101,
        email: 'googleuser@gmail.com',
        fullName: 'googleuser',
        role: 'MEMBER',
        status: 'ACTIVE',
        emailVerified: true,
        createdAt: '2026-11-04',
      });

      render(
        <MemoryRouter>
          <AuthPage mode="signin" />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(bootstrapSpy).toHaveBeenCalledTimes(1);
        expect(mockNavigate).toHaveBeenCalledWith('/');
      });
    });

    it('handles auth/account-exists-with-different-credential with safe guidance', async () => {
      const collisionError = {
        code: 'auth/account-exists-with-different-credential',
        message: 'Account exists with different credential',
      };
      vi.spyOn(firebaseAuthClient, 'isConfigured').mockReturnValue(true);
      vi.spyOn(firebaseAuthClient, 'getRedirectAuthResult').mockRejectedValue(collisionError);
      const bootstrapSpy = vi.spyOn(accountManagementApiClient, 'bootstrapOwnAccount');

      render(
        <MemoryRouter>
          <AuthPage mode="signin" />
        </MemoryRouter>
      );

      expect(
        await screen.findByText(
          'Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc.'
        )
      ).toBeInTheDocument();
      expect(bootstrapSpy).not.toHaveBeenCalled();
      expect(mockNavigate).not.toHaveBeenCalled();
    });
  });

  describe('T066: ProfilePage In-Place Password Re-authentication & Provider Check', () => {
    const passwordUser: any = {
      uid: 'pwd-user-1',
      email: 'member@mland.vn',
      emailVerified: true,
      providerData: [{ providerId: 'password' }],
      reload: vi.fn().mockResolvedValue(undefined),
      getIdToken: vi.fn().mockResolvedValue('token-123'),
    };

    const googleUser: any = {
      uid: 'goog-user-1',
      email: 'google@mland.vn',
      emailVerified: true,
      providerData: [{ providerId: 'google.com' }],
      reload: vi.fn().mockResolvedValue(undefined),
      getIdToken: vi.fn().mockResolvedValue('token-456'),
    };

    const baseProfile = {
      id: 50,
      email: 'member@mland.vn',
      fullName: 'Phan Sơn',
      phone: '0901234567',
      role: 'MEMBER' as const,
      status: 'ACTIVE' as const,
      emailVerified: true,
      createdAt: '2026-11-01',
    };

    it('renders 3-field Password Change form when user has password provider', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(passwordUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(baseProfile);

      const { container } = render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      expect(await screen.findByRole('heading', { name: 'Đổi mật khẩu' })).toBeInTheDocument();
      expect(container.querySelector('#current-password')).toBeInTheDocument();
      expect(container.querySelector('#new-password')).toBeInTheDocument();
      expect(container.querySelector('#confirm-new-password')).toBeInTheDocument();

      // Quick action button in overview
      const overviewButtons = screen.getAllByRole('button', { name: 'Đổi mật khẩu' });
      expect(overviewButtons.length).toBeGreaterThanOrEqual(1);
    });

    it('hides Password Change section and quick-action trigger when user lacks password provider', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(googleUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue({
        ...baseProfile,
        email: 'google@mland.vn',
      });

      render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      // Wait for profile to load
      await screen.findByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' });

      // Password section heading should NOT be present
      expect(screen.queryByRole('heading', { name: 'Đổi mật khẩu' })).not.toBeInTheDocument();
      // Quick action trigger should NOT be present
      expect(screen.queryByRole('button', { name: 'Đổi mật khẩu' })).not.toBeInTheDocument();
      // Email change informs that Google manages the email
      expect(
        screen.getByText(/Tài khoản của bạn đăng nhập bằng nhà cung cấp bên ngoài \(Google\)/i)
      ).toBeInTheDocument();
    });

    it('re-auth failure clears ONLY currentPassword, shows inline error, and retains newPassword/confirmPassword', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(passwordUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(baseProfile);

      const reauthSpy = vi.spyOn(firebaseAuthClient, 'reauthenticateWithCredential').mockRejectedValue({
        code: 'auth/wrong-password',
        message: 'Wrong password',
      });
      const updatePwdSpy = vi.spyOn(firebaseAuthClient, 'updatePassword');

      const { container } = render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      await screen.findByRole('heading', { name: 'Đổi mật khẩu' });

      const currentInput = container.querySelector('#current-password') as HTMLInputElement;
      const newInput = container.querySelector('#new-password') as HTMLInputElement;
      const confirmInput = container.querySelector('#confirm-new-password') as HTMLInputElement;

      fireEvent.change(currentInput, { target: { value: 'wrong-pass' } });
      fireEvent.change(newInput, { target: { value: 'new-valid-pass' } });
      fireEvent.change(confirmInput, { target: { value: 'new-valid-pass' } });

      const submitBtn = screen.getByRole('button', { name: 'Cập nhật mật khẩu' });
      fireEvent.click(submitBtn);

      expect(await screen.findByText('Mật khẩu hiện tại không chính xác.')).toBeInTheDocument();
      expect(reauthSpy).toHaveBeenCalledWith('wrong-pass');
      expect(updatePwdSpy).not.toHaveBeenCalled();

      // Current password is cleared, while newPassword and confirmPassword remain in React state
      expect(currentInput).toHaveValue('');
      expect(newInput).toHaveValue('new-valid-pass');
      expect(confirmInput).toHaveValue('new-valid-pass');
    });

    it('successful password change calls reauthenticateWithCredential then updatePassword and clears all fields', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(passwordUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(baseProfile);

      const reauthSpy = vi.spyOn(firebaseAuthClient, 'reauthenticateWithCredential').mockResolvedValue(undefined);
      const updatePwdSpy = vi.spyOn(firebaseAuthClient, 'updatePassword').mockResolvedValue(undefined);

      const { container } = render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      await screen.findByRole('heading', { name: 'Đổi mật khẩu' });

      const currentInput = container.querySelector('#current-password') as HTMLInputElement;
      const newInput = container.querySelector('#new-password') as HTMLInputElement;
      const confirmInput = container.querySelector('#confirm-new-password') as HTMLInputElement;

      fireEvent.change(currentInput, { target: { value: 'current-correct-pass' } });
      fireEvent.change(newInput, { target: { value: 'new-secret-123' } });
      fireEvent.change(confirmInput, { target: { value: 'new-secret-123' } });

      const submitBtn = screen.getByRole('button', { name: 'Cập nhật mật khẩu' });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(reauthSpy).toHaveBeenCalledWith('current-correct-pass');
        expect(updatePwdSpy).toHaveBeenCalledWith('new-secret-123');
      });

      expect(await screen.findByText('Đổi mật khẩu thành công.')).toBeInTheDocument();
      expect(currentInput).toHaveValue('');
      expect(newInput).toHaveValue('');
      expect(confirmInput).toHaveValue('');
    });
  });

  describe('T067: ProfilePage In-Place Email Change Re-authentication', () => {
    const passwordUser: any = {
      uid: 'pwd-user-2',
      email: 'owner@mland.vn',
      emailVerified: true,
      providerData: [{ providerId: 'password' }],
      reload: vi.fn().mockResolvedValue(undefined),
      getIdToken: vi.fn().mockResolvedValue('token-789'),
    };

    const baseProfile = {
      id: 55,
      email: 'owner@mland.vn',
      fullName: 'Phan Sơn Owner',
      phone: '0901234567',
      role: 'MEMBER' as const,
      status: 'ACTIVE' as const,
      emailVerified: true,
      createdAt: '2026-11-01',
    };

    it('email change failure clears ONLY current password, shows inline error, and retains proposed new email', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(passwordUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(baseProfile);

      const reauthSpy = vi.spyOn(firebaseAuthClient, 'reauthenticateWithCredential').mockRejectedValue({
        code: 'auth/invalid-credential',
        message: 'Invalid credential',
      });
      const verifyEmailSpy = vi.spyOn(firebaseAuthClient, 'verifyBeforeUpdateEmail');

      const { container } = render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      await screen.findByRole('heading', { name: 'Thay đổi địa chỉ email' });

      const currentInput = container.querySelector('#email-current-password') as HTMLInputElement;
      const newEmailInput = container.querySelector('#new-email') as HTMLInputElement;

      fireEvent.change(currentInput, { target: { value: 'bad-password' } });
      fireEvent.change(newEmailInput, { target: { value: 'newaddress@mland.vn' } });

      const submitBtn = screen.getByRole('button', { name: 'Yêu cầu đổi email' });
      fireEvent.click(submitBtn);

      expect(await screen.findByText('Mật khẩu hiện tại không chính xác.')).toBeInTheDocument();
      expect(reauthSpy).toHaveBeenCalledWith('bad-password');
      expect(verifyEmailSpy).not.toHaveBeenCalled();

      expect(currentInput).toHaveValue('');
      expect(newEmailInput).toHaveValue('newaddress@mland.vn');
    });

    it('successful email change calls reauthenticateWithCredential then verifyBeforeUpdateEmail and clears all fields', async () => {
      vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(passwordUser);
      vi.spyOn(accountManagementApiClient, 'getOwnProfile').mockResolvedValue(baseProfile);

      const reauthSpy = vi.spyOn(firebaseAuthClient, 'reauthenticateWithCredential').mockResolvedValue(undefined);
      const verifyEmailSpy = vi.spyOn(firebaseAuthClient, 'verifyBeforeUpdateEmail').mockResolvedValue(undefined);

      const { container } = render(
        <MemoryRouter>
          <ProfilePage initialMode="overview" />
        </MemoryRouter>
      );

      await screen.findByRole('heading', { name: 'Thay đổi địa chỉ email' });

      const currentInput = container.querySelector('#email-current-password') as HTMLInputElement;
      const newEmailInput = container.querySelector('#new-email') as HTMLInputElement;

      fireEvent.change(currentInput, { target: { value: 'correct-password' } });
      fireEvent.change(newEmailInput, { target: { value: 'updated@mland.vn' } });

      const submitBtn = screen.getByRole('button', { name: 'Yêu cầu đổi email' });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(reauthSpy).toHaveBeenCalledWith('correct-password');
        expect(verifyEmailSpy).toHaveBeenCalledWith('updated@mland.vn');
      });

      expect(
        await screen.findByText(
          /Thư xác nhận đã được gửi đến updated@mland.vn\. Email hiện tại của bạn sẽ được giữ nguyên cho đến khi xác minh hoàn tất\./i
        )
      ).toBeInTheDocument();

      expect(currentInput).toHaveValue('');
      expect(newEmailInput).toHaveValue('');
    });
  });
});
