import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { reviewManagementRoutes } from '../routes';

vi.mock('../../../shared/auth/RouteAccessGuard', () => import('../../../test/passThroughGuard'));

const open = (path, options) => renderRoutes(reviewManagementRoutes, { path, ...options });

describe('F11-PUBLIC-REVIEW-LIST Screen (/reviews)', () => {
  it('renders public reviews list with aggregate score and count', async () => {
    mockFetch({
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 3, averageRating: 4.7 },
        items: [
          {
            id: 'rev-pub-01',
            targetType: 'WORKSHOP_SESSION',
            targetReference: 'SESS-101',
            rating: 5,
            bodyText: 'Workshop làm nhẫn rất tuyệt vời!',
            status: 'PUBLIC',
            maskedDisplayName: 'Hội viên #1***45',
            createdAt: '2026-11-05T10:00:00Z',
          },
          {
            id: 'rev-pub-02',
            targetType: 'RETAIL_PRODUCT',
            targetReference: 'PROD-202',
            rating: 4,
            bodyText: 'Nhẫn bạc đóng gói cẩn thận.',
            status: 'PUBLIC',
            maskedDisplayName: 'k***h@gmail.com',
            createdAt: '2026-11-05T11:00:00Z',
          },
        ],
      }),
    });

    open('/reviews', { session: fakeSession('signed-out') });

    expect(await screen.findByText('Đánh giá từ khách hàng')).toBeInTheDocument();
    expect(screen.queryByText(/Chỉ đánh giá PUBLIC được hiển thị và tính vào điểm tổng hợp/)).not.toBeInTheDocument();
    expect(screen.getByText('Workshop làm nhẫn rất tuyệt vời!')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn bạc đóng gói cẩn thận.')).toBeInTheDocument();
    expect(screen.getByText('Hội viên #1***45')).toBeInTheDocument();
  });

  it('renders empty state when no public reviews exist', async () => {
    mockFetch({
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 0, averageRating: 0 },
        items: [],
      }),
    });

    open('/reviews', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Chưa có đánh giá công khai.')).toBeInTheDocument();
  });
});

describe('F11-GUEST-LOOKUP Retired (/reviews/guest/lookup)', () => {
  it('redirects guest lookup route to public reviews list', async () => {
    mockFetch({
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 0, averageRating: 0 },
        items: [],
      }),
    });

    const { router } = open('/reviews/guest/lookup', { session: fakeSession('signed-out') });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/reviews');
    });
    expect(await screen.findByText('Chưa có đánh giá công khai.')).toBeInTheDocument();
  });
});

