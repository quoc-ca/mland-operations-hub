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
  httpHandler = vi.fn().mockRejectedValue(new Error('no mock')),
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

describe('Landing HomePage (Figma Page 01 · Public & Live Features Integration)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('does not render screen heading annotations or technical handoff banner in production UI', () => {
    renderHomePage();
    expect(screen.queryByText(/LAPTOP · 1024/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/MOBILE · 320/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/HANDOFF ·/i)).not.toBeInTheDocument();
  });

  it('renders topbar brand and 5 public navigation links', () => {
    renderHomePage();
    const nav = screen.getByRole('navigation', { name: 'Khám phá' });
    expect(screen.getByRole('link', { name: 'MLAND' })).toHaveAttribute('href', '/');
    expect(within(nav).getByRole('link', { name: 'Workshop' })).toHaveAttribute('href', '/workshops');
    expect(within(nav).getByRole('link', { name: 'Ready Rings' })).toHaveAttribute('href', '/products');
    expect(within(nav).getByRole('link', { name: 'Custom' })).toHaveAttribute('href', '/design-requests/new');
    expect(within(nav).getByRole('link', { name: 'Tra cứu' })).toHaveAttribute('href', '/workshop-booking/lookup');
    expect(within(nav).getByRole('link', { name: 'Trợ lý AI' })).toHaveAttribute('href', '/ai-support');
  });

  it('renders Guest topbar CTA when unauthenticated', () => {
    renderHomePage({ initialAuthState: { status: 'signed-out' } });
    const loginLink = screen.getByRole('link', { name: 'Đăng nhập' });
    expect(loginLink).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('href', '/login');
    expect(screen.queryByText('ACTIVE · MEMBER')).not.toBeInTheDocument();
    expect(screen.queryByText(/HANDOFF ·/i)).not.toBeInTheDocument();
  });

  it('renders Member chip with account name and quick-action bar when authenticated', () => {
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const memberChip = screen.getByRole('button', { name: /Tùy chọn tài khoản/i });
    expect(memberChip).toBeInTheDocument();
    expect(screen.getByText('ACTIVE · MEMBER')).toBeInTheDocument();
    expect(screen.getByText(/Vé Workshop gần nhất:/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Xem mã QR/i })).toHaveAttribute(
      'href',
      '/workshop-booking/lookup',
    );
    expect(screen.getByRole('link', { name: /Đơn hàng \(1\)/i })).toHaveAttribute(
      'href',
      '/member/orders',
    );
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
    expect(screen.getByText('Đặng Ngọc Sơn')).toBeInTheDocument();
    expect(screen.getByText(/Xin chào, Đặng Ngọc Sơn!/i)).toBeInTheDocument();
    expect(screen.queryByText(/Minh Anh/i)).not.toBeInTheDocument();
  });

  it('renders Hero section with dual conversion CTAs', () => {
    renderHomePage();
    expect(screen.getByText('TRẢI NGHIỆM CHẾ TÁC CỦA RIÊNG BẠN')).toBeInTheDocument();
    expect(screen.getByText(/MỘT CHIẾC NHẪN,/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ĐẶT WORKSHOP NGAY/i })).toHaveAttribute(
      'href',
      '/workshops',
    );
    expect(screen.getByRole('link', { name: /KHÁM PHÁ READY RINGS/i })).toHaveAttribute(
      'href',
      '/products',
    );
  });

  it('renders Brand Story section with 3 golden commitments', () => {
    renderHomePage();
    expect(screen.getByText('VỀ XƯỞNG KIM HOÀN MLAND')).toBeInTheDocument();
    expect(
      screen.getByText(/Không gian sáng tạo kim hoàn thủ công giữa lòng đô thị/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Bạc 925 tiêu chuẩn cao cấp kèm bảo hành đánh bóng trọn đời/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Nghệ nhân hướng dẫn 1-kèm-1/i)).toBeInTheDocument();
    expect(screen.getByText(/Đầy đủ thiết bị đúc nung hiện đại/i)).toBeInTheDocument();
  });

  it('renders 3 core workshop packages with correct prices and highlight tag (fallback)', () => {
    renderHomePage();
    expect(screen.getByRole('heading', { level: 4, name: 'Signature Workshop' })).toBeInTheDocument();
    expect(screen.getByText('490,000 đ / người')).toBeInTheDocument();

    expect(screen.getByRole('heading', { level: 4, name: 'Silver Clay Workshop' })).toBeInTheDocument();
    expect(screen.getByText('690,000 đ / người')).toBeInTheDocument();
    expect(screen.getByText('Được yêu thích nhất')).toBeInTheDocument();

    expect(screen.getByRole('heading', { level: 4, name: 'Wax Carving Workshop' })).toBeInTheDocument();
    expect(screen.getByText('890,000 đ / người')).toBeInTheDocument();
  });

  it('fetches and dynamically displays live workshop packages from Feature 003 API', async () => {
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
      expect(screen.getByRole('heading', { level: 4, name: 'Workshop Bạc Tinh Xảo 2026' })).toBeInTheDocument();
    });

    expect(screen.getByText(/Workshop Silver Clay Nung Lò/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chi tiết & Đặt chỗ' })).toHaveAttribute(
      'href',
      '/workshops/pkg-live-1',
    );
  });

  it('fetches and dynamically displays live ready ring products from Feature 002 Catalogue API', async () => {
    const liveProductsMock = {
      items: [
        {
          id: 'prod-live-101',
          name: 'Nhẫn Bạc Kim Cương Nhân Tạo',
          category: { name: 'Nhẫn Nữ Bạc 925' },
          priceRange: { minimum: 580000 },
          images: [{ url: '/media/ring1.png', altText: 'Nhẫn 1' }],
          variants: [{ sku: 'ML-LIVE-01' }],
        },
      ],
    };

    vi.spyOn(catalogueApi, 'listPublicProducts').mockResolvedValue(liveProductsMock);

    renderHomePage();

    await waitFor(() => {
      expect(screen.getByText('ML-LIVE-01')).toBeInTheDocument();
    });

    expect(screen.getByText('Nhẫn Bạc Kim Cương Nhân Tạo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chi tiết →' })).toHaveAttribute(
      'href',
      '/products/prod-live-101',
    );
  });

  it('renders Ecosystem Hub with 6 services and 4 internal operation portals', () => {
    renderHomePage();
    expect(screen.getByText('🔨 Đặt lịch Workshop')).toBeInTheDocument();
    expect(screen.getByText('💍 Mua nhẫn Ready Rings')).toBeInTheDocument();
    expect(screen.getByText('🎨 Chế tác theo yêu cầu')).toBeInTheDocument();
    expect(screen.getByText('🤖 Trợ lý AI tư vấn')).toBeInTheDocument();
    expect(screen.getByText('🎫 Tra cứu vé & Booking')).toBeInTheDocument();
    expect(screen.getByText('🚚 Tra cứu đơn hàng')).toBeInTheDocument();

    expect(screen.getByRole('link', { name: /Staff Vận hành/i })).toHaveAttribute(
      'href',
      '/operations/sessions',
    );
    expect(screen.getByRole('link', { name: 'Quản trị Sản phẩm' })).toHaveAttribute(
      'href',
      '/catalogue-management/products',
    );
    expect(screen.getByRole('link', { name: 'Quản lý Ưu đãi' })).toHaveAttribute(
      'href',
      '/promotion-management/programs',
    );
    expect(screen.getByRole('link', { name: /Admin Tài khoản/i })).toHaveAttribute(
      'href',
      '/admin/accounts',
    );
  });

  it('renders Customer Reviews with 4.9 rating and Showroom Footer', () => {
    renderHomePage();
    expect(screen.getByText('4.9 / 5.0 ★')).toBeInTheDocument();
    expect(screen.getByText('Hà My & Quốc Trung')).toBeInTheDocument();
    expect(screen.getByText('Trần Hoàng Long')).toBeInTheDocument();
    expect(screen.getByText('Lê Thuỳ Dung')).toBeInTheDocument();

    expect(screen.getByText(/45 Tràng Tiền, Quận Hoàn Kiếm/i)).toBeInTheDocument();
    expect(screen.getByText(/128 Nguyễn Đình Chiểu, Quận 3/i)).toBeInTheDocument();
    expect(screen.getByText(/Hotline: 1900 6868/i)).toBeInTheDocument();
  });

  it('renders mobile sticky bottom navigation with 5 targets', () => {
    renderHomePage();
    const bottomNav = screen.getByRole('navigation', { name: 'Điều hướng di động' });
    expect(bottomNav).toBeInTheDocument();
    const links = bottomNav.querySelectorAll('a');
    expect(links).toHaveLength(5);
    expect(links[0]).toHaveAttribute('href', '/');
    expect(links[1]).toHaveAttribute('href', '/workshops');
    expect(links[2]).toHaveAttribute('href', '/products');
    expect(links[3]).toHaveAttribute('href', '/workshop-booking/lookup');
    expect(links[4]).toHaveAttribute('href', '/profile');
  });
});
