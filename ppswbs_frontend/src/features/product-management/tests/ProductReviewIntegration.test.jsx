import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import ProductReviewSection from '../ui/public/ProductReviewSection';

describe('Feature-011 Product Review Integration on Product Detail Page', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const mockReviews = [
    {
      id: 'rev-1',
      targetType: 'RETAIL_PRODUCT',
      targetReference: '101',
      rating: 5,
      maskedDisplayName: 'Nguyễn V***',
      bodyText: 'Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!',
      status: 'PUBLIC',
      createdAt: '2026-11-14T10:00:00Z',
    },
    {
      id: 'rev-2',
      targetType: 'RETAIL_PRODUCT',
      targetReference: '101',
      rating: 3,
      maskedDisplayName: 'Trần H***',
      bodyText: 'Nhẫn đẹp vừa tay nhưng hộp đựng hơi móp.',
      status: 'PUBLIC',
      createdAt: '2026-11-14T09:00:00Z',
    },
    {
      id: 'rev-3',
      targetType: 'RETAIL_PRODUCT',
      targetReference: '101',
      rating: 1,
      maskedDisplayName: 'Lê M***',
      bodyText: 'Thời gian giao hàng hơi lâu so với dự kiến.',
      status: 'PUBLIC',
      createdAt: '2026-11-14T08:00:00Z',
    },
  ];

  it('renders reviews for product 101 with aggregated score and controls', async () => {
    mockFetch({
      'GET /api/v1/reviews': () =>
        json(200, {
          items: mockReviews,
          aggregate: { count: 3, averageRating: 3.0 },
        }),
    });

    renderRoutes(
      [
        {
          path: '/test-reviews',
          element: <ProductReviewSection productId={101} productName="Nhẫn Bạc Ý Đính Đá Moissanite" />,
        },
      ],
      { path: '/test-reviews' }
    );

    expect(await screen.findByTestId('product-reviews-section')).toBeInTheDocument();
    expect(screen.getByText(/Nhẫn Bạc Ý Đính Đá Moissanite/)).toBeInTheDocument();
    expect(screen.getByText(/3 đánh giá công khai/)).toBeInTheDocument();
    expect(screen.getByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn đẹp vừa tay nhưng hộp đựng hơi móp.')).toBeInTheDocument();
    expect(screen.getByText('Thời gian giao hàng hơi lâu so với dự kiến.')).toBeInTheDocument();
  });

  it('triggers sorting and filtering requests as query params change', async () => {
    const user = userEvent.setup();
    let lastQuery = null;

    mockFetch({
      'GET /api/v1/reviews': (call) => {
        lastQuery = call.query;
        let items = [...mockReviews];
        if (call.query.rating === '5') {
          items = items.filter((r) => r.rating === 5);
        }
        if (call.query.sort === 'best_to_worse') {
          items.sort((a, b) => b.rating - a.rating);
        } else if (call.query.sort === 'worse_to_best') {
          items.sort((a, b) => a.rating - b.rating);
        }
        return json(200, {
          items,
          aggregate: { count: items.length, averageRating: 3.0 },
        });
      },
    });

    renderRoutes(
      [
        {
          path: '/test-reviews',
          element: <ProductReviewSection productId={101} productName="Nhẫn Bạc Ý Đính Đá Moissanite" />,
        },
      ],
      { path: '/test-reviews' }
    );

    expect(await screen.findByTestId('product-reviews-section')).toBeInTheDocument();

    const sortSelect = screen.getByTestId('review-sort-select');
    await user.selectOptions(sortSelect, 'best_to_worse');
    await waitFor(() => {
      expect(lastQuery?.sort).toBe('best_to_worse');
    });

    await user.selectOptions(sortSelect, 'worse_to_best');
    await waitFor(() => {
      expect(lastQuery?.sort).toBe('worse_to_best');
    });

    const filter5Star = screen.getByTestId('filter-star-5');
    await user.click(filter5Star);
    await waitFor(() => {
      expect(lastQuery?.rating).toBe('5');
    });
  });

  it('renders friendly empty state when product has no reviews', async () => {
    mockFetch({
      'GET /api/v1/reviews': () =>
        json(200, {
          items: [],
          aggregate: { count: 0, averageRating: 0 },
        }),
    });

    renderRoutes(
      [
        {
          path: '/test-reviews',
          element: <ProductReviewSection productId={999} productName="Nhẫn Chưa Có Review" />,
        },
      ],
      { path: '/test-reviews' }
    );

    expect(await screen.findByTestId('product-reviews-empty')).toBeInTheDocument();
    expect(screen.getByText('Chưa có đánh giá nào cho sản phẩm này')).toBeInTheDocument();
  });
});