describe('F11 Member reviews page (/reviews, tabs Chưa đánh giá / Đã đánh giá — session 2026-11-23)', () => {
  const PROFILE = 'GET /api/v1/accounts/me/profile';
  const SOURCES = 'GET /api/v1/reviews/eligible-sources';
  const pendingRing = {
    sourceType: 'RETAIL_ORDER_LINE', sourceReference: 'ITEM-888', targetType: 'RETAIL_PRODUCT', targetReference: '777',
    label: 'Nhẫn bạc đính đá Topaz', orderId: 'ORDER-1', variantId: '9', size: 12, eligible: true,
  };
  const reviewedRing = {
    sourceType: 'RETAIL_ORDER_LINE', sourceReference: 'ITEM-889', targetType: 'RETAIL_PRODUCT', targetReference: '778',
    label: 'Nhẫn vàng trơn', orderId: 'ORDER-1', variantId: '10', size: 14, eligible: true, alreadyReviewed: true,
    reviewId: 'rev-1', review: { id: 'rev-1', rating: 4, bodyText: 'Vàng đẹp', status: 'PUBLIC', canEdit: true, targetType: 'RETAIL_PRODUCT', createdAt: '2026-11-20T10:00:00Z' },
  };
  const reviewedWorkshop = {
    sourceType: 'WORKSHOP_ATTENDANCE', sourceReference: 'TICK-1', targetType: 'WORKSHOP_SESSION', targetReference: 'SESS-1',
    label: 'Workshop làm nhẫn', eligible: true, alreadyReviewed: true, reviewId: 'rev-2',
    review: { id: 'rev-2', rating: 2, bodyText: 'Hơi đông', status: 'PUBLIC', canEdit: true, targetType: 'WORKSHOP_SESSION', createdAt: '2026-11-21T10:00:00Z' },
  };
  beforeEach(() => clearRoleCache());

  it('no longer has the separate "có thể đánh giá" page', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [SOURCES]: json(200, { items: [pendingRing] }) });
    open('/reviews/eligible-sources');
    expect(await screen.findByRole('tab', { name: /Chưa đánh giá/ })).toBeInTheDocument();
    expect(screen.queryByText('Các sản phẩm, trải nghiệm có thể đánh giá')).toBeNull();
  });

  it('lists what is left to review with the product name and size, never the internal reference', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [SOURCES]: json(200, { items: [pendingRing, reviewedRing] }) });
    open('/reviews');
    expect(await screen.findByText('Nhẫn bạc đính đá Topaz')).toBeInTheDocument();
    expect(screen.getByText('Size 12')).toBeInTheDocument();
    expect(screen.queryByText('ITEM-888')).toBeNull();
    expect(screen.queryByText('Nhẫn vàng trơn')).toBeNull();
    expect(screen.getByRole('button', { name: 'Đánh giá' })).toBeInTheDocument();
  });

  it('opens the order review screen from "Đánh giá" on a retail line', async () => {
    const user = userEvent.setup();
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [SOURCES]: json(200, { items: [pendingRing] }) });
    open('/reviews');
    await user.click(await screen.findByRole('button', { name: 'Đánh giá' }));
    expect(await screen.findByRole('heading', { name: 'Đánh giá sản phẩm' })).toBeInTheDocument();
  });

  it('shows the Member own reviews in "Đã đánh giá" with the sort, type and star filters', async () => {
    const user = userEvent.setup();
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), [SOURCES]: json(200, { items: [pendingRing, reviewedRing, reviewedWorkshop] }) });
    open('/reviews?tab=reviewed');
    expect(await screen.findByText('Vàng đẹp')).toBeInTheDocument();
    expect(screen.getByText('Hơi đông')).toBeInTheDocument();
    expect(screen.queryByText('Nhẫn bạc đính đá Topaz')).toBeNull();
    expect(screen.getByTestId('filter-sort')).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId('filter-rating'), '4');
    expect(screen.getByText('Vàng đẹp')).toBeInTheDocument();
    expect(screen.queryByText('Hơi đông')).toBeNull();

    await user.selectOptions(screen.getByTestId('filter-rating'), '');
    await user.selectOptions(screen.getByTestId('filter-target-type'), 'WORKSHOP_SESSION');
    expect(screen.getByText('Hơi đông')).toBeInTheDocument();
    expect(screen.queryByText('Vàng đẹp')).toBeNull();
  });

  it('keeps the public list for a package link', async () => {
    mockFetch({ [PROFILE]: json(200, { role: 'MEMBER' }), 'GET /api/v1/reviews': json(200, { aggregate: { count: 0, averageRating: 0 }, items: [] }) });
    open('/reviews?packageId=145');
    expect(await screen.findByText('Chưa có đánh giá công khai.')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Chưa đánh giá/ })).toBeNull();
  });
});

