import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { designReviewRoutes } from '../routes';

const open = (path, options) => renderRoutes(designReviewRoutes, { path, ...options });

describe('F04-AI-CHAT Screen (/ai-support)', () => {
  it('allows Guest to ask AI support and displays response', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/ai-support/messages': json(200, {
        message: 'Mland cung cấp các buổi workshop làm nhẫn bạc thủ công.',
        scope: 'GUEST',
        providerCorrelationId: 'corr-ai-1',
      }),
    });

    open('/ai-support', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Khách (Công khai)')).toBeInTheDocument();
    expect(screen.getByText(/Lưu ý: Phản hồi của AI chỉ mang tính chất tham khảo/)).toBeInTheDocument();

    const textarea = screen.getByLabelText(/Nhập câu hỏi của bạn/);
    await user.type(textarea, 'Mland có workshop không?');
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));

    expect(await screen.findByText('Mland cung cấp các buổi workshop làm nhẫn bạc thủ công.')).toBeInTheDocument();
  });

  it('displays error and allows retry when AI service fails', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    mockFetch({
      'POST /api/v1/ai-support/messages': () => {
        attempt++;
        if (attempt === 1) {
          return json(503, { code: 'AI_PROVIDER_ERROR', message: 'Provider timeout.' });
        }
        return json(200, {
          message: 'Chào bạn, Mland hỗ trợ lại rồi!',
          scope: 'GUEST',
          providerCorrelationId: 'corr-ai-2',
        });
      },
    });

    open('/ai-support', { session: fakeSession('signed-out') });
    const textarea = screen.getByLabelText(/Nhập câu hỏi của bạn/);
    await user.type(textarea, 'Xin chào');
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));

    expect(await screen.findByText(/Dịch vụ phân tích AI đang gặp sự cố kết nối/)).toBeInTheDocument();

    // Click retry
    const retryBtn = screen.getByRole('button', { name: 'Thử lại' });
    await user.click(retryBtn);

    expect(await screen.findByText('Chào bạn, Mland hỗ trợ lại rồi!')).toBeInTheDocument();
  });
});

describe('F04-DESIGN-WORKSPACE Screen (/design-requests/new)', () => {
  it('requires session when signed out', async () => {
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Bạn cần đăng nhập để tiếp tục.')).toBeInTheDocument();
  });

  it('renders independent boundary notice and validates empty input', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-in') });

    expect(await screen.findByText(/Yêu cầu thiết kế độc lập không tạo, liên kết hay ràng buộc với mã đặt chỗ workshop/)).toBeInTheDocument();

    const submitBtn = screen.getByRole('button', { name: 'Gửi yêu cầu thiết kế' });
    await user.click(submitBtn);

    expect(await screen.findByText('Vui lòng nhập mô tả ý tưởng hoặc tải lên hình ảnh tham khảo.')).toBeInTheDocument();
  });

  it('requires affirmative consent when image is selected and submits multipart request', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'POST /api/v1/design-requests': json(202, {
        id: 'req-new-999',
        requestReference: 'DR-999',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        analysisStatus: 'SUCCEEDED',
        reviewStatus: 'NEED_REVIEW',
      }),
      'GET /api/v1/design-requests/req-new-999': json(200, {
        id: 'req-new-999',
        requestReference: 'DR-999',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        analysisStatus: 'SUCCEEDED',
        reviewStatus: 'NEED_REVIEW',
        candidateFeatures: { style: 'Vintage Silver' },
      }),
    });

    const { router } = open('/design-requests/new', { session: fakeSession('signed-in') });

    const descInput = await screen.findByPlaceholderText(/Mô tả chi tiết ý tưởng nhẫn/);
    await user.type(descInput, 'Nhẫn đôi đính đá sapphire tự nhiên.');

    const file = new File(['image-bytes'], 'design.png', { type: 'image/png' });
    const fileInput = screen.getByLabelText(/Hình ảnh tham khảo mẫu thiết kế/);
    await user.upload(fileInput, file);

    // Consent box must appear
    expect(await screen.findByText('Chấp thuận Xử lý Hình ảnh Tham khảo')).toBeInTheDocument();
    const submitBtn = screen.getByRole('button', { name: 'Gửi yêu cầu thiết kế' });
    expect(submitBtn).toBeDisabled();

    // Check consent checkbox
    const consentCheckbox = screen.getByRole('checkbox');
    await user.click(consentCheckbox);
    expect(submitBtn).not.toBeDisabled();

    await user.click(submitBtn);

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/design-requests/req-new-999');
    });

    const postCall = calls.find(c => c.path === '/api/v1/design-requests');
    expect(postCall).toBeDefined();
    expect(postCall.headers['Idempotency-Key']).toBeDefined();
  });

  it('displays rate-limit error (429) when image rate limit is exceeded', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/design-requests': json(429, {
        code: 'IMAGE_RATE_LIMITED',
        message: 'Rate limit exceeded.',
      }, { 'Retry-After': '300' }),
    });

    open('/design-requests/new', { session: fakeSession('signed-in') });

    const descInput = await screen.findByPlaceholderText(/Mô tả chi tiết ý tưởng nhẫn/);
    await user.type(descInput, 'Nhẫn vàng trắng 18k.');

    const file = new File(['image-bytes'], 'gold.png', { type: 'image/png' });
    const fileInput = screen.getByLabelText(/Hình ảnh tham khảo mẫu thiết kế/);
    await user.upload(fileInput, file);

    const consentCheckbox = await screen.findByRole('checkbox');
    await user.click(consentCheckbox);

    const submitBtn = screen.getByRole('button', { name: 'Gửi yêu cầu thiết kế' });
    await user.click(submitBtn);

    expect(await screen.findByText(/Bạn đã gửi ảnh quá số lần cho phép/)).toBeInTheDocument();
    // Form data is preserved
    expect(descInput).toHaveValue('Nhẫn vàng trắng 18k.');
  });
});

