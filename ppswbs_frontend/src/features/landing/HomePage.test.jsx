import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import HomePage from './HomePage';
import { AuthProvider } from '../../shared/auth/AuthProvider';
import * as workshopApi from '../../api/workshops';
import * as catalogueApi from '../../api/catalogue';
import { firebaseAuthClient } from '../accountmanagement/auth/FirebaseAuthClient';

function renderHomePage({
  initialAuthState = { status: 'signed-out' },
} = {}) {
  const mockAuthSource = {
    watch: (listener) => {
      listener(initialAuthState);
      return () => {};
    },
    getIdToken: vi.fn().mockResolvedValue('mock-token'),
    signOut: vi.fn().mockResolvedValue(undefined),
  };

  return render(
    <AuthProvider source={mockAuthSource}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('Landing HomePage (Figma Page 01 · Public & Guest Node 55:5)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders topbar brand and public navigation links matching Figma Page 01', () => {
    renderHomePage();
    const nav = screen.getByRole('navigation', { name: 'Khám phá' });
    expect(screen.getByRole('link', { name: 'MLAND' })).toHaveAttribute('href', '/');
    expect(within(nav).getByRole('link', { name: 'Workshops' })).toHaveAttribute('href', '/workshops');
    expect(within(nav).getByRole('link', { name: 'Trang sức' })).toHaveAttribute('href', '/products');
    expect(within(nav).getByRole('link', { name: 'Đặt thiết kế' })).toHaveAttribute(
      'href',
      '/workshops/PKG-COUPLE-SILVER/design',
    );
    expect(within(nav).getByRole('link', { name: 'Đánh giá' })).toHaveAttribute('href', '/reviews');

    // Utility links
    expect(screen.getByRole('link', { name: 'Tra cứu' })).toHaveAttribute(
      'href',
      '/workshop-booking/lookup',
    );
    expect(screen.getAllByRole('link', { name: /Trợ lý AI/i })[0]).toHaveAttribute(
      'href',
      '/ai-support',
    );
  });

  it('renders Guest topbar CTA when unauthenticated', () => {
    renderHomePage({ initialAuthState: { status: 'signed-out' } });
    const loginLink = screen.getByRole('link', { name: 'Đăng nhập' });
    expect(loginLink).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('href', '/login');
    expect(screen.queryByText('ACTIVE · MEMBER')).not.toBeInTheDocument();
  });

  it('renders Member chip and, with an upcoming ticket, its real time and a link to that booking', async () => {
    const start = new Date(Date.now() + 3 * 24 * 3600 * 1000).toISOString();
    vi.spyOn(workshopApi, 'listMemberBookingGroups').mockResolvedValue([
      {
        groupCode: 'WG-NEXT-1',
        groupStatus: 'CONFIRMED',
        tickets: [
          {
            ticketCode: 'WT-1',
            ticketStatus: 'CONFIRMED',
            sessions: [{ sessionReference: 's1', scheduledStart: start, scheduledEnd: start }],
          },
        ],
      },
    ]);
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const memberChip = screen.getByRole('button', { name: /Tùy chọn tài khoản/i });
    expect(memberChip).toBeInTheDocument();
    expect(screen.getByText('ACTIVE · MEMBER')).toBeInTheDocument();
    expect(await screen.findByText(/Buổi workshop sắp tới:/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Xem vé & QR/i })).toHaveAttribute(
      'href',
      '/workshop-booking/lookup?code=WG-NEXT-1',
    );
    expect(screen.getByRole('link', { name: /Đơn hàng \(\d+\)/ })).toHaveAttribute(
      'href',
      '/member/orders',
    );
  });

  it('says there is no upcoming workshop and offers no QR link when the Member has no confirmed ticket', async () => {
    vi.spyOn(workshopApi, 'listMemberBookingGroups').mockResolvedValue([]);
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    expect(await screen.findByText(/Bạn chưa có buổi workshop nào sắp tới/i)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Xem vé & QR/i })).toBeNull();
  });

  it('opens dropdown menu with account options when user chip is clicked', () => {
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const memberChip = screen.getByRole('button', { name: /Tùy chọn tài khoản/i });
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();

    fireEvent.click(memberChip);
    const menu = screen.getByRole('menu');
    expect(menu).toBeInTheDocument();

    expect(within(menu).getByRole('menuitem', { name: /Hồ sơ cá nhân/i })).toHaveAttribute('href', '/profile');
    expect(within(menu).getByRole('menuitem', { name: /Đơn hàng của tôi/i })).toHaveAttribute('href', '/member/orders');
    expect(within(menu).getByRole('menuitem', { name: /Điểm thưởng tích lũy/i })).toHaveAttribute('href', '/me/loyalty');
    expect(within(menu).getByRole('menuitem', { name: /Tra cứu vé & Booking/i })).toHaveAttribute(
      'href',
      '/workshop-booking/lookup',
    );
    expect(within(menu).getByRole('menuitem', { name: /Đăng xuất/i })).toBeInTheDocument();
  });

  it('displays real dynamic account name and email username without hardcoding', () => {
    const mockUser = {
      displayName: 'Đặng Ngọc Sơn',
      email: 'ngocson@mland.vn',
    };
    vi.spyOn(firebaseAuthClient, 'getCurrentUser').mockReturnValue(mockUser);

    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    expect(screen.getByRole('button', { name: /Tùy chọn tài khoản: Đặng Ngọc Sơn/i })).toBeInTheDocument();
    expect(screen.getByText('Đặng Ngọc Sơn...')).toBeInTheDocument();
    expect(screen.getByText(/Xin chào, Đặng Ngọc Sơn!/i)).toBeInTheDocument();
  });

  it('renders Section 1 Hero banner with headline, CTA, and brand logo box', () => {
    renderHomePage();
    expect(
      screen.getByText(/Tự tay chế tác nhẫn bạc & Trang sức thiết kế cá nhân hoá/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Khám phá Workshop →/i })).toHaveAttribute(
      'href',
      '/workshops',
    );
    expect(screen.getByText('img-holder · Brand Logo')).toBeInTheDocument();
  });

  it('renders Section 2 Workshop packages with 3 Figma baseline cards', () => {
    renderHomePage();
    expect(screen.getByText('GÓI WORKSHOP NỔI BẬT')).toBeInTheDocument();
    expect(screen.getByText('Trải nghiệm tự làm trang sức')).toBeInTheDocument();
    expect(
      screen.getByText(/Workshop Làm Nhẫn Bạc Cơ Bản · 2 Giờ/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Workshop Nhẫn Cặp Đính Đá Phong Thuỷ · 3.5 Giờ/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Khoá Masterclass Nghệ Nhân & Chế Tác Riêng/i),
    ).toBeInTheDocument();
  });

  it('fetches and dynamically displays live workshop packages from backend API', async () => {
    const livePackagesMock = [
      {
        packageId: 'pkg-live-1',
        name: 'Workshop Bạc Tinh Xảo 2026',
        description: 'Chế tác tự do cùng nghệ nhân kim hoàn cao cấp',
        publicPrice: 550000,
        requiredSessionCount: 2,
        minParticipants: 1,
        maxParticipants: 4,
      },
      {
        packageId: 'pkg-live-2',
        name: 'Workshop Silver Clay Nung Lò',
        description: 'Bạc 999 nặn hình và đính đá CZ',
        publicPrice: 750000,
        requiredSessionCount: 1,
        minParticipants: 2,
        maxParticipants: 6,
      },
    ];

    vi.spyOn(workshopApi, 'listWorkshopPackages').mockResolvedValue(livePackagesMock);

    renderHomePage();

    await waitFor(() => {
      expect(screen.getByText('Workshop Bạc Tinh Xảo 2026')).toBeInTheDocument();
    });

    expect(screen.getByText('Workshop Silver Clay Nung Lò')).toBeInTheDocument();
  });

  it('renders Section 3 About Showcase with title, story, and pagination dots', () => {
    renderHomePage();
    expect(screen.getByText('Về M-Land')).toBeInTheDocument();
    expect(
      screen.getByText(/Không gian trải nghiệm kim hoàn thủ công giữa lòng phố/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('tablist', { name: 'Chọn mẫu sản phẩm trưng bày' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Trưng bày mẫu 1' })).toBeInTheDocument();
  });

  it('renders Section 4 Reviews with 3 cards matching Figma text', () => {
    renderHomePage();
    expect(screen.getByText('Khách hàng nói gì về M-Land')).toBeInTheDocument();
    expect(screen.getByText('M*** A*** (Đà Nẵng)')).toBeInTheDocument();
    expect(screen.getByText('T*** H*** (TP.HCM)')).toBeInTheDocument();
    expect(screen.getByText('D*** L*** (Du khách Anh)')).toBeInTheDocument();
    expect(screen.getAllByText('★★★★★ 5.0 / 5.0')).toHaveLength(3);
  });

  it('renders Section 5 Footer with brand, services, policies, and branches', () => {
    renderHomePage();
    expect(screen.getByText('MLAND JEWELRY & WORKSHOP')).toBeInTheDocument();
    expect(screen.getByText('DỊCH VỤ & WORKSHOP')).toBeInTheDocument();
    expect(screen.getByText('HỖ TRỢ & CHÍNH SÁCH')).toBeInTheDocument();
    expect(screen.getByText('HỆ THỐNG CHI NHÁNH')).toBeInTheDocument();
    expect(screen.getByText(/45 Bạch Đằng, Q. Hải Châu/i)).toBeInTheDocument();
    expect(screen.getByText(/123 Nguyễn Huệ, Quận 1/i)).toBeInTheDocument();
    expect(screen.getByText(/© 2026 M-Land Operations Hub \(SEP490_G22\)\. All rights reserved\./i)).toBeInTheDocument();
  });

  it('renders internal operation portals bar for staff and manager workflows', () => {
    renderHomePage();
    expect(screen.getByText(/Cổng nội bộ:/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Staff Vận hành/i })).toHaveAttribute(
      'href',
      '/operations/sessions',
    );
    expect(screen.getByRole('link', { name: /Quản trị Sản phẩm/i })).toHaveAttribute(
      'href',
      '/catalogue-management/products',
    );
  });

  it('renders mobile drawer and FAB AI button for mobile viewport access', () => {
    renderHomePage();
    expect(screen.getByRole('button', { name: 'Mở thực đơn điều hướng' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Trợ lý AI tư vấn thiết kế' })).toHaveAttribute(
      'href',
      '/ai-support',
    );
  });
});
