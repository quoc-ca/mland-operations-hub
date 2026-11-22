import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { designReviewRoutes } from '../routes';

const open = (path, options) => renderRoutes(designReviewRoutes, { path, ...options });

describe('Feature-004 & Custom Manufacturing Frontend (Dev C)', () => {
  it('renders Orders tab by default with quote total, generation, and Pay Full Amount button', async () => {
    mockFetch({
      'GET /api/v1/members/me/custom-orders': json(200, {
        items: [
          {
            customOrderReference: 'CO-A1B2C3D4',
            status: 'FULL_PAYMENT_PENDING',
            createdAt: new Date().toISOString(),
            quote: {
              quoteReference: 'QUO-12345678',
              quoteGeneration: 1,
              totalAmount: '15000000',
              currency: 'VND',
              status: 'QUOTE_READY',
              expiresAt: new Date(Date.now() + 86400000).toISOString(),
              approvedByManager: true,
            },
            designSnapshot: {
              model: 'Classic Ring',
              material: 'Vàng 18k',
            },
          },
        ],
      }),
    });

    open('/account/my-custom-orders', { session: fakeSession('signed-in') });

    expect(await screen.findByText('CO-A1B2C3D4')).toBeInTheDocument();
    expect(screen.getByText('15.000.000 đ')).toBeInTheDocument();
    expect(screen.getByText('Phiên bản báo giá: Lần #1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thanh toán toàn bộ/i })).toBeInTheDocument();

    // Invariant checks: NO cart, NO deposit, NO remaining balance
    expect(screen.queryByText(/thêm vào giỏ/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/đặt cọc/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/số dư còn lại/i)).not.toBeInTheDocument();
  });

  it('switches to Requests tab and displays clarification explanation and response action', async () => {
    const user = userEvent.setup();
    mockFetch({
      'GET /api/v1/members/me/custom-orders': json(200, { items: [] }),
      'GET /api/v1/design-requests': json(200, [
        {
          id: 'req-001',
          requestReference: 'DR-REQ001',
          status: 'CLARIFICATION_REQUIRED',
          reviewStatus: 'CLARIFICATION_REQUIRED',
          requestVersion: 1,
          description: 'Mẫu nhẫn hoa văn lá',
          explanation: 'Vui lòng cung cấp thêm kích thước lòng nhẫn chính xác (size mm).',
          decisionExplanation: 'Vui lòng cung cấp thêm kích thước lòng nhẫn chính xác (size mm).',
        },
      ]),
    });

    open('/account/my-custom-orders', { session: fakeSession('signed-in') });

    // Wait for page to render
    expect(await screen.findByText('Đơn hàng cá nhân')).toBeInTheDocument();

    // Click Requests tab
    const requestsTab = screen.getByRole('button', { name: /Yêu cầu thiết kế/i });
    await user.click(requestsTab);

    expect(await screen.findByText('DR-REQ001')).toBeInTheDocument();
    expect(screen.getByText('Cần làm rõ thông tin')).toBeInTheDocument();
    expect(screen.getByText(/Vui lòng cung cấp thêm kích thước lòng nhẫn/i)).toBeInTheDocument();

    const clarifyBtn = screen.getByRole('button', { name: /Phản hồi làm rõ/i });
    expect(clarifyBtn).toBeInTheDocument();

    // Open clarification modal
    await user.click(clarifyBtn);
    expect(screen.getByText(/Phản hồi làm rõ yêu cầu \(DR-REQ001\)/i)).toBeInTheDocument();
  });

  it('renders Request New Quote button when quote is expired', async () => {
    mockFetch({
      'GET /api/v1/members/me/custom-orders': json(200, {
        items: [
          {
            customOrderReference: 'CO-EXPIRED01',
            status: 'QUOTE_EXPIRED',
            createdAt: new Date().toISOString(),
            quote: {
              quoteReference: 'QUO-EXP001',
              quoteGeneration: 1,
              totalAmount: '12000000',
              currency: 'VND',
              status: 'QUOTE_EXPIRED',
              expiresAt: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
            },
          },
        ],
      }),
    });

    open('/account/my-custom-orders', { session: fakeSession('signed-in') });

    expect(await screen.findByText('CO-EXPIRED01')).toBeInTheDocument();
    expect(screen.getByText('Báo giá đã hết hạn')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Yêu cầu báo giá mới/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Thanh toán toàn bộ/i })).not.toBeInTheDocument();
  });

  it('renders CustomOrderDetailPage with complete quote and stepper details', async () => {
    mockFetch({
      'GET /api/v1/members/me/custom-orders/CO-DETAIL99': json(200, {
        customOrderReference: 'CO-DETAIL99',
        status: 'FULL_PAYMENT_PENDING',
        createdAt: new Date().toISOString(),
        quote: {
          quoteReference: 'QUO-DET99',
          quoteGeneration: 2,
          totalAmount: '20000000',
          currency: 'VND',
          status: 'QUOTE_READY',
          expiresAt: new Date(Date.now() + 172800000).toISOString(),
          approvedByManager: true,
          managerReason: 'Đã thẩm duyệt chi tiết kỹ thuật phôi nhẫn.',
        },
        designSnapshot: {
          model: 'Solitaire Gold Ring',
          material: 'Vàng trắng 14k',
          fingerSize: '12',
        },
      }),
    });

    open('/account/my-custom-orders/CO-DETAIL99', { session: fakeSession('signed-in') });

    expect(await screen.findByText('CO-DETAIL99')).toBeInTheDocument();
    expect(screen.getByText('20.000.000 đ')).toBeInTheDocument();
    expect(screen.getByText('Phiên bản báo giá: Lần #2')).toBeInTheDocument();
    expect(screen.getByText(/Đã thẩm duyệt chi tiết kỹ thuật/i)).toBeInTheDocument();
    expect(screen.getByText('Solitaire Gold Ring')).toBeInTheDocument();
    expect(screen.getByText(/Thanh toán toàn bộ 20.000.000 đ qua VNPay/i)).toBeInTheDocument();

    // No cart or balance
    expect(screen.queryByText(/thêm vào giỏ/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/đặt cọc/i)).not.toBeInTheDocument();
  });
});
