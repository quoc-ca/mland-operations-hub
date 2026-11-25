import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { InternalAccountCreatePage } from '../components/InternalAccountCreatePage';
import { AdminAccountDetailPage } from '../components/AdminAccountDetailPage';
import { InternalAccountSetupPage } from '../components/InternalAccountSetupPage';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';

const renderWithProviders = (ui: React.ReactElement, initialRoute: string = '/') => {
  return render(
    <I18nProvider initialLocale="vi">
      <MemoryRouter initialEntries={[initialRoute]}>
        {ui}
      </MemoryRouter>
    </I18nProvider>
  );
};

describe('R-INV Frontend Implementation (T112, T113, T115)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('InternalAccountCreatePage — Validation & Mandatory Email (FR-048)', () => {
    it('requires email and fullName for STAFF, rejects submission when email is missing, and provides no password input or sendNotification checkbox', async () => {
      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);

      renderWithProviders(<InternalAccountCreatePage />);

      // Wait for location to load
      await waitFor(() => {
        expect(screen.getByText('Chi nhánh Quận 1')).toBeInTheDocument();
      });

      // Assert password input is NOT rendered for STAFF
      expect(screen.queryByLabelText(/Mật khẩu/i)).not.toBeInTheDocument();

      // Assert sendNotification checkbox is NOT rendered (invitation is mandatory)
      expect(screen.queryByLabelText(/Gửi thông báo hướng dẫn/i)).not.toBeInTheDocument();

      // Try submitting empty form
      const submitBtn = screen.getByRole('button', { name: /Khởi tạo tài khoản/i });
      await userEvent.click(submitBtn);

      // Validation errors
      expect(await screen.findByText('Họ và tên là bắt buộc.')).toBeInTheDocument();
      expect(screen.getByText('Email đăng nhập là bắt buộc đối với Đối tác vận chuyển.')).toBeInTheDocument();
    });
  });

  describe('InternalAccountCreatePage — Email Collision Resolution (FR-049, FR-050)', () => {
    it('redirects to account maintenance when collision outcome is STAFF_MANAGER_MAINTENANCE', async () => {
      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);
      vi.spyOn(accountManagementApiClient, 'resolveEmailCollision').mockResolvedValue({
        normalizedEmail: 'existing.staff@mland.vn',
        outcome: 'STAFF_MANAGER_MAINTENANCE',
        accountId: 42,
      });

      renderWithProviders(<InternalAccountCreatePage />);

      await waitFor(() => {
        expect(screen.getByText('Chi nhánh Quận 1')).toBeInTheDocument();
      });

      const emailInput = screen.getByLabelText(/Email định danh/i);
      const nameInput = screen.getByLabelText(/Họ và tên/i);

      await userEvent.type(emailInput, 'existing.staff@mland.vn');
      await userEvent.type(nameInput, 'Nguyễn Văn Test');

      const submitBtn = screen.getByRole('button', { name: /Khởi tạo tài khoản/i });
      await userEvent.click(submitBtn);

      // Verify maintenance banner appears
      expect(await screen.findByTestId('collision-notice-banner')).toBeInTheDocument();
      expect(screen.getAllByText(/ACC-42/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByRole('link', { name: /Đi đến trang quản trị tài khoản ACC-42/i })).toBeInTheDocument();
    });

    it('rejects with neutral error when collision outcome is BLOCKED_ROLE (ADMIN or CARRIER)', async () => {
      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);
      vi.spyOn(accountManagementApiClient, 'resolveEmailCollision').mockResolvedValue({
        normalizedEmail: 'admin@mland.vn',
        outcome: 'BLOCKED_ROLE',
      });

      renderWithProviders(<InternalAccountCreatePage />);

      await waitFor(() => {
        expect(screen.getByText('Chi nhánh Quận 1')).toBeInTheDocument();
      });

      await userEvent.type(screen.getByLabelText(/Email định danh/i), 'admin@mland.vn');
      await userEvent.type(screen.getByLabelText(/Họ và tên/i), 'Admin Collision');

      await userEvent.click(screen.getByRole('button', { name: /Khởi tạo tài khoản/i }));

      expect(await screen.findByText(/liên kết với vai trò ADMIN hoặc CARRIER/i)).toBeInTheDocument();
    });

    it('opens MEMBER review modal with DEMO badge, allows JSON export, and blocks retirement with 409 conflict while DEMO/UNAVAILABLE', async () => {
      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);
      vi.spyOn(accountManagementApiClient, 'resolveEmailCollision').mockResolvedValue({
        normalizedEmail: 'customer@gmail.com',
        outcome: 'MEMBER_REVIEW',
        accountId: 99,
      });

      vi.spyOn(accountManagementApiClient, 'reviewMemberReplacement').mockResolvedValue({
        accountId: 99,
        loginEmail: 'customer@gmail.com',
        fullName: 'Trần Khách Hàng',
        phone: '0909999999',
        status: 'ACTIVE',
        dataOrigin: 'DEMO',
        obligationStatus: 'UNAVAILABLE',
        reviewVersion: 1,
        sourceVersion: null,
        generatedAt: '2026-11-25T10:00:00Z',
        addresses: [
          { id: 101, receiverName: 'Trần Khách Hàng', phone: '0909999999', detailAddress: '123 Đường Hoa Lan, P2, Phú Nhuận', isDefault: true },
        ],
        serverStoredRetailCart: [
          { productReference: 'RING-01', variantReference: 'VAR-18K', displayName: 'Nhẫn Vàng 18K Đính Đá', quantity: 1, updatedAt: '2026-11-24T18:00:00Z' },
        ],
        retailPurchaseHistory: [
          { orderId: 'ORD-101', displayCode: 'ML-ORD-101', acceptedAt: '2026-11-20T10:00:00Z', paymentState: 'PAID', fulfilmentState: 'DELIVERED', itemSummary: '1 sản phẩm' },
        ],
        workshopBookingHistory: [
          { bookingId: 'BKG-202', displayCode: 'ML-WS-202', workshopDate: '2026-11-28', sessionTime: '09:00 - 11:30', locationName: 'Chi nhánh Quận 1', status: 'CONFIRMED', seatCount: 2 },
        ],
        obligationBlockers: [],
      });

      renderWithProviders(<InternalAccountCreatePage />);

      await waitFor(() => {
        expect(screen.getByText('Chi nhánh Quận 1')).toBeInTheDocument();
      });

      await userEvent.type(screen.getByLabelText(/Email định danh/i), 'customer@gmail.com');
      await userEvent.type(screen.getByLabelText(/Họ và tên/i), 'Trần Khách Hàng');

      await userEvent.click(screen.getByRole('button', { name: /Khởi tạo tài khoản/i }));

      // Review modal opens
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/Đối soát tài khoản MEMBER xung đột email/i)).toBeInTheDocument();

      // DEMO preview badge is prominently displayed
      expect(screen.getByTestId('demo-preview-badge')).toBeInTheDocument();
      expect(screen.getByText(/\[DEMO \/ KHÔNG KHẢ DỤNG\] Bản ghi xem thử/i)).toBeInTheDocument();

      // Displays review details
      expect(screen.getByText('ACC-99')).toBeInTheDocument();
      expect(screen.getByText(/123 Đường Hoa Lan/i)).toBeInTheDocument();
      expect(screen.getByText('Nhẫn Vàng 18K Đính Đá')).toBeInTheDocument();
      expect(screen.getByText('ML-ORD-101')).toBeInTheDocument();
      expect(screen.getByText('ML-WS-202')).toBeInTheDocument();

      // Export JSON button exists
      expect(screen.getByTestId('export-json-btn')).toBeInTheDocument();

      // Attempt retirement -> denied with 409 error message
      const retireBtn = screen.getByTestId('retire-member-btn');
      await userEvent.click(retireBtn);

      expect(
        await screen.findByText(/Nguồn dữ liệu là DEMO\/UNAVAILABLE hoặc chưa có hàng rào chấp thuận/i)
      ).toBeInTheDocument();
    });
  });

  describe('AdminAccountDetailPage — Invitation Status, Countdown & Manual Copy (FR-051, FR-052)', () => {
    it('renders server-time countdown and delivery state, and opens manual copy modal when FAILED', async () => {
      vi.spyOn(accountManagementApiClient, 'getAccountDetail').mockResolvedValue({
        accountId: 77,
        role: 'STAFF',
        status: 'PENDING_SETUP',
        loginEmail: 'pending.staff@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-25T01:00:00Z',
        updatedAt: '2026-11-25T01:00:00Z',
        profile: { fullName: 'Nhân Viên Mới', phone: '0901112233' },
        locationAssignments: [{ locationId: 1, assignedAt: '2026-11-25T01:00:00Z', active: true }],
        version: 1,
      });

      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);

      vi.spyOn(accountManagementApiClient, 'getCurrentInvitationStatus').mockResolvedValue({
        invitationId: 501,
        version: 1,
        createdAt: '2026-11-25T01:00:00Z',
        expiresAt: new Date(Date.now() + 36 * 3600 * 1000).toISOString(), // 36 hours remaining
        serverNow: new Date().toISOString(),
        deliveryState: 'FAILED',
        attempts: 2,
        manualContentAvailable: true,
        requestId: 808,
      });

      vi.spyOn(accountManagementApiClient, 'getManualInvitation').mockResolvedValue({
        requestId: 808,
        invitationVersion: 1,
        subject: '[Mland] Mời kích hoạt tài khoản nhân sự',
        html: '<p>Liên kết thiết lập: https://mland.vn/invitations/setup?secret=sec_abc123</p>',
        plainText: 'Liên kết thiết lập: https://mland.vn/invitations/setup?secret=sec_abc123',
        expiresAt: new Date(Date.now() + 36 * 3600 * 1000).toISOString(),
        serverNow: new Date().toISOString(),
      });

      renderWithProviders(
        <Routes>
          <Route path="/admin/accounts/:accountId" element={<AdminAccountDetailPage />} />
        </Routes>,
        '/admin/accounts/77'
      );

      // Wait for account detail
      expect(await screen.findByText('Nhân Viên Mới · ACC-77')).toBeInTheDocument();

      // Invitation section renders
      expect(screen.getByTestId('invitation-status-card')).toBeInTheDocument();
      expect(await screen.findByText('FAILED')).toBeInTheDocument();
      expect(screen.getByText('2 / 2')).toBeInTheDocument();
      expect(screen.getByTestId('invitation-countdown')).toBeInTheDocument();

      // Manual copy button appears because state is FAILED
      const manualCopyBtn = screen.getByTestId('manual-invitation-btn');
      expect(manualCopyBtn).toBeInTheDocument();

      // Click manual copy
      await userEvent.click(manualCopyBtn);

      // Modal opens
      expect(await screen.findByText(/Sao chép nội dung thư mời thủ công \(ADMIN\)/i)).toBeInTheDocument();
      expect(screen.getByDisplayValue(/\[Mland\] Mời kích hoạt tài khoản nhân sự/i)).toBeInTheDocument();
      expect(screen.getAllByDisplayValue(/https:\/\/mland\.vn\/invitations\/setup\?secret=sec_abc123/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByTestId('copy-all-email-btn')).toBeInTheDocument();
    });

    it('shows resend confirmation dialog warning about invalidating previous link', async () => {
      vi.spyOn(accountManagementApiClient, 'getAccountDetail').mockResolvedValue({
        accountId: 77,
        role: 'STAFF',
        status: 'PENDING_SETUP',
        loginEmail: 'pending.staff@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-25T01:00:00Z',
        updatedAt: '2026-11-25T01:00:00Z',
        profile: { fullName: 'Nhân Viên Mới', phone: '0901112233' },
        locationAssignments: [{ locationId: 1, assignedAt: '2026-11-25T01:00:00Z', active: true }],
        version: 1,
      });

      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);

      vi.spyOn(accountManagementApiClient, 'getCurrentInvitationStatus').mockResolvedValue({
        invitationId: 501,
        version: 1,
        createdAt: '2026-11-25T01:00:00Z',
        expiresAt: new Date(Date.now() + 36 * 3600 * 1000).toISOString(),
        serverNow: new Date().toISOString(),
        deliveryState: 'SENT',
        attempts: 1,
        manualContentAvailable: false,
      });

      vi.spyOn(accountManagementApiClient, 'resendInvitation').mockResolvedValue({
        accountId: 77,
        role: 'STAFF',
        status: 'PENDING_SETUP',
        loginEmail: 'pending.staff@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-25T01:00:00Z',
        updatedAt: '2026-11-25T01:30:00Z',
        provisioningStatus: 'PROVISIONED',
        invitationLastSentAt: '2026-11-25T01:30:00Z',
      });

      renderWithProviders(
        <Routes>
          <Route path="/admin/accounts/:accountId" element={<AdminAccountDetailPage />} />
        </Routes>,
        '/admin/accounts/77'
      );

      expect(await screen.findByText('Nhân Viên Mới · ACC-77')).toBeInTheDocument();

      const resendBtn = screen.getByTestId('resend-invitation-btn');
      await userEvent.click(resendBtn);

      // Warning dialog appears
      expect(await screen.findByTestId('resend-confirm-dialog')).toBeInTheDocument();
      expect(screen.getByText(/Gửi lại thư mời sẽ vô hiệu hóa ngay lập tức liên kết trước đó/i)).toBeInTheDocument();

      // Confirm resend
      const confirmBtn = screen.getByTestId('confirm-resend-action-btn');
      await userEvent.click(confirmBtn);

      expect(accountManagementApiClient.resendInvitation).toHaveBeenCalledWith(77);
    });
  });

  describe('InternalAccountSetupPage — Public Link Exchange & Completion (FR-051)', () => {
    it('shows error guidance when secret is missing or exchange fails with 400/expired', async () => {
      vi.spyOn(accountManagementApiClient, 'exchangeInternalInvitation').mockRejectedValue({
        status: 400,
        message: 'Mã thư mời đã hết hạn sau 48 giờ.',
      });

      renderWithProviders(
        <Routes>
          <Route path="/invitations/setup" element={<InternalAccountSetupPage />} />
        </Routes>,
        '/invitations/setup?secret=expired_secret'
      );

      expect(
        await screen.findByText(/Liên kết kích hoạt không hợp lệ, đã hết hạn sau 48 giờ hoặc đã được sử dụng/i)
      ).toBeInTheDocument();
    });

    it('exchanges secret successfully and allows setting password to activate account', async () => {
      vi.spyOn(accountManagementApiClient, 'exchangeInternalInvitation').mockResolvedValue({
        customToken: 'mock_custom_token',
        setupSession: 'session_xyz_789',
        accountId: 88,
        intendedEmail: 'new.staff@mland.vn',
        role: 'STAFF',
      });

      vi.spyOn(firebaseAuthClient, 'signInWithCustomToken').mockResolvedValue(undefined as any);
      vi.spyOn(firebaseAuthClient, 'updatePassword').mockResolvedValue(undefined as any);
      vi.spyOn(accountManagementApiClient, 'completeInternalInvitation').mockResolvedValue({
        accountId: 88,
        role: 'STAFF',
        roleHomeRoute: '/staff/home',
        status: 'ACTIVE',
      });

      renderWithProviders(
        <Routes>
          <Route path="/invitations/setup" element={<InternalAccountSetupPage />} />
          <Route path="/staff/home" element={<div>Trang chủ nhân viên Staff</div>} />
        </Routes>,
        '/invitations/setup?secret=valid_secret_123'
      );

      // Verify intended email and role displayed
      expect(await screen.findByText('new.staff@mland.vn')).toBeInTheDocument();
      expect(screen.getByText('STAFF')).toBeInTheDocument();

      // Enter password
      const newPwdInput = screen.getByLabelText(/Mật khẩu mới/i);
      const confirmPwdInput = screen.getByLabelText(/Xác nhận mật khẩu/i);

      await userEvent.type(newPwdInput, 'StaffSecret@123');
      await userEvent.type(confirmPwdInput, 'StaffSecret@123');

      // Submit password setup
      const submitBtn = screen.getByTestId('submit-password-setup-btn');
      await userEvent.click(submitBtn);

      // Verifies completion call and navigation to role home
      await waitFor(() => {
        expect(accountManagementApiClient.completeInternalInvitation).toHaveBeenCalledWith({
          setupSession: 'session_xyz_789',
          setupChoice: 'PASSWORD',
        });
        expect(screen.getByText('Trang chủ nhân viên Staff')).toBeInTheDocument();
      });
    });
  });

  describe('T113: Removal of Role Catalogue from Navigation', () => {
    it('does not render /admin/roles in Admin navigation tabs', async () => {
      vi.spyOn(accountManagementApiClient, 'listWorkshopLocations').mockResolvedValue([
        { id: 1, code: 'Q1', name: 'Chi nhánh Quận 1', active: true },
      ]);

      renderWithProviders(<InternalAccountCreatePage />);

      await waitFor(() => {
        expect(screen.getByText('Chi nhánh Quận 1')).toBeInTheDocument();
      });

      // Role catalogue link must not exist in admin navigation
      expect(screen.queryByRole('link', { name: /Vai trò/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /Role catalogue/i })).not.toBeInTheDocument();
    });
  });
});
