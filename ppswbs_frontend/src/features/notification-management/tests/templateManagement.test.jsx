import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import TemplateListPage from '../ui/TemplateListPage';
import TemplateDetailView from '../ui/TemplateDetailView';
import TemplateVersionEditorModal from '../ui/TemplateVersionEditorModal';
import TemplatePreviewModal from '../ui/TemplatePreviewModal';

const mockApi = {
  listTemplates: vi.fn(),
  getTemplate: vi.fn(),
  createDraftVersion: vi.fn(),
  updateDraftVersion: vi.fn(),
  activateVersion: vi.fn(),
  previewTemplate: vi.fn(),
};

vi.mock('../application/useNotificationApi', () => ({
  useNotificationApi: () => mockApi,
}));

vi.mock('../../../shared/ui/OperationsSidebar', () => ({
  default: () => <aside data-testid="sidebar">Sidebar</aside>,
}));

const sampleTemplates = [
  {
    code: 'BOOKING_CONFIRMED',
    name: 'Xác nhận đặt lịch Workshop',
    description: 'Gộp booking, tiền cọc và vé QR',
    owningModule: 'workshopbooking',
    lifecycleStatus: 'ACTIVE',
    allowedPlaceholders: ['customerName', 'bookingCode', 'startTime'],
    activeVersion: { versionNumber: 2, subjectTemplate: 'Xác nhận #{{bookingCode}}' },
    latestDraftVersionNumber: 3,
  },
  {
    code: 'AUTH_EMAIL_VERIFICATION',
    name: 'Xác minh Email',
    description: 'Xác minh tài khoản qua Firebase Auth',
    owningModule: 'firebase_auth',
    lifecycleStatus: 'EXTERNAL',
    allowedPlaceholders: ['firebaseVerificationUrl'],
    activeVersion: null,
    latestDraftVersionNumber: null,
  },
];

const sampleDetail = {
  code: 'BOOKING_CONFIRMED',
  name: 'Xác nhận đặt lịch Workshop',
  description: 'Gộp booking, tiền cọc và vé QR',
  owningModule: 'workshopbooking',
  lifecycleStatus: 'ACTIVE',
  allowedPlaceholders: ['customerName', 'bookingCode', 'startTime'],
  versions: [
    {
      versionNumber: 3,
      status: 'DRAFT',
      subjectTemplate: '[Mland] Bản nháp mới #{{bookingCode}}',
      htmlTemplate: '<p>Chào {{customerName}}</p>',
      plainTextTemplate: 'Chào {{customerName}}',
      createdAt: '2026-11-06T15:00:00Z',
      createdBy: 'acc_admin_01',
      activatedAt: null,
      activatedBy: null,
    },
    {
      versionNumber: 2,
      status: 'ACTIVE',
      subjectTemplate: '[Mland] Xác nhận đặt lịch #{{bookingCode}}',
      htmlTemplate: '<p>Chào {{customerName}}</p>',
      plainTextTemplate: 'Chào {{customerName}}',
      createdAt: '2026-11-05T09:00:00Z',
      createdBy: 'acc_mgr_01',
      activatedAt: '2026-11-05T10:00:00Z',
      activatedBy: 'acc_mgr_01',
    },
  ],
};