describe('F11 order review screen (/reviews/orders/:orderId — session 2026-11-23)', () => {
  const SOURCES = 'GET /api/v1/reviews/eligible-sources';
  const line = (ref, label, size, extra = {}) => ({
    sourceType: 'RETAIL_ORDER_LINE', sourceReference: ref, targetType: 'RETAIL_PRODUCT', targetReference: `P-${ref}`,
    label, orderId: 'ORDER-1', size, eligible: true, ...extra,
  });

  it('shows every product of the order on top of its own review and sends one review per product', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [SOURCES]: json(200, { items: [
        line('L1', 'Nhẫn Topaz', 12),
        line('L2', 'Nhẫn Ruby', 14),
        line('L3', 'Nhẫn khác đơn', 10, { orderId: 'ORDER-2' }),
      ] }),
      'POST /api/v1/reviews': json(201, { id: 'rev-new' }),
    });
    open('/reviews/orders/ORDER-1');

    expect(await screen.findByText('Nhẫn Topaz')).toBeInTheDocument();
    expect(screen.getByText('Size 12')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn Ruby')).toBeInTheDocument();
    expect(screen.queryByText('Nhẫn khác đơn')).toBeNull();
    expect(screen.queryByText(/\bxu\b/i)).toBeNull();

    const ruby = screen.getByRole('region', { name: 'Nhẫn Ruby' });
    await user.click(within(ruby).getByRole('radio', { name: '3 sao' }));
    await user.type(within(ruby).getByRole('textbox'), 'Hơi rộng');
    await user.click(screen.getByRole('button', { name: 'Hoàn thành' }));

    await waitFor(() => expect(calls.filter(c => c.method === 'POST')).toHaveLength(2));
    const posts = calls.filter(c => c.method === 'POST');
    expect(posts[0].body).toMatchObject({ sourceReference: 'L1', targetType: 'RETAIL_PRODUCT', targetReference: 'P-L1', rating: 5 });
    expect(posts[1].body).toMatchObject({ sourceReference: 'L2', targetReference: 'P-L2', rating: 3, bodyText: 'Hơi rộng' });
    expect(posts[0].headers['Idempotency-Key']).not.toEqual(posts[1].headers['Idempotency-Key']);
  });

  it('says so when the order has nothing left to review', async () => {
    mockFetch({ [SOURCES]: json(200, { items: [line('L1', 'Nhẫn Topaz', 12, { alreadyReviewed: true, reviewId: 'r1', review: { id: 'r1', rating: 5 } })] }) });
    open('/reviews/orders/ORDER-1');
    expect(await screen.findByText('Đơn này không còn sản phẩm nào chờ đánh giá.')).toBeInTheDocument();
  });
});

describe('F11-CREATE-REVIEW Screen (/reviews/new)', () => {
  it('displays error notice when navigated to directly without a chosen source', async () => {
    mockFetch({});
    open('/reviews/new', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Vui lòng chọn nguồn đủ điều kiện trước.')).toBeInTheDocument();
  });

  it('submits review successfully and validates 2000-char length limit', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'POST /api/v1/reviews': json(201, {
        id: 'rev-created-999',
        targetType: 'WORKSHOP_SESSION',
        targetReference: 'SESS-101',
        rating: 5,
        bodyText: 'Trải nghiệm rất tuyệt vời!',
        status: 'PUBLIC',
        maskedDisplayName: 'Hội viên #1***45',
        createdAt: '2026-11-06T00:00:00Z',
      }),
      'GET /api/v1/reviews/rev-created-999': json(200, {
        id: 'rev-created-999',
        targetType: 'WORKSHOP_SESSION',
        targetReference: 'SESS-101',
        rating: 5,
        bodyText: 'Trải nghiệm rất tuyệt vời!',
        status: 'PUBLIC',
        maskedDisplayName: 'Hội viên #1***45',
        createdAt: '2026-11-06T00:00:00Z',
      }),
    });

    // Navigate with state
    const source = {
      sourceType: 'WORKSHOP_ATTENDANCE',
      sourceReference: 'TICK-101',
      targetType: 'WORKSHOP_SESSION',
      targetReference: 'SESS-101',
      label: 'Workshop làm nhẫn cơ bản',
    };

    renderRoutes(reviewManagementRoutes, {
      path: '/reviews/new',
      session: fakeSession('signed-in'),
      state: { source },
    });

    expect(await screen.findByText('Chia sẻ trải nghiệm của bạn')).toBeInTheDocument();
    expect(screen.getByText('Workshop làm nhẫn cơ bản')).toBeInTheDocument();

    const textarea = screen.getByPlaceholderText(/Viết đánh giá tối đa 2.000 ký tự/);
    await user.type(textarea, 'Trải nghiệm rất tuyệt vời!');

    const submitBtn = screen.getByRole('button', { name: 'Đăng đánh giá' });
    await user.click(submitBtn);

    const postCall = calls.find(c => c.path === '/api/v1/reviews');
    expect(postCall).toBeDefined();
    expect(postCall.body).toMatchObject({
      sourceType: 'WORKSHOP_ATTENDANCE',
      sourceReference: 'TICK-101',
      targetType: 'WORKSHOP_SESSION',
      targetReference: 'SESS-101',
      rating: 5,
      bodyText: 'Trải nghiệm rất tuyệt vời!',
    });
  });

  it('rejects submission exceeding 2000 characters before sending API call', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({});

    const source = {
      sourceType: 'WORKSHOP_ATTENDANCE',
      sourceReference: 'TICK-101',
      targetType: 'WORKSHOP_SESSION',
      targetReference: 'SESS-101',
      label: 'Workshop làm nhẫn cơ bản',
    };

    renderRoutes(reviewManagementRoutes, {
      path: '/reviews/new',
      session: fakeSession('signed-in'),
      state: { source },
    });

    expect(await screen.findByText('Chia sẻ trải nghiệm của bạn')).toBeInTheDocument();

    const textarea = screen.getByPlaceholderText(/Viết đánh giá tối đa 2.000 ký tự/);
    fireEvent.change(textarea, { target: { value: 'A'.repeat(2005) } });

    const submitBtn = screen.getByRole('button', { name: 'Đăng đánh giá' });
    await user.click(submitBtn);

    expect(await screen.findByText('Nội dung không được vượt quá 2.000 ký tự.')).toBeInTheDocument();
    expect(calls.filter(c => c.path === '/api/v1/reviews').length).toBe(0);
  });
});

