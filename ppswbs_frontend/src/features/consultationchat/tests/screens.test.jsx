import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { MemberConsultationPage } from '../ui/MemberConsultationPage';
import { StaffConsultationInboxPage } from '../ui/StaffConsultationInboxPage';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';

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
        <MemberConsultationPage />
      </MemoryRouter>
    );
    expect(screen.getByText('Tư vấn thiết kế')).toBeInTheDocument();
    expect(screen.getByText('+ Tạo cuộc tư vấn mới')).toBeInTheDocument();
  });

  it('renders StaffConsultationInboxPage header and filter bar', () => {
    render(<StaffConsultationInboxPage currentUser={{ role: 'STAFF' }} />);
    expect(screen.getByText('Hộp thư tư vấn')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
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

  it('renders the staff inbox in English', () => {
    render(
      <I18nProvider initialLanguage="en">
        <StaffConsultationInboxPage currentUser={{ role: 'STAFF' }} />
      </I18nProvider>
    );
    expect(screen.getByText('Consultation Inbox')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'All statuses' })).toBeInTheDocument();
  });
});
