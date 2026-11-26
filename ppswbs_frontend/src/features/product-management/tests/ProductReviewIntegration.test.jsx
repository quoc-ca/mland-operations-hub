import React from 'react';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { clearRoleCache } from '../../../shared/auth/useRole';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import ProductReviewSection, { reviewScore } from '../ui/public/ProductReviewSection';
import { publicProduct } from './fixtures';

/** FR-063 (Session 2026-11-20, T172): customer reviews on the public product page, read from 011. */
const mockReviews = [
  { id: 'rev-1', targetType: 'RETAIL_PRODUCT', targetReference: '101', rating: 5, maskedDisplayName: 'Nguyễn V***', bodyText: 'Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!', status: 'PUBLIC', createdAt: '2026-11-14T10:00:00Z' },
  { id: 'rev-2', targetType: 'RETAIL_PRODUCT', targetReference: '101', rating: 3, maskedDisplayName: 'Trần H***', bodyText: 'Nhẫn đẹp vừa tay nhưng hộp đựng hơi móp.', status: 'PUBLIC', createdAt: '2026-11-14T09:00:00Z' },
  { id: 'rev-3', targetType: 'RETAIL_PRODUCT', targetReference: '101', rating: 1, maskedDisplayName: 'Lê M***', bodyText: 'Thời gian giao hàng hơi lâu so với dự kiến.', status: 'PUBLIC', createdAt: '2026-11-14T08:00:00Z' },
];
const page = (items, count = items.length, averageRating = 3.0) => json(200, { items, aggregate: { count, averageRating } });
const show = (productId = 101, options) => renderRoutes(
  [{ path: '/test-reviews', element: <ProductReviewSection productId={productId} productName="Nhẫn Bạc Ý Đính Đá Moissanite" /> }],
  { path: '/test-reviews', ...options },
);
const scoreCard = () => screen.getByTestId('product-reviews-score');

beforeEach(() => { vi.restoreAllMocks(); clearRoleCache(); });
afterEach(() => vi.unstubAllGlobals());

describe('FR-063 score', () => {
  it('has no score without a review, and the real average otherwise', () => {
    expect(reviewScore({ count: 0, averageRating: 0 })).toEqual({ count: 0, average: null, stars: '☆☆☆☆☆' });
    expect(reviewScore(undefined)).toEqual({ count: 0, average: null, stars: '☆☆☆☆☆' });
    expect(reviewScore({ count: 3, averageRating: 3 })).toEqual({ count: 3, average: '3.0', stars: '★★★☆☆' });
    expect(reviewScore({ count: 2, averageRating: 4.5 })).toEqual({ count: 2, average: '4.5', stars: '★★★★★' });
    expect(reviewScore({ count: 1, averageRating: 1 })).toEqual({ count: 1, average: '1.0', stars: '★☆☆☆☆' });
  });
});