describe('F11-EDIT-REVIEW Screen (/reviews/:id/edit)', () => {
  it('shows the reviewed product (name and size) above the form, found by review id among the Member sources', async () => {
    mockFetch({
      'GET /api/v1/reviews/rev-edit-02': json(200, { id: 'rev-edit-02', rating: 5, bodyText: 'oke', version: 0, status: 'PUBLIC', canEdit: true, targetType: 'RETAIL_PRODUCT', targetReference: '42' }),
      'GET /api/v1/reviews/eligible-sources': json(200, { items: [
        { sourceType: 'RETAIL_ORDER_LINE', sourceReference: 'L9', targetType: 'RETAIL_PRODUCT', targetReference: '42', label: 'Nhẫn bạc cành lá citrine', size: 8, alreadyReviewed: true, reviewId: 'rev-edit-02' },
      ] }),
    });
    open('/reviews/rev-edit-02/edit', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Nhẫn bạc cành lá citrine')).toBeInTheDocument();
    expect(screen.getByText('Size 8')).toBeInTheDocument();
  });

  it('loads existing review and submits update with If-Match-Version header', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/reviews/rev-edit-01': json(200, {
        id: 'rev-edit-01',
        rating: 4,
        bodyText: 'Nội dung ban đầu',
        version: 2,
        status: 'PUBLIC',
        maskedDisplayName: 'Hội viên #1***45',
        canEdit: true,
      }),
      'PATCH /api/v1/reviews/rev-edit-01': json(200, {
        id: 'rev-edit-01',
        rating: 5,
        bodyText: 'Nội dung sau chỉnh sửa',
        version: 3,
        status: 'PUBLIC',
        maskedDisplayName: 'Hội viên #1***45',
        canEdit: true,
      }),
    });

    open('/reviews/rev-edit-01/edit', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Chỉnh sửa đánh giá')).toBeInTheDocument();
    const textarea = screen.getByPlaceholderText(/Viết đánh giá tối đa 2.000 ký tự/);
    expect(textarea).toHaveValue('Nội dung ban đầu');

    await user.clear(textarea);
    await user.type(textarea, 'Nội dung sau chỉnh sửa');

    const saveBtn = screen.getByRole('button', { name: 'Lưu thay đổi' });
    await user.click(saveBtn);

    const patchCall = calls.find(c => c.path === '/api/v1/reviews/rev-edit-01' && c.method === 'PATCH');
    expect(patchCall).toBeDefined();
    expect(patchCall.headers).toMatchObject({
      'If-Match-Version': '2',
    });
    expect(patchCall.body).toMatchObject({
      rating: 4,
      bodyText: 'Nội dung sau chỉnh sửa',
    });
  });

  it('displays conflict notice when version conflict occurs (409)', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/reviews/rev-edit-02': json(200, {
        id: 'rev-edit-02',
        rating: 4,
        bodyText: 'Đang sửa thì bị conflict',
        version: 1,
        status: 'PUBLIC',
        canEdit: true,
      }),
      'PATCH /api/v1/reviews/rev-edit-02': json(409, {
        code: 'REVIEW_STATE_CONFLICT',
        message: 'Dữ liệu đã thay đổi. Hãy tải lại trước khi tiếp tục.',
      }),
    });

    open('/reviews/rev-edit-02/edit', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Chỉnh sửa đánh giá')).toBeInTheDocument();

    const saveBtn = screen.getByRole('button', { name: 'Lưu thay đổi' });
    await user.click(saveBtn);

    expect(await screen.findByText(/Dữ liệu đã thay đổi/)).toBeInTheDocument();
  });

  it('blocks editing of another user review when canEdit is false and displays forbidden notice', async () => {
    mockFetch({
      'GET /api/v1/reviews/rev-other-user': json(200, {
        id: 'rev-other-user',
        rating: 5,
        bodyText: 'Đánh giá của người khác',
        version: 1,
        status: 'PUBLIC',
        maskedDisplayName: 'Người khác #8***88',
        canEdit: false,
      }),
    });

    open('/reviews/rev-other-user/edit', { session: fakeSession('signed-in') });

    expect(await screen.findByTestId('edit-review-forbidden')).toBeInTheDocument();
    expect(screen.getByText(/Bạn không có quyền chỉnh sửa đánh giá này/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/Viết đánh giá tối đa 2.000 ký tự/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Lưu thay đổi' })).toBeNull();
  });
});