describe('F04-ANALYSIS-STATUS & F04-RESULT Screen (/design-requests/:id)', () => {
  it('displays safe candidate features, confidence, explanation without raw payload', async () => {
    mockFetch({
      'GET /api/v1/design-requests/req-123': json(200, {
        id: 'req-123',
        requestReference: 'DR-REF-123',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        analysisStatus: 'SUCCEEDED',
        reviewStatus: 'NEED_REVIEW',
        nextAction: 'AWAITING_STAFF_REVIEW',
        description: 'Nhẫn bạc đính đá topaz',
        candidateFeatures: {
          style: 'Solitaire Classic',
          metal: '925 Sterling Silver',
          gemstone: 'Blue Topaz',
          estimatedValue: 1800000,
        },
        imageQuality: {
          lighting: 'Good',
          sharpness: 'High',
        },
        confidence: {
          styleConfidence: 0.92,
        },
        safeExplanation: 'Thiết kế nhẫn phong cách Solitaire đơn giản với đá chủ topaz.',
      }),
    });

    open('/design-requests/req-123', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Chi tiết Yêu cầu Thiết kế')).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: DR-REF-123')).toBeInTheDocument();
    expect(screen.getByText('Trạng thái phân tích AI: SUCCEEDED')).toBeInTheDocument();
    expect(screen.getByText('Solitaire Classic')).toBeInTheDocument();
    expect(screen.getByText('925 Sterling Silver')).toBeInTheDocument();
    expect(screen.getByText(/1\.800\.000/)).toBeInTheDocument();
    expect(screen.getByText('Thiết kế nhẫn phong cách Solitaire đơn giản với đá chủ topaz.')).toBeInTheDocument();
  });
});

describe('F04-REVIEW-QUEUE Screen (/design-review-requests)', () => {
  it('renders queue items and handles empty queue', async () => {
    mockFetch({
      'GET /api/v1/design-review-requests': json(200, [
        {
          id: 'rev-01',
          requestReference: 'DR-001',
          requestVersion: 1,
          reviewStatus: 'NEED_REVIEW',
          catalogueReference: 'CAT-01',
        },
      ]),
    });

    open('/design-review-requests', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Hàng đợi Thẩm duyệt Thiết kế')).toBeInTheDocument();
    expect(screen.getByText('rev-01')).toBeInTheDocument();
    expect(screen.getByText('DR-001')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Thẩm duyệt' })).toHaveAttribute('href', '/design-review-requests/rev-01');
  });

  it('renders empty queue state when no requests exist', async () => {
    mockFetch({
      'GET /api/v1/design-review-requests': json(200, []),
    });

    open('/design-review-requests', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Hiện không có yêu cầu nào đang chờ thẩm duyệt.')).toBeInTheDocument();
  });
});

describe('F04-REVIEW-DETAIL Screen (/design-review-requests/:id)', () => {
  it('submits human review decision with reason and optimistic designVersion', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/design-requests/rev-01': json(200, {
        id: 'rev-01',
        requestReference: 'DR-001',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'NEED_REVIEW',
        description: 'Mẫu nhẫn hoa mai',
        candidateFeatures: { style: 'Floral' },
      }),
      'POST /api/v1/design-review-requests/rev-01/decisions': json(200, {
        id: 'rev-01',
        requestReference: 'DR-001',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        description: 'Mẫu nhẫn hoa mai',
      }),
    });

    open('/design-review-requests/rev-01', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Thẩm duyệt Yêu cầu Thiết kế #rev-01')).toBeInTheDocument();

    const reasonInput = screen.getByPlaceholderText(/Nhập lý do chi tiết/);
    await user.type(reasonInput, 'Mẫu hoa mai tinh xảo, đủ điều kiện gia công.');

    const submitBtn = screen.getByRole('button', { name: 'Gửi quyết định' });
    await user.click(submitBtn);

    // Confirm dialog
    const dialog = await screen.findByRole('dialog');
    const confirmBtn = within(dialog).getByRole('button', { name: 'Gửi quyết định' });
    await user.click(confirmBtn);

    expect(await screen.findByText('Đã ghi nhận quyết định thẩm duyệt thành công.')).toBeInTheDocument();

    const decisionCall = calls.find(c => c.path === '/api/v1/design-review-requests/rev-01/decisions');
    expect(decisionCall).toBeDefined();
    expect(decisionCall.body).toEqual({
      decision: 'ACCEPTED',
      designVersion: 1,
      reason: 'Mẫu hoa mai tinh xảo, đủ điều kiện gia công.',
    });
  });

  it('displays 403 error when reviewer is the creator (self-review)', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/design-requests/rev-02': json(200, {
        id: 'rev-02',
        requestReference: 'DR-002',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'NEED_REVIEW',
        description: 'Mẫu nhẫn chính tôi tạo',
      }),
      'POST /api/v1/design-review-requests/rev-02/decisions': json(403, {
        code: 'SELF_REVIEW_NOT_ALLOWED',
        message: 'Cannot review own request.',
      }),
    });

    open('/design-review-requests/rev-02', { session: fakeSession('signed-in') });

    const reasonInput = await screen.findByPlaceholderText(/Nhập lý do chi tiết/);
    await user.type(reasonInput, 'Duyệt thử chính mình.');

    const submitBtn = screen.getByRole('button', { name: 'Gửi quyết định' });
    await user.click(submitBtn);

    const dialog = await screen.findByRole('dialog');
    const confirmBtn = within(dialog).getByRole('button', { name: 'Gửi quyết định' });
    await user.click(confirmBtn);

    expect(await screen.findByText(/Bạn không thể tự thẩm duyệt yêu cầu thiết kế do chính mình tạo ra/)).toBeInTheDocument();
  });
});

