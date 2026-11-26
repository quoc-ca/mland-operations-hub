import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { designReviewRoutes } from '../routes';

vi.mock('../../../shared/auth/RouteAccessGuard', () => import('../../../test/passThroughGuard'));

beforeEach(() => {
  clearRoleCache();
});

function simulateScroll(element, { scrollTop = 0, scrollHeight = 600, clientHeight = 250 } = {}) {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, value: scrollHeight });
  Object.defineProperty(element, 'clientHeight', { configurable: true, value: clientHeight });
  Object.defineProperty(element, 'scrollTop', { configurable: true, value: scrollTop });
  fireEvent.scroll(element);
}

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

  it('displays quota for guest and updates remaining quota after prompt', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/ai-support/messages': json(200, {
        message: 'Mland có workshop cuối tuần.',
        scope: 'WORKSHOP',
        correlationId: 'corr-q1',
        quota: {
          actor: 'GUEST',
          limit: 3,
          remaining: 2,
          resetAt: '2026-11-19T00:00:00+07:00',
        },
      }),
    });

    open('/ai-support', { session: fakeSession('signed-out') });
    expect(await screen.findByText(/Còn 3\/3 lượt hỏi hôm nay/)).toBeInTheDocument();

    const textarea = screen.getByLabelText(/Nhập câu hỏi của bạn/);
    await user.type(textarea, 'Có workshop nào?');
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));

    expect(await screen.findByText(/Còn 2\/3 lượt hỏi hôm nay/)).toBeInTheDocument();
  });

  it('disables input and displays warning when daily quota is exceeded (429)', async () => {
    const user = userEvent.setup();
    mockFetch({
      'POST /api/v1/ai-support/messages': json(429, {
        code: 'AI_DAILY_QUOTA_EXCEEDED',
        message: 'Bạn đã sử dụng hết số lượt hỏi AI trong ngày.',
        quota: {
          actor: 'GUEST',
          limit: 3,
          remaining: 0,
          resetAt: '2026-11-19T00:00:00+07:00',
        },
      }),
    });

    open('/ai-support', { session: fakeSession('signed-out') });
    const textarea = screen.getByLabelText(/Nhập câu hỏi của bạn/);
    await user.type(textarea, 'Câu hỏi vượt hạn mức');
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));

    expect(await screen.findByText(/Lượt hỏi sẽ được làm mới vào lúc 00:00/)).toBeInTheDocument();
    expect(textarea).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled();
  });
});

