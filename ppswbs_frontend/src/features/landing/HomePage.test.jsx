import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../../shared/i18n/I18nProvider';
import HomePage from './HomePage';
import { AuthProvider } from '../../shared/auth/AuthProvider';
import * as workshopApi from '../../api/workshops';
import * as catalogueApi from '../../api/catalogue';
import { firebaseAuthClient } from '../accountmanagement/auth/FirebaseAuthClient';
import * as useCurrentAccountModule from '../accountmanagement/hooks/useCurrentAccount';

function renderHomePage({
  initialAuthState = { status: 'signed-out' },
  httpHandler = vi.fn().mockRejectedValue(new Error('no mock')),
  language,
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
    <I18nProvider initialLanguage={language}>
      <AuthProvider source={mockAuthSource}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
}

function signedInRole(role) {
  vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
    account: { role },
    status: 'authenticated',
    refetch: vi.fn(),
  });
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
    expect(within(nav).getByRole('link', { name: 'Cấu hình nhẫn' })).toHaveAttribute('href', '/workshops/PKG-COUPLE-SILVER/design');
    expect(within(nav).getByRole('link', { name: 'Ready Rings' })).toHaveAttribute('href', '/products');
    expect(within(nav).getByRole('link', { name: 'Yêu cầu chế tác' })).toHaveAttribute('href', '/design-requests/new');
    expect(within(nav).getByRole('link', { name: 'Tra cứu' })).toHaveAttribute('href', '/workshop-booking/lookup');
    expect(within(nav).queryByRole('link', { name: 'Trợ lý AI' })).not.toBeInTheDocument();
    expect(screen.getByTestId('floating-ai-chat-btn')).toHaveAttribute('href', '/ai-support');
  });

  it('renders Guest topbar CTA when unauthenticated', () => {
    renderHomePage({ initialAuthState: { status: 'signed-out' } });
    const loginLink = screen.getByRole('link', { name: 'Đăng nhập' });
    expect(loginLink).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('href', '/login');
    expect(screen.queryByText('ACTIVE · MEMBER')).not.toBeInTheDocument();
    expect(screen.queryByText(/HANDOFF ·/i)).not.toBeInTheDocument();
  });

  it('renders Member chip and, with an upcoming ticket, its real time and a link to that booking', async () => {
    signedInRole('MEMBER');
    const start = new Date(Date.now() + 3 * 24 * 3600 * 1000).toISOString();
    vi.spyOn(workshopApi, 'listMemberBookingGroups').mockResolvedValue([
      {
        groupCode: 'WG-NEXT-1',
        groupStatus: 'CONFIRMED',
        tickets: [{ ticketCode: 'WT-1', ticketStatus: 'CONFIRMED', sessions: [{ sessionReference: 's1', scheduledStart: start, scheduledEnd: start }] }],
      },
      {
        groupCode: 'WG-PENDING-2',
        groupStatus: 'PENDING_PAYMENT',
        tickets: [{ ticketCode: 'WT-2', ticketStatus: 'PENDING_PAYMENT', sessions: [{ sessionReference: 's2', scheduledStart: start, scheduledEnd: start }] }],
      },
    ]);
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const memberChip = screen.getByRole('button', { name: /Tùy chọn tài khoản/i });
    expect(memberChip).toBeInTheDocument();
    expect(screen.getByText('ACTIVE · MEMBER')).toBeInTheDocument();
    expect(await screen.findByText(/Buổi workshop sắp tới:/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Xem vé & mã QR/i })).toHaveAttribute(
      'href',
      '/workshop-booking/lookup?code=WG-NEXT-1',
    );
    expect(screen.getByRole('link', { name: /Đơn hàng \(\d+\)/ })).toHaveAttribute(
      'href',
      '/member/orders',
    );
  });

  it('says there is no upcoming workshop and offers no QR link when the Member has no confirmed ticket', async () => {
    signedInRole('MEMBER');
    vi.spyOn(workshopApi, 'listMemberBookingGroups').mockResolvedValue([]);
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    expect(await screen.findByText(/Bạn chưa có buổi workshop nào sắp tới/i)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Xem vé/i })).toBeNull();
  });

  it('opens dropdown menu with account options when user chip is clicked', () => {
    signedInRole('MEMBER');
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
    signedInRole('MEMBER');
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
    expect(screen.getByText('490.000 ₫ / người')).toBeInTheDocument();

    expect(screen.getByRole('heading', { level: 4, name: 'Silver Clay Workshop' })).toBeInTheDocument();
    expect(screen.getByText('690.000 ₫ / người')).toBeInTheDocument();
    expect(screen.getByText('Được yêu thích nhất')).toBeInTheDocument();

    expect(screen.getByRole('heading', { level: 4, name: 'Wax Carving Workshop' })).toBeInTheDocument();
    expect(screen.getByText('890.000 ₫ / người')).toBeInTheDocument();
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
          canBuy: true,
        },
      ],
    };

    vi.spyOn(catalogueApi, 'listPublicProducts').mockResolvedValue(liveProductsMock);

    renderHomePage();

    // The live products use 002's product card: name and image open the product, a cart button adds to the cart.
    await waitFor(() => {
      expect(screen.getByText('Nhẫn Bạc Kim Cương Nhân Tạo')).toBeInTheDocument();
    });
    expect(screen.getByText('Nhẫn Bạc Kim Cương Nhân Tạo').closest('a')).toHaveAttribute(
      'href',
      '/products/prod-live-101',
    );
    expect(screen.getByText('580.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('Còn hàng')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thêm Nhẫn Bạc Kim Cương Nhân Tạo vào giỏ hàng' })).toBeEnabled();
    // Shown as a sideways strip with arrows and a link to the whole catalogue.
    expect(screen.getByRole('list', { name: 'Nhẫn Ready Rings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sản phẩm tiếp theo' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem tất cả nhẫn →' })).toHaveAttribute('href', '/products');
  });

  it('shows every product of the first catalogue page, not only four', async () => {
    const items = Array.from({ length: 7 }, (_, index) => ({
      id: `p${index}`, name: `Nhẫn số ${index}`, priceRange: { minimum: '100000' }, images: [], variants: [], canBuy: true,
    }));
    vi.spyOn(catalogueApi, 'listPublicProducts').mockResolvedValue({ items });
    renderHomePage();
    await waitFor(() => expect(screen.getByText('Nhẫn số 6')).toBeInTheDocument());
    expect(within(screen.getByRole('list', { name: 'Nhẫn Ready Rings' })).getAllByRole('listitem')).toHaveLength(7);
  });

  it('renders Ecosystem Hub with 6 services and no internal portal for a Guest', () => {
    renderHomePage();
    expect(screen.getByText('🔨 Đặt lịch Workshop')).toBeInTheDocument();
    expect(screen.getByText('💍 Mua nhẫn Ready Rings')).toBeInTheDocument();
    expect(screen.getByText('🎨 Chế tác theo yêu cầu')).toBeInTheDocument();
    expect(screen.getByText('🤖 Trợ lý AI tư vấn')).toBeInTheDocument();
    expect(screen.getByText('🎫 Tra cứu vé & Booking')).toBeInTheDocument();
    expect(screen.getByText('🚚 Tra cứu đơn hàng')).toBeInTheDocument();

    expect(screen.queryByRole('region', { name: /Cổng/ })).toBeNull();
    expect(screen.queryByRole('link', { name: /Admin Tài khoản/i })).toBeNull();
  });

  it.each([
    ['STAFF', ['/operations/sessions', '/design-review-requests', '/catalogue-management/products']],
    ['MANAGER', ['/operations/sessions', '/design-review-requests', '/catalogue-management/products', '/promotion-management/programs']],
    ['ADMIN', ['/admin/accounts']],
  ])('offers %s only its own portals', (role, expected) => {
    signedInRole(role);
    const { container } = renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const links = [...container.querySelectorAll('.landing-portals-bar a')].map(link => link.getAttribute('href'));
    expect(links).toEqual(expected);
  });

  it.each(['MEMBER', 'CARRIER'])('shows no internal portal to %s', (role) => {
    signedInRole(role);
    const { container } = renderHomePage({ initialAuthState: { status: 'signed-in' } });
    expect(container.querySelector('.landing-portals-bar')).toBeNull();
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

  it('shows the whole landing page in English when the language is en', async () => {
    signedInRole('MEMBER');
    vi.spyOn(workshopApi, 'listMemberBookingGroups').mockResolvedValue([]);
    renderHomePage({ language: 'en', initialAuthState: { status: 'signed-in' } });
    expect(screen.getByText('YOUR OWN CRAFTING EXPERIENCE')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /BOOK A WORKSHOP NOW/i })).toHaveAttribute('href', '/workshops');
    expect(screen.getByText('FEATURED CRAFTING WORKSHOP PACKAGES')).toBeInTheDocument();
    expect(screen.getByText('490,000 ₫ / person')).toBeInTheDocument();
    expect(screen.getByText('Twilight Twist Silver Ring')).toBeInTheDocument();
    expect(screen.getByText('🔨 Book a workshop')).toBeInTheDocument();
    expect(screen.getByText(/Hanoi: 45 Trang Tien/i)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Mobile navigation' })).toBeInTheDocument();
    expect(await screen.findByText(/You have no upcoming workshops/i)).toBeInTheDocument();
    expect(screen.queryByText(/CÁC GÓI WORKSHOP/)).not.toBeInTheDocument();
  });

  it('points the Carrier account tab at password self-service, not the profile page', () => {
    signedInRole('CARRIER');
    renderHomePage({ initialAuthState: { status: 'signed-in' } });
    const links = screen.getByRole('navigation', { name: 'Điều hướng di động' }).querySelectorAll('a');
    expect(links[4]).toHaveAttribute('href', '/security/password');
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

  it.each(['STAFF', 'MANAGER', 'ADMIN', 'CARRIER'])('offers %s no Member journey, cart or Member menu entry', (role) => {
    signedInRole(role);
    const { container } = renderHomePage({ initialAuthState: { status: 'signed-in' } });
    expect(screen.queryByText('ACTIVE · MEMBER')).not.toBeInTheDocument();
    expect(screen.queryByText('🚚 Tra cứu đơn hàng')).not.toBeInTheDocument();
    expect(container.querySelector('.main-topbar-cart')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Tùy chọn tài khoản/i }));
    const targets = within(screen.getByRole('menu')).getAllByRole('menuitem').map(item => item.getAttribute('href'));
    for (const path of ['/my/designs', '/member/orders', '/me/loyalty', '/me/saved-vouchers', '/member/consultations', '/account/my-custom-orders']) {
      expect(targets).not.toContain(path);
    }
    const nav = screen.getByRole('navigation', { name: 'Khám phá' });
    expect(within(nav).queryByRole('link', { name: 'Cấu hình nhẫn' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Custom' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Trợ lý AI' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('floating-ai-chat-btn')).not.toBeInTheDocument();
    if (role !== 'CARRIER') {
      expect(within(nav).getByRole('link', { name: 'Workshop' })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: 'Ready Rings' })).toBeInTheDocument();
    }

    expect(screen.queryByText('🎨 Chế tác theo yêu cầu')).not.toBeInTheDocument();
    expect(screen.queryByText('🤖 Trợ lý AI tư vấn')).not.toBeInTheDocument();
  });
});
