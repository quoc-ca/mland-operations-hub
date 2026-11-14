import React from 'react';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { ringDesignConfigurationRoutes } from '../routes';

const testRoutes = [
  ...ringDesignConfigurationRoutes,
  {
    path: '/workshops/booking',
    element: <div data-testid="mock-booking-page">Mock Booking Page</div>,
  },
];

const open = (path, options) => renderRoutes(testRoutes, { path, ...options });

describe('Feature-012 Workshop Ring Design Configuration Screens', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  const mockOptionsData = {
    packageReference: 'PKG-COUPLE-01',
    branchReference: 'BR-HCM-01',
    materials: [
      {
        reference: 'MAT-SILVER-925',
        name: 'Bạc Ý 925',
        description: 'Bạc cao cấp chuẩn 92.5% sáng bóng',
        unitPrice: 200000,
        currency: 'VND',
        available: true,
      },
      {
        reference: 'MAT-COPPER',
        name: 'Đồng Đỏ / Đồng Thau',
        description: 'Đồng nguyên chất vintage',
        unitPrice: 150000,
        currency: 'VND',
        available: true,
      },
      {
        reference: 'MAT-STAINLESS-STEEL',
        name: 'Thép Không Gỉ 316L',
        description: 'Thép y tế 316L siêu bền',
        unitPrice: 180000,
        currency: 'VND',
        available: true,
      },
    ],
    shapes: [
      {
        reference: 'SHP-CLASSIC-ROUND',
        name: 'Tròn cổ điển',
        description: 'Dáng nhẫn tròn trơn mượt mà',
        priceAdjustment: 0,
        available: true,
      },
      {
        reference: 'SHP-TWISTED',
        name: 'Dáng xoắn vô cực',
        description: 'Dáng xoắn biểu tượng tình yêu vĩnh cửu',
        priceAdjustment: 50000,
        available: true,
      },
    ],
    sizes: Array.from({ length: 24 }, (_, i) => String(i + 6)),
    components: [
      {
        reference: 'GEM-CZ-WHITE-2MM',
        name: 'Đá CZ Trắng 2mm',
        description: 'Đá nhân tạo lấp lánh giác cắt kim cương',
        unitPrice: 50000,
        maxQuantity: 5,
        available: true,
      },
    ],
    attachments: [
      {
        reference: 'ATT-BEAD-EDGE',
        name: 'Hạt viền Milgrain',
        description: 'Chạm viền bi cổ điển vintage',
        unitPrice: 40000,
        available: true,
      },
    ],
    models: [
      {
        modelReference: 'MOD-CLASSIC-ROUND',
        name: 'Mẫu Nhẫn Cưới Classic',
        description: 'Thiết kế tinh giản, sang trọng vượt thời gian',
        materialReference: 'MAT-SILVER-925',
        shapeReference: 'SHP-CLASSIC-ROUND',
        estimatedPrice: 200000,
        difficultyScore: 2.0,
      },
      {
        modelReference: 'MOD-TWIST-DUO',
        name: 'Mẫu Nhẫn Xoắn Đôi',
        description: 'Điểm nhấn tạo nét uyển chuyển độc đáo',
        materialReference: 'MAT-GOLD-14K',
        shapeReference: 'SHP-TWISTED',
        estimatedPrice: 1650000,
        difficultyScore: 3.5,
      },
    ],
  };

  describe('RingDesignEntryPage (/workshops/:packageReference/design)', () => {
    it('renders disclaimer banner and starting choices', async () => {
      open('/workshops/PKG-COUPLE-01/design', { session: fakeSession('signed-out') });

      expect(await screen.findByTestId('ring-design-entry-page')).toBeInTheDocument();
      expect(screen.getByText(/Thiết kế nhẫn áp dụng cho buổi chế tác Workshop/)).toBeInTheDocument();
      expect(screen.getByText('Mẫu gợi ý theo gói')).toBeInTheDocument();
      expect(screen.getByText('Tự thiết kế từ phôi trắng')).toBeInTheDocument();
    });

    it('navigates to model catalogue when selecting model choice', async () => {
      const user = userEvent.setup();
      const { router } = open('/workshops/PKG-COUPLE-01/design', { session: fakeSession('signed-out') });

      const selectModelBtn = screen.getByRole('button', { name: 'Chọn mẫu này' });
      await user.click(selectModelBtn);

      expect(router.state.location.pathname).toBe('/workshops/PKG-COUPLE-01/design/models');
    });

    it('navigates to blank configurator when selecting blank choice', async () => {
      const user = userEvent.setup();
      const { router } = open('/workshops/PKG-COUPLE-01/design', { session: fakeSession('signed-out') });

      const startBlankBtn = screen.getByRole('button', { name: 'Bắt đầu cấu hình' });
      await user.click(startBlankBtn);

      expect(router.state.location.pathname).toBe('/workshops/PKG-COUPLE-01/design/configure');
      expect(router.state.location.search).toBe('?mode=BLANK');
    });
  });

  describe('RingModelSelectionPage (/workshops/:packageReference/design/models)', () => {
    it('loads and displays catalogue models', async () => {
      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
      });

      open('/workshops/PKG-COUPLE-01/design/models', { session: fakeSession('signed-out') });

      expect(await screen.findByText('Mẫu Nhẫn Cưới Classic')).toBeInTheDocument();
      expect(screen.getByText('Mẫu Nhẫn Xoắn Đôi')).toBeInTheDocument();
      expect(screen.getByText('Độ khó ước tính: 2 / 10')).toBeInTheDocument();
    });

    it('navigates to configurator with selected model reference on card click', async () => {
      const user = userEvent.setup();
      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
      });

      const { router } = open('/workshops/PKG-COUPLE-01/design/models', { session: fakeSession('signed-out') });

      const modelCard = await screen.findByText('Mẫu Nhẫn Cưới Classic');
      await user.click(modelCard);

      expect(router.state.location.pathname).toBe('/workshops/PKG-COUPLE-01/design/configure');
      expect(router.state.location.search).toContain('mode=MODEL');
      expect(router.state.location.search).toContain('modelRef=MOD-CLASSIC-ROUND');
    });
  });

  describe('RingConfigurationPage (/workshops/:packageReference/design/configure)', () => {
    it('renders 2D SVG preview and interactive control fields', async () => {
      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
      });

      open('/workshops/PKG-COUPLE-01/design/configure?mode=BLANK', { session: fakeSession('signed-out') });

      expect(await screen.findByTestId('ring-configuration-page')).toBeInTheDocument();
      expect(screen.getByTestId('ring-preview-container')).toBeInTheDocument();
      expect(screen.getByTestId('ring-side-view-card')).toBeInTheDocument();
      expect(screen.getByTestId('material-card-MAT-SILVER-925')).toBeInTheDocument();
      expect(screen.getByTestId('material-card-MAT-COPPER')).toBeInTheDocument();
      expect(screen.getByTestId('material-card-MAT-STAINLESS-STEEL')).toBeInTheDocument();
      expect(screen.queryByTestId('material-card-MAT-GOLD-14K')).not.toBeInTheDocument();
      expect(screen.getByTestId('shape-card-SHP-CLASSIC-ROUND')).toBeInTheDocument();
      expect(screen.getByTestId('size-pill-29')).toBeInTheDocument();
      expect(screen.getByTestId('size-pill-29')).toHaveTextContent('69 mm');
      expect(screen.getByTestId('thickness-slider')).toBeInTheDocument();
      expect(screen.getByTestId('bandwidth-slider')).toBeInTheDocument();
    });

    it('updates size, toggles engraving, and executes create and save commands', async () => {
      const user = userEvent.setup();

      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
        'POST /api/v1/workshop-designs': json(201, {
          designId: 'DSG-NEW-456',
          designVersion: 1,
          guestSession: 'guest-sess-abc',
          status: 'DRAFT',
        }),
        'POST /api/v1/workshop-designs/DSG-NEW-456/versions': json(200, {
          designId: 'DSG-NEW-456',
          designVersion: 2,
          status: 'DRAFT',
        }),
      });

      const { router } = open('/workshops/PKG-COUPLE-01/design/configure?mode=BLANK', {
        session: fakeSession('signed-out'),
      });

      expect(await screen.findByTestId('ring-configuration-page')).toBeInTheDocument();

      // Click size 12 pill
      const size12Pill = screen.getByTestId('size-pill-12');
      await user.click(size12Pill);

      // Toggle engraving
      const engravingToggle = screen.getByTestId('engraving-toggle');
      await user.click(engravingToggle);

      const engravingInput = await screen.findByTestId('engraving-input');
      await user.type(engravingInput, 'Love Forever');

      // Click Save & Evaluate
      const saveBtn = screen.getByTestId('save-and-evaluate-btn');
      await user.click(saveBtn);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/workshops/PKG-COUPLE-01/design/DSG-NEW-456');
      });

      // Guest session should be preserved in sessionStorage
      expect(sessionStorage.getItem('mland_guest_design_session')).toBe('guest-sess-abc');
    });

    it('shows surface texture warning when outer engraving is selected on a textured shape', async () => {
      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
      });

      const user = userEvent.setup();
      open('/workshops/PKG-COUPLE-01/design/configure?mode=BLANK', {
        session: fakeSession('signed-out'),
      });

      expect(await screen.findByTestId('ring-configuration-page')).toBeInTheDocument();

      // Select Twisted shape (SHP-TWISTED)
      const twistedShape = screen.getByTestId('shape-card-SHP-TWISTED');
      await user.click(twistedShape);

      // Enable engraving
      const engravingToggle = screen.getByTestId('engraving-toggle');
      await user.click(engravingToggle);

      // Select OUTER_BAND
      const outerBtn = await screen.findByTestId('placement-outer');
      await user.click(outerBtn);

      // Surface texture warning must be visible
      expect(await screen.findByTestId('surface-texture-warning')).toBeInTheDocument();
      expect(screen.getByTestId('surface-texture-warning')).toHaveTextContent(/kết cấu bề mặt gồ ghề/);
    });

    it('allows adding multiple engraving items and custom image cropper', async () => {
      mockFetch({
        'GET /api/v1/workshop-design-options': json(200, mockOptionsData),
      });

      const user = userEvent.setup();
      open('/workshops/PKG-COUPLE-01/design/configure?mode=BLANK', {
        session: fakeSession('signed-out'),
      });

      expect(await screen.findByTestId('ring-configuration-page')).toBeInTheDocument();

      // Enable engraving
      const engravingToggle = screen.getByTestId('engraving-toggle');
      await user.click(engravingToggle);

      // Add a second item
      const addBtn = await screen.findByTestId('add-engraving-item-btn');
      await user.click(addBtn);

      expect(screen.getByTestId('engraving-item-0')).toBeInTheDocument();
      expect(screen.getByTestId('engraving-item-1')).toBeInTheDocument();
      expect(screen.getByTestId('upload-engraving-image-btn')).toBeInTheDocument();
    });
  });

  describe('DesignEvaluationPage (/workshops/:packageReference/design/:designId)', () => {
    const mockDesignData = {
      designId: 'DSG-EVAL-789',
      packageReference: 'PKG-COUPLE-01',
      designVersion: 2,
      status: 'VALIDATED',
      evaluation: {
        status: 'VALIDATED',
        difficultyScore: 3.5,
        estimatedPrice: 350000,
        rulesVersion: 'v1.0',
        message: 'Thiết kế đạt chuẩn gia công tại workshop.',
      },
      componentSnapshot: {
        packageReference: 'PKG-COUPLE-01',
        materialReference: 'MAT-SILVER-925',
        shapeReference: 'SHP-CLASSIC-ROUND',
        ringSize: '12',
        components: [{ reference: 'GEM-CZ-WHITE-2MM', quantity: 1, placement: 'CENTER' }],
        attachments: [],
        engraving: { enabled: true, text: 'Love Forever', placement: 'INNER_BAND' },
      },
    };

    it('renders evaluation status, estimated price, and difficulty score', async () => {
      mockFetch({
        'GET /api/v1/workshop-designs/DSG-EVAL-789': json(200, mockDesignData),
      });

      open('/workshops/PKG-COUPLE-01/design/DSG-EVAL-789', { session: fakeSession('signed-out') });

      expect(await screen.findByTestId('design-evaluation-page')).toBeInTheDocument();
      expect(screen.getByText(/Mã thiết kế:/)).toBeInTheDocument();
      expect(screen.getByText('DSG-EVAL-789')).toBeInTheDocument();
      expect(screen.getByText('350.000 đ')).toBeInTheDocument();
      expect(screen.getByText('3.5 / 10')).toBeInTheDocument();
      expect(screen.getByTestId('proceed-booking-btn')).toBeInTheDocument();
    });

    it('navigates to workshop booking flow when clicking proceed to booking', async () => {
      const user = userEvent.setup();
      mockFetch({
        'GET /api/v1/workshop-designs/DSG-EVAL-789': json(200, mockDesignData),
      });

      const { router } = open('/workshops/PKG-COUPLE-01/design/DSG-EVAL-789', {
        session: fakeSession('signed-out'),
      });

      const proceedBtn = await screen.findByTestId('proceed-booking-btn');
      await user.click(proceedBtn);

      expect(router.state.location.pathname).toBe('/workshops/booking');
      expect(router.state.location.search).toContain('packageReference=PKG-COUPLE-01');
      expect(router.state.location.search).toContain('designId=DSG-EVAL-789');
    });

    it('archives design when user confirms archive action', async () => {
      const user = userEvent.setup();
      vi.spyOn(window, 'confirm').mockImplementation(() => true);

      mockFetch({
        'GET /api/v1/workshop-designs/DSG-EVAL-789': json(200, mockDesignData),
        'POST /api/v1/workshop-designs/DSG-EVAL-789/archive': json(200, {
          ...mockDesignData,
          status: 'ARCHIVED',
        }),
      });

      open('/workshops/PKG-COUPLE-01/design/DSG-EVAL-789', { session: fakeSession('signed-out') });

      const archiveBtn = await screen.findByTestId('archive-design-btn');
      await user.click(archiveBtn);

      expect(await screen.findByText('Đã lưu trữ')).toBeInTheDocument();
    });
  });

  describe('BranchCompatibilityPage (/workshops/:packageReference/design/recovery)', () => {
    it('displays incompatibility notice, reason, and recovery actions', async () => {
      open(
        '/workshops/PKG-COUPLE-01/design/recovery?packageReference=PKG-COUPLE-01&designId=DSG-REC-01&reason=Thieu+may+khac+laser',
        { session: fakeSession('signed-out') }
      );

      expect(await screen.findByTestId('branch-compatibility-page')).toBeInTheDocument();
      expect(screen.getByText(/Chi nhánh workshop bạn vừa chọn chưa hỗ trợ/)).toBeInTheDocument();
      expect(screen.getByText('Thieu may khac laser')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Điều chỉnh thiết kế/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Chọn chi nhánh khác/ })).toBeInTheDocument();
    });
  });

  describe('MemberDesignHistoryPage (/my/designs)', () => {
    it('requires session when signed out', async () => {
      open('/my/designs', { session: fakeSession('signed-out') });

      expect(await screen.findByText('Bạn cần đăng nhập để tiếp tục.')).toBeInTheDocument();
    });

    it('displays saved designs list when signed in as member', async () => {
      mockFetch({
        'GET /api/v1/workshop-designs': json(200, [
          {
            designId: 'DSG-DEMO-MEMBER-01',
            packageReference: 'PKG-COUPLE-01',
            status: 'DRAFT',
            designVersion: 1,
            estimatedPrice: '250000.00',
            difficultyScore: 2.0,
            componentSnapshot: {
              materialReference: 'MAT-SILVER-925',
              shapeReference: 'SHP-CLASSIC-ROUND',
              ringSize: '8',
              components: [],
            },
            updatedAt: '2026-11-14T10:00:00Z',
          },
        ]),
      });

      open('/my/designs', { session: fakeSession('signed-in') });

      expect(await screen.findByTestId('member-design-history-page')).toBeInTheDocument();
      expect(await screen.findByText('DSG-DEMO-MEMBER-01')).toBeInTheDocument();
      expect(screen.getByText('250.000 đ')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Xem chi tiết →' })).toBeInTheDocument();
    });
  });
});