describe('F11-REVIEW-DETAIL Screen (/reviews/:id)', () => {
  it('renders review detail and manager reply when present and shows edit button only if owner', async () => {
    mockFetch({
      'GET /api/v1/reviews/rev-detail-01': json(200, {
        id: 'rev-detail-01',
        targetType: 'WORKSHOP_SESSION',
        targetReference: 'SESS-101',
        rating: 5,
        bodyText: 'Cảm ơn nghệ nhân đã hướng dẫn tận tình!',
        status: 'PUBLIC',
        maskedDisplayName: 'Hội viên #9***99',
        createdAt: '2026-11-05T09:00:00Z',
        canEdit: true,
        reply: {
          id: 'rep-01',
          bodyText: 'Mland rất vui vì bạn hài lòng!',
        },
      }),
    });

    open('/reviews/rev-detail-01', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Chi tiết đánh giá')).toBeInTheDocument();
    expect(screen.getByText('Cảm ơn nghệ nhân đã hướng dẫn tận tình!')).toBeInTheDocument();
    expect(screen.getByText('Mland rất vui vì bạn hài lòng!')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chỉnh sửa đánh giá của bạn' })).toBeInTheDocument();
  });

  it('hides edit button on review detail when viewer is not the owner (canEdit is false)', async () => {
    mockFetch({
      'GET /api/v1/reviews/rev-detail-02': json(200, {
        id: 'rev-detail-02',
        targetType: 'RETAIL_PRODUCT',
        targetReference: 'PROD-101',
        rating: 4,
        bodyText: 'Nhẫn rất đẹp nhưng người khác đánh giá.',
        status: 'PUBLIC',
        maskedDisplayName: 'Khách hàng khác',
        createdAt: '2026-11-05T10:00:00Z',
        canEdit: false,
      }),
    });

    open('/reviews/rev-detail-02', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Chi tiết đánh giá')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn rất đẹp nhưng người khác đánh giá.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Chỉnh sửa đánh giá của bạn' })).toBeNull();
  });
});