describe('FR-063 customer reviews on the product page', () => {
  it('shows the real average, the stars and the count of public reviews with each review', async () => {
    mockFetch({ 'GET /api/v1/reviews': () => page(mockReviews) });
    show();
    expect(await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Trải nghiệm về “Nhẫn Bạc Ý Đính Đá Moissanite”' })).toBeInTheDocument();
    expect(within(scoreCard()).getByText('3.0')).toBeInTheDocument();
    expect(within(scoreCard()).getByRole('img', { name: '3.0 trên 5 sao' })).toHaveTextContent('★★★☆☆');
    expect(within(scoreCard()).getByText('3 đánh giá công khai')).toBeInTheDocument();
    expect(screen.getByText('Nhẫn đẹp vừa tay nhưng hộp đựng hơi móp.')).toBeInTheDocument();
    expect(screen.getByText('Thời gian giao hàng hơi lâu so với dự kiến.')).toBeInTheDocument();
  });

  it('without any review says "Chưa có đánh giá" with empty stars and no 5.0', async () => {
    mockFetch({ 'GET /api/v1/reviews': () => page([], 0, 0) });
    show(999);
    expect(await screen.findByText('Chưa có đánh giá nào cho sản phẩm này')).toBeInTheDocument();
    expect(within(scoreCard()).getAllByText('Chưa có đánh giá').length).toBeGreaterThan(0);
    expect(within(scoreCard()).getByRole('img', { name: 'Chưa có đánh giá' })).toHaveTextContent('☆☆☆☆☆');
    expect(within(scoreCard()).getByText('0 đánh giá công khai')).toBeInTheDocument();
    expect(scoreCard().textContent).not.toMatch(/5\.0|★/);
    expect(screen.getByText(/Hãy là người đầu tiên/)).toBeInTheDocument();
  });

  it('sends the sort and the star filter, and a second click on the star removes the filter', async () => {
    const user = userEvent.setup();
    const queries = [];
    mockFetch({
      'GET /api/v1/reviews': call => {
        queries.push(call.query);
        return page(call.query.rating ? mockReviews.filter(review => String(review.rating) === call.query.rating) : mockReviews, 3);
      },
    });
    show();
    await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!');
    expect(queries[0]).toMatchObject({ targetType: 'RETAIL_PRODUCT', targetReference: '101', sort: 'recent', limit: '50' });

    const select = screen.getByLabelText('Sắp xếp theo:');
    expect([...select.options].map(option => option.text)).toEqual(['Mới nhất', 'Điểm cao nhất', 'Điểm thấp nhất']);
    await user.selectOptions(select, 'best_to_worse');
    await waitFor(() => expect(queries.at(-1).sort).toBe('best_to_worse'));

    await user.click(screen.getByRole('button', { name: 'Chỉ xem đánh giá 5 sao' }));
    await waitFor(() => expect(queries.at(-1).rating).toBe('5'));
    expect(screen.getByRole('button', { name: 'Chỉ xem đánh giá 5 sao' })).toHaveAttribute('aria-pressed', 'true');
    // The score is the product's, whatever the filter.
    expect(within(scoreCard()).getByText('3 đánh giá công khai')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Chỉ xem đánh giá 5 sao' }));
    await waitFor(() => expect(queries.at(-1).rating).toBeUndefined());
  });

  it('a filter without a match says so, not that the product has no review', async () => {
    const user = userEvent.setup();
    mockFetch({ 'GET /api/v1/reviews': call => page(call.query.rating === '2' ? [] : mockReviews, 3) });
    show();
    await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!');
    await user.click(screen.getByRole('button', { name: 'Chỉ xem đánh giá 2 sao' }));
    expect(await screen.findByText('Không có đánh giá 2 sao nào')).toBeInTheDocument();
    expect(screen.queryByText('Chưa có đánh giá nào cho sản phẩm này')).toBeNull();
    expect(within(scoreCard()).getByText('3.0')).toBeInTheDocument();
  });

  it('keeps only this product\'s reviews even if the source sends others', async () => {
    mockFetch({ 'GET /api/v1/reviews': () => page([...mockReviews, { ...mockReviews[0], id: 'other', targetReference: '7', bodyText: 'Đánh giá sản phẩm khác' }], 3) });
    show();
    await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!');
    expect(screen.queryByText('Đánh giá sản phẩm khác')).toBeNull();
  });

  it('says when the reviews cannot be read and reads them again on retry', async () => {
    const user = userEvent.setup();
    let up = false;
    mockFetch({
      'GET /api/v1/reviews': () => (up ? page(mockReviews) : json(503, { code: 'SERVICE_UNAVAILABLE', message: 'Down.' })),
    });
    show();
    expect(await screen.findByTestId('product-reviews-error')).toHaveTextContent('Không tải được đánh giá của sản phẩm.');
    up = true;
    await user.click(screen.getByRole('button', { name: 'Tải lại đánh giá' }));
    expect(await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!')).toBeInTheDocument();
    expect(screen.queryByTestId('product-reviews-error')).toBeNull();
  });

  it('is in English when English is chosen', async () => {
    mockFetch({ 'GET /api/v1/reviews': () => page([], 0, 0) });
    show(999, { language: 'en' });
    expect(await screen.findByText('No reviews for this product yet')).toBeInTheDocument();
    expect(within(scoreCard()).getByRole('img', { name: 'No reviews yet' })).toBeInTheDocument();
    expect([...screen.getByLabelText('Sort by:').options].map(option => option.text)).toEqual(['Newest', 'Highest rated', 'Lowest rated']);
    expect(screen.queryByText(/Chưa có|Sắp xếp|Recent reviews/)).toBeNull();
  });

  it('asks 011 for review sources only for a Member', async () => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER' }),
      'GET /api/v1/reviews': () => page(mockReviews),
      'GET /api/v1/reviews/eligible-sources': json(200, { sources: [] }),
    });
    show();
    await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!');
    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/reviews/eligible-sources')).toBe(true));
  });

  it.each(['STAFF', 'MANAGER', 'ADMIN', 'CARRIER'])('%s sees the reviews without asking for Member review sources', async role => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role }),
      'GET /api/v1/reviews': () => page(mockReviews),
    });
    show();
    await screen.findByText('Sản phẩm tuyệt vời, nhẫn sáng bóng tinh xảo!');
    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/accounts/me/profile')).toBe(true));
    expect(calls.some(call => call.path === '/api/v1/reviews/eligible-sources')).toBe(false);
  });

  it('is shown on the public product page for that product', async () => {
    vi.stubGlobal('scrollTo', vi.fn());
    const queries = [];
    mockFetch({
      'GET /api/v1/products/42': json(200, publicProduct()),
      'GET /api/v1/reviews': call => { queries.push(call.query); return page([], 0, 0); },
    });
    renderRoutes(productManagementRoutes, { path: '/products/42' });
    expect(await screen.findByTestId('product-reviews-section')).toBeInTheDocument();
    await waitFor(() => expect(queries[0]).toMatchObject({ targetType: 'RETAIL_PRODUCT', targetReference: '42' }));
  });
});
