import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { productManagementRoutes } from '../routes/productManagementRoutes';
import { publicProduct } from './fixtures';

// FR-068 (Session 2026-11-22): previous/next buttons on the big image of the public detail page.
const open = path => renderRoutes(productManagementRoutes, { path });
const threeImages = publicProduct({ images: [
  { id: '9', url: '/api/v1/product-media/9', altText: 'Mặt trước', position: 0 },
  { id: '10', url: '/api/v1/product-media/10', altText: 'Mặt bên', position: 1 },
  { id: '11', url: '/api/v1/product-media/11', altText: 'Trong hộp', position: 2 },
] });
const bigImage = () => document.querySelector('.detail-image');
const activeThumb = () => document.querySelector('.thumb.active img');

beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()));
afterEach(() => vi.unstubAllGlobals());

describe('FR-068 public detail image buttons', () => {
  it('moves to the next and previous image, wraps at both ends and keeps the thumbnail in step', async () => {
    const user = userEvent.setup();
    mockFetch({ 'GET /api/v1/products/42': json(200, threeImages) });
    open('/products/42');
    const next = await screen.findByRole('button', { name: 'Ảnh sau' });
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
    expect(bigImage()).toHaveAttribute('alt', 'Mặt trước');

    await user.click(next);
    expect(bigImage()).toHaveAttribute('alt', 'Mặt bên');
    expect(activeThumb()).toHaveAttribute('alt', 'Mặt bên');
    expect(screen.getByText('2 / 3')).toBeInTheDocument();

    await user.click(next);
    await user.click(next); // past the last image: back to the first
    expect(bigImage()).toHaveAttribute('alt', 'Mặt trước');

    await user.click(screen.getByRole('button', { name: 'Ảnh trước' })); // before the first: the last
    expect(bigImage()).toHaveAttribute('alt', 'Trong hộp');
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
  });

  it('follows the arrow keys while a button has focus, and a horizontal swipe', async () => {
    mockFetch({ 'GET /api/v1/products/42': json(200, threeImages) });
    open('/products/42');
    const next = await screen.findByRole('button', { name: 'Ảnh sau' });
    fireEvent.keyDown(next, { key: 'ArrowRight' });
    expect(bigImage()).toHaveAttribute('alt', 'Mặt bên');
    fireEvent.keyDown(next, { key: 'ArrowLeft' });
    expect(bigImage()).toHaveAttribute('alt', 'Mặt trước');

    const stage = document.querySelector('.detail-stage');
    fireEvent.touchStart(stage, { touches: [{ clientX: 300 }] });
    fireEvent.touchEnd(stage, { changedTouches: [{ clientX: 200 }] }); // swipe left: next image
    expect(bigImage()).toHaveAttribute('alt', 'Mặt bên');
  });

  it('shows no buttons for a product with one image', async () => {
    mockFetch({ 'GET /api/v1/products/42': json(200, publicProduct()) });
    open('/products/42');
    await screen.findByRole('heading', { name: 'Nhẫn bạc' });
    expect(screen.queryByRole('button', { name: 'Ảnh sau' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ảnh trước' })).not.toBeInTheDocument();
  });
});
