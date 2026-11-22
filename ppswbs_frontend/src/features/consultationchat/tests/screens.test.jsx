import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { MemberConsultationPage } from '../ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from '../ui/StaffConsultationInboxPage';

vi.mock('../../../shared/auth/useRole', () => ({
  useRole: () => 'STAFF',
}));

// Mock useConsultationChatApi
vi.mock('../api/useConsultationChatApi', () => ({
  useConsultationChatApi: () => ({
    listConversations: vi.fn().mockResolvedValue({ items: [] }),
    getConversation: vi.fn().mockResolvedValue(null),
    getContextOptions: vi.fn().mockResolvedValue({ items: [] }),
  }),
}));

describe('Feature-013 Consultation UI Screens', () => {
  it('renders MemberConsultationPage header and action button', () => {
    render(
      <MemoryRouter>
        <MemberConsultationPage lang="vi" />
      </MemoryRouter>
    );
    expect(screen.getByText('Tư vấn thiết kế')).toBeInTheDocument();
    expect(screen.getByText('+ Tạo cuộc tư vấn mới')).toBeInTheDocument();
  });

  it('renders StaffConsultationInboxPage header and filter bar', () => {
    render(<StaffConsultationInboxPage lang="vi" currentUser={{ role: 'STAFF' }} />);
    expect(screen.getByText('Hộp thư tư vấn')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });
});