describe('F04-DESIGN-WORKSPACE Screen (/design-requests/new)', () => {
  it('requires Member session for guests attempting to access workspace', async () => {
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-out') });
    expect(await screen.findByText('Vui lòng đăng nhập để tạo yêu cầu thiết kế tùy chỉnh')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('renders design workspace and validates empty input', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-in') });

    expect(await screen.findByText(/Biến ý tưởng của bạn thành hiện thực/)).toBeInTheDocument();

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
    expect(await screen.findByRole('heading', { level: 3, name: /Chấp thuận/i })).toBeInTheDocument();
    const submitBtn = screen.getByRole('button', { name: 'Gửi yêu cầu thiết kế' });
    expect(submitBtn).toBeDisabled();

    // Check consent checkbox - opens terms popup
    const consentCheckbox = screen.getByRole('checkbox');
    await user.click(consentCheckbox);

    // Terms modal appears
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Điều khoản Sử dụng Hình ảnh & Miễn trừ/i })).toBeInTheDocument();

    const acceptBtn = screen.getByRole('button', { name: 'Chấp nhận' });
    const declineBtn = screen.getByRole('button', { name: 'Từ chối' });
    expect(acceptBtn).toBeDisabled();
    expect(declineBtn).toBeDisabled();

    // Scroll to bottom of terms
    const termsBox = screen.getByTestId('terms-scroll-box');
    simulateScroll(termsBox, { scrollTop: 500, scrollHeight: 600, clientHeight: 250 });
    expect(acceptBtn).not.toBeDisabled();
    expect(declineBtn).not.toBeDisabled();

    // Accept terms
    await user.click(acceptBtn);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(submitBtn).not.toBeDisabled();

    await user.click(submitBtn);

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/design-requests/req-new-999');
    });

    expect(await screen.findByText('Yêu cầu đã được gửi thành công, đang đợi phản hồi của nhân viên')).toBeInTheDocument();

    const postCall = calls.find(c => c.path === '/api/v1/design-requests');
    expect(postCall).toBeDefined();
    expect(postCall.headers['Idempotency-Key']).toBeDefined();
  });

  it('does not render catalogue reference input and validates invalid image files', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-in') });

    // 1. First requirement: Catalogue reference input is removed
    expect(screen.queryByPlaceholderText(/RING-SILVER-CLASSIC-01/)).not.toBeInTheDocument();

    // 2. Third requirement: Validate empty image file (0 bytes)
    const fileInput = screen.getByLabelText(/Hình ảnh tham khảo mẫu thiết kế/);
    const emptyFile = new File([], 'empty.png', { type: 'image/png' });
    await user.upload(fileInput, emptyFile);

    expect(await screen.findByText(/Tệp ảnh rỗng hoặc bị lỗi/)).toBeInTheDocument();
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

    // Accept via modal
    const termsBox = await screen.findByTestId('terms-scroll-box');
    simulateScroll(termsBox, { scrollTop: 500, scrollHeight: 600, clientHeight: 250 });
    await user.click(screen.getByRole('button', { name: 'Chấp nhận' }));

    const submitBtn = screen.getByRole('button', { name: 'Gửi yêu cầu thiết kế' });
    await user.click(submitBtn);

    expect(await screen.findByText(/Bạn đã gửi ảnh quá số lần cho phép/)).toBeInTheDocument();
    // Form data is preserved
    expect(descInput).toHaveValue('Nhẫn vàng trắng 18k.');
  });

  it('enforces scrolling to bottom before enabling Accept and Decline buttons in terms modal', async () => {
    const user = userEvent.setup();
    mockFetch({});
    open('/design-requests/new', { session: fakeSession('signed-in') });

    const file = new File(['image-bytes'], 'ring.png', { type: 'image/png' });
    const fileInput = screen.getByLabelText(/Hình ảnh tham khảo mẫu thiết kế/);
    await user.upload(fileInput, file);

    const openTermsBtn = await screen.findByRole('button', { name: /Đọc & Chấp thuận Điều khoản/i });
    await user.click(openTermsBtn);

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Điều khoản Sử dụng Hình ảnh & Miễn trừ/i })).toBeInTheDocument();
    expect(screen.getByTestId('scroll-warning')).toBeInTheDocument();

    const acceptBtn = screen.getByRole('button', { name: 'Chấp nhận' });
    const declineBtn = screen.getByRole('button', { name: 'Từ chối' });
    expect(acceptBtn).toBeDisabled();
    expect(declineBtn).toBeDisabled();

    // Partial scroll (not reaching bottom)
    const termsBox = screen.getByTestId('terms-scroll-box');
    simulateScroll(termsBox, { scrollTop: 50, scrollHeight: 600, clientHeight: 250 });
    expect(acceptBtn).toBeDisabled();
    expect(declineBtn).toBeDisabled();

    // Full scroll to bottom
    simulateScroll(termsBox, { scrollTop: 400, scrollHeight: 600, clientHeight: 250 });
    expect(screen.getByTestId('scroll-success')).toBeInTheDocument();
    expect(acceptBtn).not.toBeDisabled();
    expect(declineBtn).not.toBeDisabled();
  });

  it('navigates back to previous screen when user declines terms in popup after scrolling', async () => {
    const user = userEvent.setup();
    mockFetch({});
    const { router } = open('/design-requests/new', { session: fakeSession('signed-in') });

    const file = new File(['image-bytes'], 'ring.png', { type: 'image/png' });
    const fileInput = screen.getByLabelText(/Hình ảnh tham khảo mẫu thiết kế/);
    await user.upload(fileInput, file);

    const openTermsBtn = await screen.findByRole('button', { name: /Đọc & Chấp thuận Điều khoản/i });
    await user.click(openTermsBtn);

    const termsBox = await screen.findByTestId('terms-scroll-box');
    simulateScroll(termsBox, { scrollTop: 400, scrollHeight: 600, clientHeight: 250 });

    const declineBtn = screen.getByRole('button', { name: 'Từ chối' });
    expect(declineBtn).not.toBeDisabled();

    await user.click(declineBtn);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('F04-ANALYSIS-STATUS & F04-RESULT Screen (/design-requests/:id)', () => {
  it('displays human review status, description, and reviewer feedback without AI', async () => {
    mockFetch({
      'GET /api/v1/design-requests/req-123': json(200, {
        id: 'req-123',
        requestReference: 'DR-REF-123',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        description: 'Nhẫn bạc đính đá topaz',
        decisionExplanation: 'Thiết kế nhẫn hoa văn rất đẹp, độ dày 1.8mm hoàn toàn phù hợp để gia công.',
        images: [
          {
            imageReference: 'IMG-001',
            originalFilename: 'topaz-ring.jpg',
            fileSizeBytes: 204800,
          },
        ],
      }),
    });

    open('/design-requests/req-123', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Chi tiết Yêu cầu Thiết kế')).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: DR-REF-123')).toBeInTheDocument();
    expect(screen.getByText('Đã chấp nhận thiết kế')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn bạc đính đá topaz')).toBeInTheDocument();
    expect(screen.getByText('Thiết kế nhẫn hoa văn rất đẹp, độ dày 1.8mm hoàn toàn phù hợp để gia công.')).toBeInTheDocument();
    expect(screen.getByText('topaz-ring.jpg')).toBeInTheDocument();
  });

  it('requires Member session when guest attempts to view request details without login', async () => {
    mockFetch({});
    open('/design-requests/guest-req-1?token=token-guest-xyz', { session: fakeSession('signed-out') });

    expect(await screen.findByText('Vui lòng đăng nhập để xem tiến độ yêu cầu thiết kế.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
  });
});

describe('F04-REVIEW-QUEUE Screen (/design-review-requests)', () => {
  it('renders queue items from envelope { items } and handles empty queue', async () => {
    mockFetch({
      'GET /api/v1/design-review-requests': json(200, {
        items: [
          {
            id: 'rev-01',
            requestReference: 'DR-001',
            requestVersion: 1,
            reviewStatus: 'NEED_REVIEW',
            catalogueReference: 'CAT-01',
          },
        ],
      }),
    });

    open('/design-review-requests', { session: fakeSession('signed-in') });

    expect(await screen.findByText(/Danh sách các yêu cầu thiết kế đang chờ nhân sự thẩm duyệt/)).toBeInTheDocument();
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
      requestVersion: 1,
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

  it('prompts confirmation popup when staff sends quote, hides the button after sending and shows success message', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/design-requests/rev-quote-01': json(200, {
        id: 'rev-quote-01',
        requestReference: 'DR-Q01',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        ownershipType: 'MEMBER',
        description: 'Mẫu nhẫn đính kim cương tự nhiên',
      }),
      'POST /api/v1/custom-design-requests/rev-quote-01/quote': json(200, {
        quoteReference: 'QUO-88889999',
        totalAmount: '800000',
        currency: 'VND',
        status: 'QUOTE_PENDING',
        quoteGeneration: 1,
        approvedByManager: false,
        managerReason: 'Vàng 18k và kim cương 4.5mm',
      }),
    });

    open('/design-review-requests/rev-quote-01', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Báo giá Gia công Tùy chỉnh (Custom Quote)')).toBeInTheDocument();

    const amountInput = screen.getByTestId('quote-amount-input');
    await user.type(amountInput, '800000');

    const reasonInput = screen.getByPlaceholderText(/Ghi chú cơ cấu vật liệu/);
    await user.type(reasonInput, 'Vàng 18k và kim cương 4.5mm');

    const submitQuoteBtn = screen.getByRole('button', { name: 'Gửi báo giá cho Manager duyệt' });
    await user.click(submitQuoteBtn);

    // Confirmation dialog must appear
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Xác nhận gửi báo giá cho Quản lý')).toBeInTheDocument();
    expect(within(dialog).getByText(/800\.000 đ/)).toBeInTheDocument();

    // Confirm sending
    const confirmBtn = within(dialog).getByRole('button', { name: 'Xác nhận gửi' });
    await user.click(confirmBtn);

    // Success notice displayed
    expect(await screen.findByText(/Gửi báo giá cho Quản lý \(Manager\) phê duyệt thành công!/)).toBeInTheDocument();
    expect(screen.getByText('QUO-88889999')).toBeInTheDocument();
    expect(screen.getByText(/Chờ Quản lý \(Manager\) duyệt/)).toBeInTheDocument();

    // The submit button must disappear so staff cannot click again
    expect(screen.queryByRole('button', { name: 'Gửi báo giá cho Manager duyệt' })).toBeNull();
  });

  it('shows error banner when quote submission fails and keeps button for retry', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/design-requests/rev-quote-err': json(200, {
        id: 'rev-quote-err',
        requestReference: 'DR-QERR',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        ownershipType: 'MEMBER',
        description: 'Mẫu nhẫn hoa văn chạm trổ',
      }),
      'POST /api/v1/custom-design-requests/rev-quote-err/quote': json(500, {
        code: 'INTERNAL_ERROR',
        message: 'Lỗi máy chủ khi tạo báo giá.',
      }),
    });

    open('/design-review-requests/rev-quote-err', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Báo giá Gia công Tùy chỉnh (Custom Quote)')).toBeInTheDocument();

    const amountInput = screen.getByTestId('quote-amount-input');
    await user.type(amountInput, '500000');

    const submitQuoteBtn = screen.getByRole('button', { name: 'Gửi báo giá cho Manager duyệt' });
    await user.click(submitQuoteBtn);

    const dialog = await screen.findByRole('dialog');
    const confirmBtn = within(dialog).getByRole('button', { name: 'Xác nhận gửi' });
    await user.click(confirmBtn);

    expect(await screen.findByText(/✕ Thất bại:/)).toBeInTheDocument();
    // Button is still present for retrying
    expect(screen.getByRole('button', { name: 'Gửi báo giá cho Manager duyệt' })).toBeInTheDocument();
  });

  it('validates quote amount <= 1000000000 and duration 24-72 hours on ReviewDetailPage', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }),
      'GET /api/v1/design-requests/rev-validate-01': json(200, {
        id: 'rev-validate-01',
        requestReference: 'DR-V01',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        ownershipType: 'MEMBER',
        description: 'Mẫu nhẫn test validation',
      }),
    });

    open('/design-review-requests/rev-validate-01', { session: fakeSession('signed-in') });

    expect(await screen.findByText('Báo giá Gia công Tùy chỉnh (Custom Quote)')).toBeInTheDocument();

    const amountInput = screen.getByTestId('quote-amount-input');
    const hoursInput = screen.getByTestId('quote-hours-input');

    // Amount > 1000000000 shows error
    await user.type(amountInput, '1500000000');
    expect(screen.getByTestId('quote-amount-error')).toHaveTextContent('Số tiền tối đa không được vượt quá 1.000.000.000 VNĐ.');

    // Clear and type valid amount
    await user.clear(amountInput);
    await user.type(amountInput, '50000000');
    expect(screen.queryByTestId('quote-amount-error')).toBeNull();

    // Hours < 24 shows error
    await user.clear(hoursInput);
    await user.type(hoursInput, '12');
    expect(screen.getByTestId('quote-hours-error')).toHaveTextContent('Thời hạn thanh toán chỉ được phép từ 24 đến 72 giờ.');

    // Hours > 72 shows error
    await user.clear(hoursInput);
    await user.type(hoursInput, '100');
    expect(screen.getByTestId('quote-hours-error')).toHaveTextContent('Thời hạn thanh toán chỉ được phép từ 24 đến 72 giờ.');

    // Valid hours
    await user.clear(hoursInput);
    await user.type(hoursInput, '48');
    expect(screen.queryByTestId('quote-hours-error')).toBeNull();
  });

  it('allows Manager to approve pending quote on ReviewDetailPage', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/design-requests/rev-quote-mgr': json(200, {
        id: 'rev-quote-mgr',
        requestReference: 'DR-QMGR',
        requestVersion: 1,
        requestStatus: 'ACTIVE',
        reviewStatus: 'ACCEPTED',
        ownershipType: 'MEMBER',
        quoteReference: 'QUO-PENDING-01',
        description: 'Mẫu nhẫn cưới vàng hồng',
      }),
      'GET /api/v1/custom-design-requests/rev-quote-mgr/quote': json(200, {
        quoteReference: 'QUO-PENDING-01',
        totalAmount: '25000000',
        currency: 'VND',
        status: 'QUOTE_PENDING',
        quoteGeneration: 1,
        approvedByManager: false,
        managerReason: 'Chất liệu vàng hồng 18k đính 3 viên kim cương tấm',
      }),
      'POST /api/v1/custom-design-requests/rev-quote-mgr/quote/approve': json(200, {
        quoteReference: 'QUO-PENDING-01',
        totalAmount: '25000000',
        currency: 'VND',
        status: 'QUOTE_READY',
        quoteGeneration: 1,
        approvedByManager: true,
        managerReason: 'Quản lý phê duyệt báo giá',
      }),
    });

    open('/design-review-requests/rev-quote-mgr', { session: fakeSession('signed-in') });

    expect(await screen.findByText('QUO-PENDING-01')).toBeInTheDocument();
    expect(await screen.findByText(/25\.000\.000 đ/)).toBeInTheDocument();
    expect(screen.getByText(/Chờ Quản lý \(Manager\) duyệt/)).toBeInTheDocument();

    const approveBtn = await screen.findByRole('button', { name: 'Phê duyệt Báo giá (Manager Approve)' });
    await user.click(approveBtn);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Xác nhận phê duyệt báo giá')).toBeInTheDocument();

    const confirmApproveBtn = within(dialog).getByRole('button', { name: 'Phê duyệt báo giá' });
    await user.click(confirmApproveBtn);

    expect(await screen.findByText(/Đã phê duyệt báo giá thành công!/)).toBeInTheDocument();
    expect(screen.getByText(/Quản lý đã duyệt/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Phê duyệt Báo giá (Manager Approve)' })).toBeNull();
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