describe('F11-MODERATION-QUEUE Screen (/review-moderation)', () => {
  it('requires session when signed out', async () => {
    mockFetch({});
    open('/review-moderation', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Bạn cần đăng nhập để tiếp tục.')).toBeInTheDocument();
  });

  it('renders moderation queue and navigates to moderation detail', async () => {
    mockFetch({
      'GET /api/v1/review-moderation': json(200, {
        items: [
          {
            id: 'rev-mod-01',
            status: 'PUBLIC',
            rating: 1,
            bodyText: 'Chất lượng không như quảng cáo.',
            maskedDisplayName: 'Khách #8***12',
          },
        ],
      }),
    });

    open('/review-moderation', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Staff và Manager xử lý hậu kiểm theo quyền được cấp.')).toBeInTheDocument();
    expect(screen.getByText('Chất lượng không như quảng cáo.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem chi tiết' })).toHaveAttribute('href', '/review-moderation/rev-mod-01');
  });
});

describe('F11-MODERATION-DETAIL Screen (/review-moderation/:id)', () => {
  it('submits hide action with reason and optimistic If-Match-Version', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/reviews/rev-mod-02': json(200, {
        id: 'rev-mod-02',
        status: 'PUBLIC',
        rating: 1,
        bodyText: 'Cần ẩn đánh giá này do chứa từ ngữ vi phạm',
        version: 3,
        maskedDisplayName: 'Khách #1***00',
      }),
      'PATCH /api/v1/reviews/rev-mod-02/visibility': json(200, {
        id: 'rev-mod-02',
        status: 'HIDDEN',
        version: 4,
      }),
    });

    open('/review-moderation/rev-mod-02', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Quản lý đánh giá')).toBeInTheDocument();
    expect(screen.getByText('Cần ẩn đánh giá này do chứa từ ngữ vi phạm')).toBeInTheDocument();

    const reasonInput = screen.getByPlaceholderText(/Nhập lý do xử lý/);
    await user.type(reasonInput, 'Vi phạm quy chuẩn ngôn từ.');

    const hideBtn = screen.getByRole('button', { name: 'Ẩn đánh giá' });
    await user.click(hideBtn);

    expect(await screen.findByText('Đã cập nhật trạng thái đánh giá.')).toBeInTheDocument();

    const visCall = calls.find(c => c.path === '/api/v1/reviews/rev-mod-02/visibility');
    expect(visCall).toBeDefined();
    expect(visCall.headers).toMatchObject({
      'If-Match-Version': '3',
    });
    expect(visCall.body).toMatchObject({
      status: 'HIDDEN',
      reason: 'Vi phạm quy chuẩn ngôn từ.',
    });
  });

  it('submits official manager reply with Idempotency-Key', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/reviews/rev-mod-03': json(200, {
        id: 'rev-mod-03',
        status: 'PUBLIC',
        rating: 3,
        bodyText: 'Sản phẩm tạm ổn nhưng cần cải thiện phục vụ.',
        version: 1,
        maskedDisplayName: 'Hội viên #2***34',
      }),
      'POST /api/v1/reviews/rev-mod-03/reply': json(201, {
        id: 'rep-mod-03',
        reviewId: 'rev-mod-03',
        bodyText: 'Mland xin lỗi và sẽ cải thiện ngay lập tức.',
      }),
    });

    open('/review-moderation/rev-mod-03', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Quản lý đánh giá')).toBeInTheDocument();

    const replyInput = screen.getByPlaceholderText(/Viết phản hồi cho khách hàng/);
    await user.type(replyInput, 'Mland xin lỗi và sẽ cải thiện ngay lập tức.');

    const saveReplyBtn = screen.getByRole('button', { name: 'Lưu phản hồi' });
    await user.click(saveReplyBtn);

    expect(await screen.findByText('Đã lưu phản hồi.')).toBeInTheDocument();

    const replyCall = calls.find(c => c.path === '/api/v1/reviews/rev-mod-03/reply');
    expect(replyCall).toBeDefined();
    expect(replyCall.body).toMatchObject({
      bodyText: 'Mland xin lỗi và sẽ cải thiện ngay lập tức.',
    });
  });

  it('enforces Staff limitations on moderation detail screen', async () => {
    clearRoleCache();
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF', fullName: 'Staff User' }),
      'GET /api/v1/reviews/rev-mod-04': json(200, {
        id: 'rev-mod-04',
        status: 'PUBLIC',
        rating: 2,
        bodyText: 'Chờ đợi quá lâu.',
        version: 1,
        maskedDisplayName: 'Khách #5***67',
      }),
    });

    open('/review-moderation/rev-mod-04', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Quản lý đánh giá')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Ẩn đánh giá' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gỡ đánh giá' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Lưu phản hồi' })).not.toBeInTheDocument();
    expect(screen.getByText(/Chỉ Quản lý \(Manager\) mới có quyền gửi phản hồi chính thức/)).toBeInTheDocument();
  });

  it('shows Manager full moderation and reply capabilities', async () => {
    clearRoleCache();
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'Manager User' }),
      'GET /api/v1/reviews/rev-mod-05': json(200, {
        id: 'rev-mod-05',
        status: 'HIDDEN',
        rating: 1,
        bodyText: 'Đánh giá đã bị ẩn.',
        version: 2,
        maskedDisplayName: 'Khách #9***88',
      }),
    });

    open('/review-moderation/rev-mod-05', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Quản lý đánh giá')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Khôi phục công khai' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gỡ đánh giá' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Lưu phản hồi' })).toBeInTheDocument();
  });

  it('renders review card with attached images and supports toolbar filters', async () => {
    mockFetch({
      'GET /api/v1/reviews': json(200, {
        aggregate: { count: 1, averageRating: 5.0 },
        items: [
          {
            id: 'rev-photo-01',
            targetType: 'WORKSHOP_SESSION',
            targetReference: 'SESS-PHOTO',
            rating: 5,
            bodyText: 'Buổi làm nhẫn kèm ảnh sản phẩm thực tế.',
            imageUrls: ['data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='],
            status: 'PUBLIC',
            maskedDisplayName: 'k***h@gmail.com',
            createdAt: '2026-11-05T12:00:00Z',
          },
        ],
      }),
    });

    open('/reviews', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Buổi làm nhẫn kèm ảnh sản phẩm thực tế.')).toBeInTheDocument();
    expect(screen.getByTestId('review-images')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Review attachment 1' })).toBeInTheDocument();

    expect(screen.getByTestId('filter-sort')).toBeInTheDocument();
    expect(screen.getByTestId('filter-target-type')).toBeInTheDocument();
    expect(screen.getByTestId('filter-rating')).toBeInTheDocument();
  });

  it('validates image attachments in ReviewForm (max 3 photos limit)', async () => {
    const onSaved = vi.fn();
    const source = { sourceType: 'WORKSHOP_ATTENDANCE', sourceReference: 'ATT-1', targetType: 'WORKSHOP_SESSION', targetReference: 'SESS-1' };
    renderRoutes(reviewManagementRoutes, {
      path: '/reviews/new',
      state: { source },
      session: fakeSession('signed-in'),
    });

    const fileInput = await screen.findByTestId('review-image-input');
    const f1 = new File(['a'], '1.png', { type: 'image/png' });
    const f2 = new File(['b'], '2.png', { type: 'image/png' });
    const f3 = new File(['c'], '3.png', { type: 'image/png' });
    const f4 = new File(['d'], '4.png', { type: 'image/png' });

    // Selecting 4 files exceeds max 3
    fireEvent.change(fileInput, { target: { files: [f1, f2, f3, f4] } });
    expect(await screen.findByText('Tối đa 3 hình ảnh cho mỗi đánh giá.')).toBeInTheDocument();
  });
});