describe('F04-PRICE-PROPOSAL Screen (/price-proposals/:id/ai-suggestions)', () => {
  it('displays Manager view with suggested price and decision form', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      'GET /api/v1/price-proposals/prop-pkg-01/ai-suggestions': json(200, {
        id: 1,
        proposalId: 'prop-pkg-01',
        targetType: 'WORKSHOP_PACKAGE',
        advisory: true,
        suggestionStatus: 'COMPLETED',
        suggestedPrice: 2500000,
        explanation: 'Dựa trên chi phí phôi bạc 925 và thời lượng workshop 3 tiếng.',
        technicalAuditEvidenceOnly: false,
      }),
      'POST /api/v1/price-proposals/prop-pkg-01/ai-suggestions/decisions': json(200, {
        id: 1,
        proposalId: 'prop-pkg-01',
        targetType: 'WORKSHOP_PACKAGE',
        advisory: true,
        suggestionStatus: 'COMPLETED',
        suggestedPrice: 2500000,
        managerDecision: 'ACCEPTED',
        finalPrice: 2500000,
        decidedAt: '2026-11-01T10:00:00Z',
        technicalAuditEvidenceOnly: false,
      }),
    });

    open('/price-proposals/prop-pkg-01/ai-suggestions', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Tư vấn Giá AI cho Đề xuất Mới')).toBeInTheDocument();
    expect(screen.getByText(/2\.500\.000/)).toBeInTheDocument();
    expect(screen.getByText(/Dựa trên chi phí phôi bạc 925/)).toBeInTheDocument();

    const submitDecisionBtn = screen.getByRole('button', { name: 'Lưu quyết định giá' });
    await user.click(submitDecisionBtn);

    expect(await screen.findByText('Đã ghi nhận quyết định giá thành công.')).toBeInTheDocument();
  });

  it('displays Admin view hiding commercial pricing details', async () => {
    mockFetch({
      'GET /api/v1/price-proposals/prop-pkg-02/ai-suggestions': json(200, {
        id: 2,
        proposalId: 'prop-pkg-02',
        targetType: 'RETAIL_PRODUCT',
        advisory: true,
        suggestionStatus: 'COMPLETED',
        suggestedPrice: null,
        finalPrice: null,
        providerCorrelationId: 'prov-corr-admin-99',
        technicalAuditEvidenceOnly: true,
      }),
    });

    open('/price-proposals/prop-pkg-02/ai-suggestions', { session: fakeSession('signed-in') });

    expect(await screen.findByText(/Chế độ xem kiểm toán kỹ thuật \(Admin\)/)).toBeInTheDocument();
    expect(screen.getByText('prov-corr-admin-99')).toBeInTheDocument();
    // Commercial price and decision form must NOT be shown
    expect(screen.queryByText('Giá đề xuất bởi AI:')).toBeNull();
    expect(screen.queryByText('Lưu quyết định giá')).toBeNull();
  });
});
