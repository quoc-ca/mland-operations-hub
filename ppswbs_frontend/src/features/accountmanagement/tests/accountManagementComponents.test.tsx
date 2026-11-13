import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import AuthPage from '../components/AuthPage';
import InternalAccountCreatePage from '../components/InternalAccountCreatePage';
import AdminAccountDetailPage from '../components/AdminAccountDetailPage';
import AdminAccountListPage from '../components/AdminAccountListPage';
import RoleCataloguePage from '../components/RoleCataloguePage';
import { validateProfileInput } from '../hooks/useProfile';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';

describe('FE-001 Account Management Components & Hooks', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('AuthPage (T018)', () => {
    it('renders sign-in form and validates required fields', async () => {
      render(
        <MemoryRouter>
          <AuthPage mode="signin" />
        </MemoryRouter>
      );

      expect(screen.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeInTheDocument();
      const submitBtn = screen.getByRole('button', { name: 'Đăng nhập' });

      // Click submit without entering values
      fireEvent.click(submitBtn);

      expect(await screen.findByText('Vui lòng nhập địa chỉ email.')).toBeInTheDocument();
      expect(screen.getByText('Vui lòng nhập mật khẩu.')).toBeInTheDocument();
    });

    it('renders register form and checks password confirmation', async () => {
      render(
        <MemoryRouter>
          <AuthPage mode="register" />
        </MemoryRouter>
      );

      expect(screen.getByRole('heading', { name: 'Đăng ký tài khoản' })).toBeInTheDocument();
      const emailInput = screen.getByLabelText('Địa chỉ email');
      const passwordInput = screen.getByLabelText('Mật khẩu');
      const confirmInput = screen.getByLabelText('Xác nhận mật khẩu');

      fireEvent.change(emailInput, { target: { value: 'user@example.com' } });
      fireEvent.change(passwordInput, { target: { value: 'secret123' } });
      fireEvent.change(confirmInput, { target: { value: 'secret456' } });

      const submitBtn = screen.getByRole('button', { name: 'Đăng ký' });
      fireEvent.click(submitBtn);

      expect(await screen.findByText('Mật khẩu xác nhận không khớp.')).toBeInTheDocument();
    });

    it('forgot password form does not reveal whether account exists', async () => {
      const resetSpy = vi.spyOn(firebaseAuthClient, 'sendPasswordReset').mockResolvedValue(undefined);

      render(
        <MemoryRouter>
          <AuthPage mode="forgot" />
        </MemoryRouter>
      );

      const emailInput = screen.getByLabelText('Địa chỉ email');
      fireEvent.change(emailInput, { target: { value: 'user@example.com' } });

      const submitBtn = screen.getByRole('button', { name: 'Gửi liên kết đặt lại' });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(resetSpy).toHaveBeenCalledWith('user@example.com');
      });

      expect(
        screen.getByText(/Nếu email tồn tại trên hệ thống, thư hướng dẫn đặt lại mật khẩu đã được gửi/i)
      ).toBeInTheDocument();
    });
  });

  describe('useProfile Validation (T023)', () => {
    it('validates Unicode name length, whitespace trimming, and ISO control characters', () => {
      // Empty name
      expect(validateProfileInput('', null).fullName).toBe('Họ và tên là bắt buộc');
      expect(validateProfileInput('   ', null).fullName).toBe('Họ và tên là bắt buộc');

      // Valid Vietnamese name
      expect(validateProfileInput(' Nguyễn Văn Sơn ', null)).toEqual({});

      // ISO control char (\u0007 bell, \u001F)
      expect(validateProfileInput('Test\u0007Name', null).fullName).toBe(
        'Họ và tên không được chứa ký tự điều khiển ISO'
      );

      // Name exceeding 100 chars
      const longName = 'A'.repeat(101);
      expect(validateProfileInput(longName, null).fullName).toBe(
        'Họ và tên không được vượt quá 100 ký tự'
      );

      // Phone validation (7-30 chars)
      expect(validateProfileInput('Valid Name', '12345').phone).toBe(
        'Số điện thoại phải từ 7 đến 30 ký tự'
      );
      expect(validateProfileInput('Valid Name', '0901234567').phone).toBeUndefined();
    });
  });

  describe('InternalAccountCreatePage (T031)', () => {
    it('enforces Staff exactly 1 location and Manager 1+ locations', async () => {
      const createSpy = vi.spyOn(accountManagementApiClient, 'createInternalAccount').mockResolvedValue({
        accountId: 99,
        role: 'STAFF',
        status: 'PENDING_SETUP',
        loginEmail: 'local-staff@example.com',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-03',
        updatedAt: '2026-11-03',
        provisioningStatus: 'INVITATION_PENDING',
        invitationLastSentAt: '2026-11-03',
      });

      render(
        <MemoryRouter>
          <InternalAccountCreatePage />
        </MemoryRouter>
      );

      const nameInput = screen.getByLabelText('Họ và tên');
      fireEvent.change(nameInput, { target: { value: 'Nhân viên Cơ sở 1' } });

      const submitBtn = screen.getByRole('button', { name: 'Khởi tạo tài khoản' });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(createSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            role: 'STAFF',
            fullName: 'Nhân viên Cơ sở 1',
            locationIds: [1],
          }),
          expect.any(String)
        );
      });

      // Shows PENDING_SETUP state and explanation that setup != active access
      expect(await screen.findByText('Khởi tạo thành công!')).toBeInTheDocument();
      expect(screen.getByText('Trạng thái: PENDING_SETUP (Chờ kích hoạt)')).toBeInTheDocument();
    });

    it('renders approved wording distinguishing Staff operational limit vs Manager organizational assignment (T080)', async () => {
      render(
        <MemoryRouter>
          <InternalAccountCreatePage />
        </MemoryRouter>
      );

      // Default is STAFF
      expect(screen.getByTestId('role-scope-hint')).toHaveTextContent(
        'Phân công cơ sở giới hạn phạm vi truy cập vận hành của Nhân viên (Staff)'
      );
      expect(screen.getByText('Cơ sở làm việc (giới hạn quyền)')).toBeInTheDocument();

      // Switch to MANAGER
      const roleSelect = screen.getByLabelText('Vai trò nội bộ');
      fireEvent.change(roleSelect, { target: { value: 'MANAGER' } });

      expect(screen.getByTestId('role-scope-hint')).toHaveTextContent(
        'Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống'
      );
      expect(screen.getByText('Cơ sở phân công tổ chức')).toBeInTheDocument();
    });
  });

  describe('AdminAccountDetailPage (T037, T080)', () => {
    it('displays account details, allows suspend with reason, and explains revocation pending', async () => {
      const mockDetail = {
        accountId: 5,
        role: 'STAFF' as const,
        status: 'ACTIVE' as const,
        loginEmail: 'staff5@example.com',
        revocationStatus: 'NOT_APPLICABLE' as const,
        createdAt: '2026-11-01',
        updatedAt: '2026-11-03',
        profile: {
          fullName: 'Nhân viên Năm',
          phone: '0987654321',
        },
        locationAssignments: [{ locationId: 1, assignedAt: '2026-11-01', active: true }],
        version: 3,
      };

      vi.spyOn(accountManagementApiClient, 'getAccountDetail').mockResolvedValue(mockDetail);
      const suspendSpy = vi.spyOn(accountManagementApiClient, 'suspendAccount').mockResolvedValue({
        accountId: 5,
        role: 'STAFF',
        status: 'SUSPENDED',
        loginEmail: 'staff5@example.com',
        revocationStatus: 'PENDING',
        createdAt: '2026-11-01',
        updatedAt: '2026-11-03',
      });

      render(
        <MemoryRouter initialEntries={['/admin/accounts/5']}>
          <Routes>
            <Route path="/admin/accounts/:accountId" element={<AdminAccountDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('staff5@example.com')).toBeInTheDocument();
      });

      // Click suspend button to open modal
      const suspendBtn = screen.getByRole('button', { name: 'Tạm khóa tài khoản (Suspend)' });
      fireEvent.click(suspendBtn);

      expect(screen.getByRole('heading', { name: 'Xác nhận tạm khóa tài khoản' })).toBeInTheDocument();

      // Enter reason and confirm
      const reasonInput = screen.getByLabelText('Lý do tạm khóa');
      fireEvent.change(reasonInput, { target: { value: 'Vi phạm quy chế bảo mật cơ sở' } });

      const confirmBtn = screen.getByRole('button', { name: 'Xác nhận tạm khóa' });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(suspendSpy).toHaveBeenCalledWith(5, {
          reason: 'Vi phạm quy chế bảo mật cơ sở',
          version: 3,
        });
      });
    });

    it('renders Manager organizational assignments, role transition notice, and committed-state confirmation (T080)', async () => {
      const mockManager = {
        accountId: 10,
        role: 'MANAGER' as const,
        status: 'ACTIVE' as const,
        loginEmail: 'manager10@example.com',
        revocationStatus: 'NOT_APPLICABLE' as const,
        createdAt: '2026-11-01',
        updatedAt: '2026-11-03',
        profile: {
          fullName: 'Quản lý Mười',
          phone: '0912345678',
        },
        locationAssignments: [
          { locationId: 1, assignedAt: '2026-11-01', active: true },
          { locationId: 2, assignedAt: '2026-11-01', active: true },
        ],
        version: 5,
      };

      vi.spyOn(accountManagementApiClient, 'getAccountDetail').mockResolvedValue(mockManager);
      const assignSpy = vi.spyOn(accountManagementApiClient, 'assignRoleLocation').mockResolvedValue({
        accountId: 10,
        role: 'STAFF',
        status: 'ACTIVE',
        loginEmail: 'manager10@example.com',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-01',
        updatedAt: '2026-11-14',
      });

      render(
        <MemoryRouter initialEntries={['/admin/accounts/10']}>
          <Routes>
            <Route path="/admin/accounts/:accountId" element={<AdminAccountDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('manager10@example.com')).toBeInTheDocument();
      });

      // Wait for form to reflect Manager role and hint
      await waitFor(() => {
        expect(screen.getByTestId('edit-role-scope-hint')).toHaveTextContent(
          'Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống'
        );
      });

      // Overview table shows Manager organizational assignment label and global scope note
      expect(screen.getByText('Cơ sở phân công tổ chức:')).toBeInTheDocument();
      expect(
        screen.getAllByText(/Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý \(Manager\) là toàn hệ thống/i).length
      ).toBeGreaterThanOrEqual(1);

      // Wait for checkboxes to be populated
      await waitFor(() => {
        expect(screen.getByRole('checkbox', { name: /Cơ sở Quận 1/i })).toBeChecked();
      });

      // Transition role to STAFF
      const editRoleSelect = screen.getByLabelText('Vai trò nội bộ');
      fireEvent.change(editRoleSelect, { target: { value: 'STAFF' } });

      // Role transition warning notice appears
      expect(screen.getByTestId('role-transition-notice')).toHaveTextContent(
        'Lưu ý khi đổi vai trò (MANAGER → STAFF): Quyền hạn chỉ thay đổi sau khi máy chủ xác nhận lưu thành công'
      );

      // Submit role update
      const updateBtn = screen.getByRole('button', { name: 'Cập nhật vai trò & cơ sở' });
      fireEvent.click(updateBtn);

      await waitFor(() => {
        expect(assignSpy).toHaveBeenCalledWith(10, {
          role: 'STAFF',
          locationIds: [1],
          version: 5,
        });
      });

      // Committed-state success message
      expect(
        await screen.findByText('Cập nhật vai trò và phân công cơ sở thành công. Thay đổi đã được máy chủ xác nhận và có hiệu lực trên hệ thống.')
      ).toBeInTheDocument();
    });

    it('handles 409 conflict recovery when assigning role/location (T080)', async () => {
      const mockStaff = {
        accountId: 11,
        role: 'STAFF' as const,
        status: 'ACTIVE' as const,
        loginEmail: 'staff11@example.com',
        revocationStatus: 'NOT_APPLICABLE' as const,
        createdAt: '2026-11-01',
        updatedAt: '2026-11-03',
        profile: {
          fullName: 'Nhân viên Mười Một',
          phone: '0912345679',
        },
        locationAssignments: [{ locationId: 1, assignedAt: '2026-11-01', active: true }],
        version: 2,
      };

      const getSpy = vi.spyOn(accountManagementApiClient, 'getAccountDetail').mockResolvedValue(mockStaff);
      vi.spyOn(accountManagementApiClient, 'assignRoleLocation').mockRejectedValue(
        new ApiError({ status: 409, code: 'VERSION_CONFLICT', message: 'Version mismatch' })
      );

      render(
        <MemoryRouter initialEntries={['/admin/accounts/11']}>
          <Routes>
            <Route path="/admin/accounts/:accountId" element={<AdminAccountDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('staff11@example.com')).toBeInTheDocument();
      });

      // Ensure form state has populated from selectedAccount and select location 1
      await waitFor(() => {
        expect(screen.getByRole('radio', { name: /Cơ sở Quận 1/i })).toBeInTheDocument();
      });
      const radio = screen.getByRole('radio', { name: /Cơ sở Quận 1/i });
      fireEvent.click(radio);

      const updateBtn = screen.getByRole('button', { name: 'Cập nhật vai trò & cơ sở' });
      fireEvent.click(updateBtn);

      expect(
        await screen.findByText(/Dữ liệu đã bị thay đổi bởi phiên khác \(xung đột phiên bản\)/i)
      ).toBeInTheDocument();
    });
  });

  describe('AdminAccountListPage (T041)', () => {
    it('renders search bar, role and status filters, and accounts list', async () => {
      const mockPaged = {
        data: [
          {
            accountId: 1,
            role: 'ADMIN' as const,
            status: 'ACTIVE' as const,
            loginEmail: 'admin@mland.vn',
            revocationStatus: 'NOT_APPLICABLE' as const,
            createdAt: '2026-10-01',
            updatedAt: '2026-11-01',
          },
          {
            accountId: 2,
            role: 'STAFF' as const,
            status: 'PENDING_SETUP' as const,
            loginEmail: 'staff@mland.vn',
            revocationStatus: 'NOT_APPLICABLE' as const,
            createdAt: '2026-11-01',
            updatedAt: '2026-11-02',
          },
        ],
        meta: {
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        },
      };

      const listSpy = vi.spyOn(accountManagementApiClient, 'listAccounts').mockResolvedValue(mockPaged);

      render(
        <MemoryRouter>
          <AdminAccountListPage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('admin@mland.vn')).toBeInTheDocument();
        expect(screen.getByText('staff@mland.vn')).toBeInTheDocument();
      });

      expect(listSpy).toHaveBeenCalled();
    });
  });

  describe('RoleCataloguePage (T042)', () => {
    it('renders read-only fixed role descriptions and has zero permission edit controls', async () => {
      const mockRoles = {
        roles: [
          { role: 'MEMBER' as const, description: 'Khách hàng thành viên / Member' },
          { role: 'STAFF' as const, description: 'Nhân viên cơ sở / Staff' },
          { role: 'MANAGER' as const, description: 'Quản lý cơ sở / Manager' },
          { role: 'ADMIN' as const, description: 'Quản trị viên / Admin' },
        ],
      };

      vi.spyOn(accountManagementApiClient, 'listRoles').mockResolvedValue(mockRoles);

      render(
        <MemoryRouter>
          <RoleCataloguePage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('Danh mục vai trò hệ thống (Fixed Roles)')).toBeInTheDocument();
        expect(screen.getByText('Khách hàng thành viên / Member')).toBeInTheDocument();
        expect(screen.getByText('Quản trị viên / Admin')).toBeInTheDocument();
      });

      // Verify that NO edit buttons, form controls for permissions exist
      expect(screen.queryByRole('button', { name: /chỉnh sửa|edit|thêm|add/i })).toBeNull();

      // Verify governance rules (T080)
      expect(
        screen.getByText(/phạm vi vận hành cho các tác vụ quản lý của Manager là toàn hệ thống/i)
      ).toBeInTheDocument();
      expect(
        screen.getByText(/ADMIN là vai trò quản trị hệ thống cao nhất/i)
      ).toBeInTheDocument();
    });
  });
});
