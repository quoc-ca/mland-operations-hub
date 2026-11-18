import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { reviewManagementRoutes } from '../routes';

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

describe('F11-GUEST-LOOKUP Screen (/reviews/guest/lookup)', () => {
  it('allows Guest to search eligible sources by email and receive opaque handle', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/review-eligibility/lookup': json(200, {
        eligibilityHandle: 'opaque-guest-handle-xyz',
        maskedContact: 't***t@example.com',
        items: [
          {
            sourceType: 'WORKSHOP_ATTENDANCE',
            sourceReference: 'TICK-901',
            targetType: 'WORKSHOP_SESSION',
            targetReference: 'SESS-101',
            label: 'Buổi làm nhẫn hoa mai',
            eligible: true,
          },
        ],
      }),
    });

    open('/reviews/guest/lookup', { session: fakeSession('signed-out') });

    expect(await screen.findByText('Tìm giao dịch đủ điều kiện')).toBeInTheDocument();

    const input = screen.getByLabelText('Email hoặc số điện thoại');
    await user.type(input, 'test@example.com');

    const submitBtn = screen.getByRole('button', { name: 'Tìm nguồn' });
    await user.click(submitBtn);

    expect(await screen.findByText('Buổi làm nhẫn hoa mai')).toBeInTheDocument();
    expect(screen.getByText('Nguồn đã xác minh')).toBeInTheDocument();
  });

  it('displays empty state when guest has no eligible sources', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/review-eligibility/lookup': json(200, {
        eligibilityHandle: null,
        items: [],
      }),
    });

    open('/reviews/guest/lookup', { session: fakeSession('signed-out') });
    const input = screen.getByLabelText('Email hoặc số điện thoại');
    await user.type(input, 'unknown@example.com');
    await user.click(screen.getByRole('button', { name: 'Tìm nguồn' }));

    expect(await screen.findByText('Không có nguồn đủ điều kiện.')).toBeInTheDocument();
  });
});

describe('F11-ELIGIBLE-SOURCES Screen (/reviews/eligible-sources)', () => {
  it('requires session when signed out', async () => {
    mockFetch({});
    open('/reviews/eligible-sources', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Bạn cần đăng nhập để tiếp tục.')).toBeInTheDocument();
  });

  it('renders Member eligible sources when signed in', async () => {
    mockFetch({
      'GET /api/v1/reviews/eligible-sources': json(200, {
        items: [
          {
            sourceType: 'RETAIL_ORDER_LINE',
            sourceReference: 'ITEM-888',
            targetType: 'RETAIL_PRODUCT',
            targetReference: 'PROD-777',
            label: 'Nhẫn bạc đính đá Topaz',
            eligible: true,
          },
        ],
      }),
    });

    open('/reviews/eligible-sources', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Các sản phẩm, trải nghiệm có thể đánh giá')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn bạc đính đá Topaz')).toBeInTheDocument();
    expect(screen.getByText('ITEM-888')).toBeInTheDocument();
  });

  it('renders already reviewed status and view details button when source already has a review', async () => {
    mockFetch({
      'GET /api/v1/reviews/eligible-sources': json(200, {
        sources: [
          {
            sourceType: 'RETAIL_ORDER_LINE',
            sourceReference: 'ITEM-888',
            targetType: 'RETAIL_PRODUCT',
            targetReference: 'PROD-777',
            label: 'Nhẫn bạc đính đá Topaz',
            eligible: true,
            alreadyReviewed: true,
            reviewId: 'rev-existing-123',
          },
        ],
      }),
    });

    open('/reviews/eligible-sources', { session: fakeSession('signed-in') });
    expect(await screen.findByText('Các sản phẩm, trải nghiệm có thể đánh giá')).toBeInTheDocument();
    expect(screen.getByText('Đã viết đánh giá')).toBeInTheDocument();
    expect(screen.getByText(/Xem chi tiết/i)).toBeInTheDocument();
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

    expect(await screen.findByText('Quản lý đánh giá')).toBeInTheDocument();
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
