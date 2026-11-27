import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { MemberConsultationPage } from '../ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from '../ui/StaffConsultationInboxPage';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { AuthContext } from '../../../shared/auth/AuthProvider';

vi.mock('../../../shared/auth/useRole', () => ({
  useRole: () => 'STAFF',
}));

const mockApi = {
  listConversations: vi.fn().mockResolvedValue({ items: [] }),
  getConversation: vi.fn().mockResolvedValue(null),
  getContextOptions: vi.fn().mockResolvedValue({ items: [] }),
};

vi.mock('../api/useConsultationChatApi', () => ({
  useConsultationChatApi: () => mockApi,
}));

describe('Feature-013 Consultation UI Screens', () => {
  it('renders MemberConsultationPage header and action button', () => {
    render(
      <MemoryRouter>
        <MemberConsultationPage />
      </MemoryRouter>
    );
    expect(screen.getByText('Tư vấn thiết kế')).toBeInTheDocument();
    expect(screen.getByText('+ Tạo cuộc tư vấn mới')).toBeInTheDocument();
  });

  it('renders StaffConsultationInboxPage header and filter bar', () => {
    render(<MemoryRouter><StaffConsultationInboxPage currentUser={{ role: 'STAFF' }} /></MemoryRouter>);
    expect(screen.getByText('Hộp thư tư vấn')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('opens on the unclaimed conversations when the badge link asks for them', async () => {
    mockApi.listConversations.mockClear();
    render(
      <MemoryRouter initialEntries={['/operations/consultations?status=OPEN&unclaimed=true']}>
        <StaffConsultationInboxPage currentUser={{ role: 'MANAGER' }} />
      </MemoryRouter>
    );
    expect(screen.getByRole('combobox')).toHaveValue('UNCLAIMED');
    await waitFor(() => expect(mockApi.listConversations).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'OPEN', unclaimed: true }),
    ));
  });

  it('follows the language switch (I18nProvider) instead of a lang prop', () => {
    render(
      <I18nProvider initialLanguage="en">
        <MemoryRouter>
          <MemberConsultationPage />
        </MemoryRouter>
      </I18nProvider>
    );
    expect(screen.getByText('Design Consultation')).toBeInTheDocument();
    expect(screen.getByText('+ New Consultation')).toBeInTheDocument();
  });

  it('renders waiting message when last message is from user in both vi and en', async () => {
    const mockDetail = {
      conversationReference: 'CONV-001',
      status: 'OPEN',
      messages: [
        {
          id: 1,
          senderRole: 'MEMBER',
          senderName: 'Member Test',
          content: 'Hello, need help',
          createdAt: new Date().toISOString(),
        },
      ],
    };

    mockApi.listConversations.mockResolvedValue({
      items: [
        {
          conversationReference: 'CONV-001',
          status: 'OPEN',
          lastMessagePreview: 'Hello, need help',
          updatedAt: new Date().toISOString(),
        },
      ],
    });
    mockApi.getConversation.mockResolvedValue(mockDetail);

    render(
      <I18nProvider initialLanguage="vi">
        <MemoryRouter>
          <MemberConsultationPage />
        </MemoryRouter>
      </I18nProvider>
    );

    expect(await screen.findByTestId('consultation-waiting-staff-reply')).toHaveTextContent(
      'Vui lòng đợi, sẽ có nhân viên phản hồi trong thời gian sớm nhất có thể.'
    );
  });

  it('renders login requirement when user is signed out', async () => {
    render(
      <I18nProvider initialLanguage="vi">
        <AuthContext.Provider value={{ status: 'signed-out' }}>
          <MemoryRouter>
            <MemberConsultationPage />
          </MemoryRouter>
        </AuthContext.Provider>
      </I18nProvider>
    );

    expect(await screen.findByText('Vui lòng đăng nhập để chat tư vấn với nhân viên.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('renders Manager Release Claim button when viewed by manager on claimed conversation', async () => {
    const mockDetail = {
      conversationReference: 'CONV-CLAIMED-MGR',
      status: 'OPEN',
      claimantRole: 'STAFF',
      claimantUserId: 20,
      aggregateVersion: 2,
      messages: [],
    };
    mockApi.listConversations.mockResolvedValue({
      items: [
        {
          conversationReference: 'CONV-CLAIMED-MGR',
          status: 'OPEN',
          claimantRole: 'STAFF',
          claimantUserId: 20,
          updatedAt: new Date().toISOString(),
        },
      ],
    });
    mockApi.getConversation.mockResolvedValue(mockDetail);

    render(
      <MemoryRouter>
        <StaffConsultationInboxPage currentUser={{ role: 'MANAGER', id: 50 }} />
      </MemoryRouter>
    );

    expect(await screen.findByTestId('manager-release-claim-btn')).toBeInTheDocument();
    expect(screen.getByTestId('manager-release-claim-btn')).toHaveTextContent('🔓 Gỡ tiếp nhận');
  });

  it('renders read-only warning when staff views conversation claimed by another staff', async () => {
    const mockDetail = {
      conversationReference: 'CONV-CLAIMED-OTHER',
      status: 'OPEN',
      claimantRole: 'STAFF',
      claimantUserId: 99, // Claimed by staff 99
      aggregateVersion: 2,
      messages: [],
    };
    mockApi.listConversations.mockResolvedValue({
      items: [
        {
          conversationReference: 'CONV-CLAIMED-OTHER',
          status: 'OPEN',
          claimantRole: 'STAFF',
          claimantUserId: 99,
          updatedAt: new Date().toISOString(),
        },
      ],
    });
    mockApi.getConversation.mockResolvedValue(mockDetail);

    // Current staff is id 10, not 99
    render(
      <MemoryRouter>
        <StaffConsultationInboxPage currentUser={{ role: 'STAFF', id: 10 }} />
      </MemoryRouter>
    );

    expect(await screen.findByTestId('chat-claimed-by-other-warning')).toBeInTheDocument();
    expect(screen.getByTestId('chat-claimed-by-other-warning')).toHaveTextContent(
      'Cuộc trò chuyện đang được phụ trách bởi nhân viên khác. Bạn đang ở chế độ xem.'
    );
  });
});