describe('Template Management UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders TemplateListPage with template items and badges', async () => {
    mockApi.listTemplates.mockResolvedValue(sampleTemplates);

    render(
      <MemoryRouter initialEntries={['/admin/notifications/templates']}>
        <TemplateListPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Đang tải danh sách mẫu thông báo…')).toBeInTheDocument();

    expect(await screen.findByText('BOOKING_CONFIRMED')).toBeInTheDocument();
    expect(screen.getByText('AUTH_EMAIL_VERIFICATION')).toBeInTheDocument();
    expect(screen.getByText('Đang kích hoạt')).toBeInTheDocument();
    expect(screen.getByText('Bên ngoài (Firebase)')).toBeInTheDocument();
    expect(screen.getByText('v2')).toBeInTheDocument();
    expect(screen.getByText('v3')).toBeInTheDocument();
  });

  it('handles fetch error and reload in TemplateListPage', async () => {
    mockApi.listTemplates
      .mockRejectedValueOnce(new Error('Network connection failed'))
      .mockResolvedValueOnce(sampleTemplates);

    render(
      <MemoryRouter initialEntries={['/admin/notifications/templates']}>
        <TemplateListPage />
      </MemoryRouter>
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Network connection failed');

    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('BOOKING_CONFIRMED')).toBeInTheDocument();
  });

  it('renders TemplateDetailView and handles version activation', async () => {
    mockApi.getTemplate.mockResolvedValue(sampleDetail);
    mockApi.activateVersion.mockResolvedValue({
      activatedVersionNumber: 3,
      deactivatedVersionNumber: 2,
    });

    render(
      <MemoryRouter initialEntries={['/admin/notifications/templates/BOOKING_CONFIRMED']}>
        <Routes>
          <Route
            path="/admin/notifications/templates/:code"
            element={<TemplateDetailView />}
          />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Xác nhận đặt lịch Workshop')).toBeInTheDocument();
    expect(screen.getByText('{{customerName}}')).toBeInTheDocument();
    expect(screen.getByText('{{bookingCode}}')).toBeInTheDocument();

    // Verify version rows
    expect(screen.getByText('v3')).toBeInTheDocument();
    expect(screen.getByText('Bản nháp')).toBeInTheDocument();
    expect(screen.getByText('v2')).toBeInTheDocument();
    expect(screen.getByText('Đang áp dụng')).toBeInTheDocument();

    // Click "Kích hoạt" button on draft version v3
    const activateButtons = screen.getAllByRole('button', { name: 'Kích hoạt' });
    fireEvent.click(activateButtons[0]);

    // Check confirmation modal appears
    expect(screen.getByText('Xác nhận kích hoạt phiên bản v3')).toBeInTheDocument();

    // Confirm activation
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận kích hoạt' }));

    await waitFor(() => {
      expect(mockApi.activateVersion).toHaveBeenCalledWith('BOOKING_CONFIRMED', 3);
    });
  });

  it('validates placeholders inside TemplateVersionEditorModal', () => {
    const onSave = vi.fn();
    const onClose = vi.fn();

    const { rerender } = render(
      <TemplateVersionEditorModal
        isOpen={true}
        onClose={onClose}
        template={sampleDetail}
        initialVersion={null}
        onSave={onSave}
      />
    );

    expect(screen.getByText('Tạo bản nháp mới (Xác nhận đặt lịch Workshop)')).toBeInTheDocument();

    // Enter subject with an invalid placeholder
    const subjectInput = screen.getByLabelText(/Tiêu đề email/i);
    fireEvent.change(subjectInput, {
      target: { value: 'Xác nhận đơn {{invalidPlaceholder}}' },
    });

    expect(
      screen.getByText(/Biến không hợp lệ sẽ bị máy chủ từ chối/i)
    ).toBeInTheDocument();

    // Save button should be disabled
    const saveBtn = screen.getByRole('button', { name: 'Lưu bản nháp' });
    expect(saveBtn).toBeDisabled();

    // Fix placeholder to valid one
    fireEvent.change(subjectInput, {
      target: { value: 'Xác nhận đơn {{bookingCode}}' },
    });

    const htmlInput = screen.getByLabelText(/Nội dung HTML/i);
    fireEvent.change(htmlInput, {
      target: { value: '<p>Xin chào {{customerName}}</p>' },
    });

    expect(saveBtn).not.toBeDisabled();
    fireEvent.click(saveBtn);

    expect(onSave).toHaveBeenCalledWith({
      subjectTemplate: 'Xác nhận đơn {{bookingCode}}',
      htmlTemplate: '<p>Xin chào {{customerName}}</p>',
      plainTextTemplate: '',
    });
  });

  it('renders safe preview in TemplatePreviewModal', async () => {
    const onPreview = vi.fn().mockResolvedValue({
      renderedSubject: '[Mland] Xác nhận đặt lịch #BK-SAMPLE-2026',
      renderedHtml: '<p>Chào Nguyễn Văn A</p>',
      renderedPlainText: 'Chào Nguyễn Văn A',
    });
    const onClose = vi.fn();

    render(
      <TemplatePreviewModal
        isOpen={true}
        onClose={onClose}
        template={sampleDetail}
        versionNumber={2}
        onPreview={onPreview}
      />
    );

    expect(screen.getByText(/Dữ liệu mẫu an toàn/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(onPreview).toHaveBeenCalled();
    });

    expect(
      screen.getByText('[Mland] Xác nhận đặt lịch #BK-SAMPLE-2026')
    ).toBeInTheDocument();

    // Tab switching
    fireEvent.click(screen.getByRole('button', { name: 'Nội dung Plain Text' }));
    expect(screen.getByText('Chào Nguyễn Văn A')).toBeInTheDocument();
  });
});

